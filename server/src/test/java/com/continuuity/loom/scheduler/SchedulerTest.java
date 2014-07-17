/*
 * Copyright 2012-2014, Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.continuuity.loom.scheduler;

import com.continuuity.loom.BaseTest;
import com.continuuity.loom.Entities;
import com.continuuity.loom.TestHelper;
import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.common.conf.Constants;
import com.continuuity.loom.common.queue.Element;
import com.continuuity.loom.common.queue.QueueGroup;
import com.continuuity.loom.common.queue.TrackingQueue;
import com.continuuity.loom.common.zookeeper.IdService;
import com.continuuity.loom.http.LoomService;
import com.continuuity.loom.scheduler.callback.CallbackData;
import com.continuuity.loom.scheduler.task.ClusterJob;
import com.continuuity.loom.scheduler.task.ClusterTask;
import com.continuuity.loom.scheduler.task.JobId;
import com.continuuity.loom.scheduler.task.TaskId;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.gson.JsonObject;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test ClusterScheduler
 */
public class SchedulerTest extends BaseTest {
  private static QueueGroup clusterQueues;
  private static QueueGroup provisionerQueues;
  private static QueueGroup callbackQueues;
  private static QueueGroup solverQueues;
  private static QueueGroup jobQueues;
  private static LoomService loomService;
  private static Cluster cluster;
  private static ClusterJob job;

