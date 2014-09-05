/*
 * Copyright © 2012-2014 Cask Data, Inc.
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
package co.cask.coopr.scheduler;

import co.cask.coopr.BaseTest;
import co.cask.coopr.Entities;
import co.cask.coopr.account.Account;
import co.cask.coopr.spec.template.ClusterDefaults;
import co.cask.coopr.spec.template.ClusterTemplate;
import co.cask.coopr.spec.template.Compatibilities;
import co.cask.coopr.spec.template.Constraints;
import co.cask.coopr.spec.HardwareType;
import co.cask.coopr.spec.ImageType;
import co.cask.coopr.spec.template.LayoutConstraint;
import co.cask.coopr.spec.Provider;
import co.cask.coopr.spec.ProvisionerAction;
import co.cask.coopr.spec.service.Service;
import co.cask.coopr.spec.service.ServiceAction;
import co.cask.coopr.spec.template.ServiceConstraint;
import co.cask.coopr.cluster.Cluster;
import co.cask.coopr.cluster.Node;
import co.cask.coopr.common.conf.Constants;
import co.cask.coopr.common.queue.Element;
import co.cask.coopr.common.queue.QueueGroup;
import co.cask.coopr.http.request.ClusterCreateRequest;
import co.cask.coopr.scheduler.task.ClusterJob;
import co.cask.coopr.scheduler.task.JobId;
import co.cask.coopr.spec.template.SizeConstraint;
import co.cask.coopr.store.entity.EntityStoreView;
import com.google.common.base.Function;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class SolverSchedulerTest extends BaseTest {
  private static QueueGroup solverQueues;
  private static QueueGroup clusterQueues;
  private static SolverScheduler solverScheduler;
  private static ClusterTemplate reactorTemplate;
  private static Provider provider;
  private static Account account = new Account(Constants.ADMIN_USER, "tenant1");

  @Test
  public void testAddCluster() throws Exception {
    String clusterName = "my-cluster";
    Cluster cluster = Cluster.builder()
      .setID("1")
      .setAccount(account)
      .setName(clusterName)
      .setDescription("my cluster")
      .setClusterTemplate(reactorTemplate)
      .setProvider(provider)
      .build();
    ClusterJob job = new ClusterJob(new JobId(cluster.getId(), 1), ClusterAction.CLUSTER_CREATE);
    cluster.setLatestJobId(job.getJobId());
    clusterStoreService.getView(cluster.getAccount()).writeCluster(cluster);
    clusterStore.writeClusterJob(job);
    ClusterCreateRequest createRequest = ClusterCreateRequest.builder()
      .setName(cluster.getName())
      .setDescription(cluster.getDescription())
      .setClusterTemplateName(reactorTemplate.getName())
      .setNumMachines(5)
      .setInitialLeaseDuration(0L)
      .build();
    SolverRequest solverRequest = new SolverRequest(SolverRequest.Type.CREATE_CLUSTER, gson.toJson(createRequest));
    String queueName = "tenant123";
    solverQueues.add(queueName, new Element(cluster.getId(), gson.toJson(solverRequest)));

    solverScheduler.run();

    Cluster solvedCluster = clusterStoreService.getView(cluster.getAccount()).getCluster(cluster.getId());
    // check the cluster is as expected
    Assert.assertEquals(clusterName, solvedCluster.getName());
    Assert.assertEquals("my cluster", solvedCluster.getDescription());
    // check the node counts are as expected
    Multiset<Set<String>> serviceSetCounts = HashMultiset.create();
    for (String nodeId : solvedCluster.getNodes()) {
      Node node = clusterStore.getNode(nodeId);
      Set<String> serviceNames = Sets.newHashSet(Iterables.transform(node.getServices(),
                                                                     new Function<Service, String>() {
        @Nullable
        @Override
        public String apply(Service input) {
          return input.getName();
        }
      }));
      serviceSetCounts.add(serviceNames);
    }
    Assert.assertEquals(1, serviceSetCounts.count(ImmutableSet.of("namenode", "resourcemanager", "hbasemaster")));
    Assert.assertEquals(3, serviceSetCounts.count(ImmutableSet.of("datanode", "regionserver", "nodemanager")));
    Assert.assertEquals(1, serviceSetCounts.count(ImmutableSet.of("reactor", "zookeeper")));

    Element element = clusterQueues.take(queueName, "0");
    Assert.assertEquals(cluster.getId(), element.getId());

    solverQueues.removeAll(queueName);
    clusterQueues.removeAll(queueName);
  }

  @BeforeClass
  public static void setupSchedulerTest() throws Exception {
    solverQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.SOLVER)));
    clusterQueues = injector.getInstance(Key.get(QueueGroup.class, Names.named(Constants.Queue.CLUSTER)));
    solverScheduler = injector.getInstance(SolverScheduler.class);

    Set<String> services = ImmutableSet.of("namenode", "datanode", "resourcemanager", "nodemanager",
                                           "hbasemaster", "regionserver", "zookeeper", "reactor");
    reactorTemplate = new ClusterTemplate(
      "reactor-medium",
      "medium reactor cluster template",
      new ClusterDefaults(services, "joyent", null, null, null, new JsonObject()),
      new Compatibilities(
        ImmutableSet.<String>of("large-mem", "large-cpu", "large", "medium", "small"),
        null,
        null
      ),
      new Constraints(
        ImmutableMap.<String, ServiceConstraint>of(
          "namenode",
          new ServiceConstraint(
            ImmutableSet.of("large-mem"),
            ImmutableSet.of("centos6", "ubuntu12"), 1, 1),
          "datanode",
          new ServiceConstraint(
            ImmutableSet.of("medium", "large-cpu"),
            ImmutableSet.of("centos6", "ubuntu12"), 1, 50),
          "zookeeper",
          new ServiceConstraint(
            ImmutableSet.of("small", "medium"),
            ImmutableSet.of("centos6"), 1, 5),
          "reactor",
          new ServiceConstraint(
            ImmutableSet.of("medium", "large"),
            null, 1, 5)
        ),
        new LayoutConstraint(
          ImmutableSet.<Set<String>>of(
            ImmutableSet.of("datanode", "nodemanager", "regionserver"),
            ImmutableSet.of("namenode", "resourcemanager", "hbasemaster")
          ),
          ImmutableSet.<Set<String>>of(
            ImmutableSet.of("datanode", "namenode"),
            ImmutableSet.of("datanode", "zookeeper"),
            ImmutableSet.of("namenode", "zookeeper"),
            ImmutableSet.of("datanode", "reactor"),
            ImmutableSet.of("namenode", "reactor")
          )
        ),
        SizeConstraint.EMPTY
      ),
      null
    );

    EntityStoreView superadminView =
      entityStoreService.getView(new Account(Constants.ADMIN_USER, Constants.SUPERADMIN_TENANT));
    superadminView.writeProviderType(Entities.ProviderTypeExample.JOYENT);

    EntityStoreView adminView = entityStoreService.getView(account);
    // create providers
    provider = new Provider("joyent", "joyent provider", Entities.JOYENT, ImmutableMap.<String, String>of());
    adminView.writeProvider(provider);
    // create hardware types
    adminView.writeHardwareType(
      new HardwareType(
        "medium",
        "medium hardware",
        ImmutableMap.<String, Map<String, String>>of("joyent", ImmutableMap.<String, String>of("flavor", "Medium 4GB"))
      )
    );
    adminView.writeHardwareType(
      new HardwareType(
        "large-mem",
        "hardware with a lot of memory",
        ImmutableMap.<String, Map<String, String>>of("joyent", ImmutableMap.<String, String>of("flavor", "Large 32GB"))
      )
    );
    adminView.writeHardwareType(
      new HardwareType(
        "large-cpu",
        "hardware with a lot of cpu",
        ImmutableMap.<String, Map<String, String>>of("joyent", ImmutableMap.<String, String>of("flavor", "Large 16GB"))
      )
    );
    // create image types
    adminView.writeImageType(
      new ImageType(
        "centos6",
        "CentOs 6.4 image",
        ImmutableMap.<String, Map<String, String>>of("joyent", ImmutableMap.<String, String>of("image",
                                                                                               "joyent-hash-of-centos6.4"))
      )
    );
    // create services
    for (String serviceName : services) {
      adminView.writeService(new Service(serviceName, serviceName + " description", ImmutableSet.<String>of(),
                                           ImmutableMap.<ProvisionerAction, ServiceAction>of()));
    }
    adminView.writeClusterTemplate(reactorTemplate);
  }
}