  @BeforeClass
  public static void start() throws Exception {
    injector.getInstance(IdService.class).startAndWait();

    clusterQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.CLUSTER)));
    provisionerQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.PROVISIONER)));
    solverQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.SOLVER)));
    jobQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.JOB)));
    callbackQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.CALLBACK)));

    loomService = injector.getInstance(LoomService.class);
    loomService.startAndWait();
  }

  @AfterClass
  public static void stop() throws Exception {
    loomService.stopAndWait();
  }

  @Before
  public void beforeTest() throws Exception {
    jobQueues.removeAll();
    clusterQueues.removeAll();
    solverQueues.removeAll();
    provisionerQueues.removeAll();
    callbackQueues.removeAll();
    mockClusterCallback.clear();

    cluster = Entities.ClusterExample.createCluster();
    job = new ClusterJob(new JobId(cluster.getId(), 0), ClusterAction.CLUSTER_CREATE);
    cluster.setLatestJobId(job.getJobId());
    clusterStoreService.getView(cluster.getAccount()).writeCluster(cluster);
    clusterStore.writeClusterJob(job);

    clusterStore.writeNode(Entities.ClusterExample.NODE1);
    clusterStore.writeNode(Entities.ClusterExample.NODE2);
  }

  @Test(timeout = 20000)
  public void testScheduler() throws Exception {
    String tenantId = cluster.getAccount().getTenantId();
    ClusterScheduler clusterScheduler = injector.getInstance(ClusterScheduler.class);
    CallbackScheduler callbackScheduler = injector.getInstance(CallbackScheduler.class);

    clusterQueues.add(tenantId, new Element(cluster.getId(), ClusterAction.CLUSTER_CREATE.name()));
    clusterScheduler.run();

    String hosts = Entities.ServiceExample.HOSTS.getName();
    String namenode = Entities.ServiceExample.NAMENODE.getName();
    String datanode = Entities.ServiceExample.DATANODE.getName();
    // Verify stages and actions
    List<Multiset<ActionService>> expectedStages =
      ImmutableList.<Multiset<ActionService>>of(
        ImmutableMultiset.of(new ActionService("CREATE", ""), new ActionService("CREATE", "")),

        ImmutableMultiset.of(new ActionService("CONFIRM", ""), new ActionService("CONFIRM", "")),

        ImmutableMultiset.of(new ActionService("BOOTSTRAP", ""), new ActionService("BOOTSTRAP", "")),

        ImmutableMultiset.of(new ActionService("CONFIGURE", hosts), new ActionService("CONFIGURE", hosts),
                             new ActionService("INSTALL", datanode),
                             new ActionService("INSTALL", namenode)),

        ImmutableMultiset.of(new ActionService("CONFIGURE", namenode),
                             new ActionService("CONFIGURE", datanode)),

        ImmutableMultiset.of(new ActionService("INITIALIZE", namenode)),

        ImmutableMultiset.of(new ActionService("START", namenode)),

        ImmutableMultiset.of(new ActionService("INITIALIZE", datanode)),

        ImmutableMultiset.of(new ActionService("START", datanode))
      );

    List<Multiset<ActionService>> actualStages = Lists.newArrayList();
    waitForCallback(callbackScheduler);

    Assert.assertEquals(1, jobQueues.size(tenantId));
    String consumerId = "testJobScheduler";
    Element jobQueueElement = jobQueues.take(tenantId, consumerId);
    String jobId = jobQueueElement.getValue();
    job = clusterStore.getClusterJob(JobId.fromString(jobId));
    while (true) {
      Multiset<ActionService> actionServices = HashMultiset.create();
      for (String taskId : job.getCurrentStage()) {
        ClusterTask task = clusterStore.getClusterTask(TaskId.fromString(taskId));
        actionServices.add(new ActionService(task.getTaskName().name(), task.getService()));
      }

      actualStages.add(actionServices);

      if (!job.hasNextStage()) {
        break;
      }
      job.advanceStage();
    }

    // 4th and 5th stage get deduped, hence merging them back for comparison
    Multiset<ActionService> actionServices = actualStages.remove(3);
    actualStages.get(3).addAll(actionServices);

    Assert.assertEquals(expectedStages, actualStages);
    jobQueues.recordProgress(consumerId, tenantId, jobQueueElement.getId(),
                             TrackingQueue.ConsumingStatus.FINISHED_SUCCESSFULLY, "");


    // Add the job back into the jobQueues, and run job scheduler
    jobQueues.add(tenantId, new Element(jobId));
    JobScheduler jobScheduler = injector.getInstance(JobScheduler.class);
    jobScheduler.run();
    Assert.assertEquals(0, jobQueues.size(tenantId));

    // Two tasks should have been submitted for provisioning.
    JsonObject taskJson = TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);

    JsonObject returnJson = new JsonObject();
    returnJson.addProperty("status", 0);
    returnJson.addProperty("workerId", "consumer1");
    returnJson.addProperty("tenantId", tenantId);
    returnJson.addProperty("taskId", taskJson.get("taskId").getAsString());
    returnJson.add("result", gson.toJsonTree(ImmutableMap.of("ipaddress", "123.456.789.123")));
    TestHelper.finishTask(getLoomUrl(), returnJson);

    taskJson = TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);
    returnJson = new JsonObject();
    returnJson.addProperty("status", 0);
    returnJson.addProperty("workerId", "consumer1");
    returnJson.addProperty("tenantId", tenantId);
    returnJson.addProperty("taskId", taskJson.get("taskId").getAsString());
    returnJson.add("result", gson.toJsonTree(ImmutableMap.of("ipaddress", "456.789.123.123")));
    TestHelper.finishTask(getLoomUrl(), returnJson);

    TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);

    Assert.assertEquals(2, jobQueues.size(tenantId));

    jobScheduler.run();
    jobScheduler.run();
    jobScheduler.run();
    jobScheduler.run();

    for (int i = 0; i < 5; i++) {
      taskJson = TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);
      returnJson = new JsonObject();
      returnJson.addProperty("status", 0);
      returnJson.addProperty("workerId", "consumer1");
      returnJson.addProperty("tenantId", tenantId);
      returnJson.addProperty("taskId", taskJson.get("taskId").getAsString());
      TestHelper.finishTask(getLoomUrl(), returnJson);
      jobScheduler.run();
      jobScheduler.run();
    }
  }

  @Test(timeout = 20000)
  public void testSuccessCallbacks() throws Exception {
    testCallbacks(false);
  }

  @Test(timeout = 20000)
  public void testFailureCallbacks() throws Exception {
    testCallbacks(true);
  }

  @Test(timeout = 20000)
  public void testFalseOnStartStopsJob() throws Exception {
    String tenantId = "q";
    ClusterScheduler clusterScheduler = injector.getInstance(ClusterScheduler.class);

    clusterQueues.add(tenantId, new Element(cluster.getId(), ClusterAction.CLUSTER_CREATE.name()));
    clusterScheduler.run();

    CallbackScheduler callbackScheduler = injector.getInstance(CallbackScheduler.class);
    // should be no job in the queue until the start callback runs
    Assert.assertEquals(0, jobQueues.size(tenantId));

    // tell mock callback to return false for onStart callback
    mockClusterCallback.setReturnOnStart(false);
    // wait for start callback to finish
    waitForCallback(callbackScheduler);
    Assert.assertEquals(CallbackData.Type.START, mockClusterCallback.getReceivedCallbacks().get(0).getType());

    // wait for fail callback to finish
    if (mockClusterCallback.getReceivedCallbacks().size() < 2) {
      waitForCallback(callbackScheduler);
    }
    Assert.assertEquals(CallbackData.Type.FAILURE, mockClusterCallback.getReceivedCallbacks().get(1).getType());

    // there also should not be any jobs in the queue
    Assert.assertEquals(0, jobQueues.size(tenantId));
  }

  private void waitForCallback(CallbackScheduler callbackScheduler) throws InterruptedException {
    int initialSize = mockClusterCallback.getReceivedCallbacks().size();
    int size = initialSize;
    callbackScheduler.run();
    while (size == initialSize) {
      size = mockClusterCallback.getReceivedCallbacks().size();
      TimeUnit.MILLISECONDS.sleep(20);
    }
  }

  private void testCallbacks(boolean failJob) throws Exception {
    ClusterScheduler clusterScheduler = injector.getInstance(ClusterScheduler.class);
    String tenantId = cluster.getAccount().getTenantId();

    clusterQueues.add(tenantId, new Element(cluster.getId(), ClusterAction.CLUSTER_CREATE.name()));
    clusterScheduler.run();

    CallbackScheduler callbackScheduler = injector.getInstance(CallbackScheduler.class);
    // should be no job in the queue until the start callback runs
    Assert.assertEquals(0, jobQueues.size(tenantId));
    waitForCallback(callbackScheduler);

    Assert.assertEquals(CallbackData.Type.START, mockClusterCallback.getReceivedCallbacks().get(0).getType());

    JobScheduler jobScheduler = injector.getInstance(JobScheduler.class);
    jobScheduler.run();

    // take tasks until there are no more
    JsonObject taskJson = TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);
    while (taskJson.entrySet().size() > 0) {
      JsonObject returnJson = new JsonObject();
      returnJson.addProperty("status", failJob ? 1 : 0);
      returnJson.addProperty("workerId", "consumer1");
      returnJson.addProperty("tenantId", tenantId);
      returnJson.addProperty("taskId", taskJson.get("taskId").getAsString());
      TestHelper.finishTask(getLoomUrl(), returnJson);
      jobScheduler.run();
      jobScheduler.run();
      taskJson = TestHelper.takeTask(getLoomUrl(), "consumer1", tenantId);
    }
    jobScheduler.run();
    waitForCallback(callbackScheduler);

    // at this point, the failure callback should have run
    Assert.assertEquals(failJob ? CallbackData.Type.FAILURE : CallbackData.Type.SUCCESS,
                        mockClusterCallback.getReceivedCallbacks().get(1).getType());
  }


  private String getLoomUrl() {
    InetSocketAddress address = loomService.getBindAddress();
    return String.format("http://%s:%s", address.getHostName(), address.getPort());
  }

  private static class ActionService {
    private final String action;
    private final String service;

    private ActionService(String action, String service) {
      this.action = action;
      this.service = service;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ActionService that = (ActionService) o;

      return !(action != null ? !action.equals(that.action) : that.action != null) &&
        !(service != null ? !service.equals(that.service) : that.service != null);

    }

    @Override
    public int hashCode() {
      int result = action != null ? action.hashCode() : 0;
      result = 31 * result + (service != null ? service.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("action", action)
        .add("service", service)
        .toString();
    }
  }
}
