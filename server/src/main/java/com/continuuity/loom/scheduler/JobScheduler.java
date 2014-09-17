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

import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.cluster.Node;
import com.continuuity.loom.common.conf.Configuration;
import com.continuuity.loom.common.conf.Constants;
import com.continuuity.loom.common.queue.Element;
import com.continuuity.loom.common.queue.GroupElement;
import com.continuuity.loom.common.queue.QueueGroup;
import com.continuuity.loom.common.queue.TrackingQueue;
import com.continuuity.loom.common.zookeeper.LockService;
import com.continuuity.loom.common.zookeeper.lib.ZKInterProcessReentrantLock;
import com.continuuity.loom.macro.Expander;
import com.continuuity.loom.scheduler.task.ClusterJob;
import com.continuuity.loom.scheduler.task.ClusterTask;
import com.continuuity.loom.scheduler.task.JobId;
import com.continuuity.loom.scheduler.task.SchedulableTask;
import com.continuuity.loom.scheduler.task.TaskConfig;
import com.continuuity.loom.scheduler.task.TaskId;
import com.continuuity.loom.scheduler.task.TaskService;
import com.continuuity.loom.spec.service.Service;
import com.continuuity.loom.store.cluster.ClusterStore;
import com.continuuity.loom.store.cluster.ClusterStoreService;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schedules a cluster job. Polls a queue containing job ids to coordinate. Each time it gets a job id from the queue,
 * it will examine the status of tasks for the job's current stage and take the appropriate action. If all tasks in the
 * stage successfully completed, the job will be moved to the next stage and all tasks in the stage will be scheduled.
 * If some task was failed, the appropriate retry and rollback actions are taken for the task. If the job itself fails,
 * unneeded tasks are dropped and cluster and job state is managed. If all tasks for the job have completed, status
 * is updated across the job and cluster.
 */
public class JobScheduler implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(JobScheduler.class);
  private static final String consumerId = "jobscheduler";

  private final ClusterStore clusterStore;
  private final LockService lockService;
  private final TaskService taskService;
  private final int maxTaskRetries;
  private final Gson gson;
  private final QueueGroup jobQueues;
  private final QueueGroup provisionerQueues;

  @Inject
  private JobScheduler(ClusterStoreService clusterStoreService,
                       @Named(Constants.Queue.JOB) QueueGroup jobQueues,
                       @Named(Constants.Queue.PROVISIONER) QueueGroup provisionerQueues,
                       LockService lockService,
                       TaskService taskService,
                       Configuration conf,
                       Gson gson) {
    this.clusterStore = clusterStoreService.getSystemView();
    this.lockService = lockService;
    this.taskService = taskService;
    this.maxTaskRetries = conf.getInt(Constants.MAX_ACTION_RETRIES);
    this.gson = gson;
    this.jobQueues = jobQueues;
    this.provisionerQueues = provisionerQueues;
  }

  @Override
  public void run() {
    try {
      while (true) {
        GroupElement gElement = jobQueues.take(consumerId);
        if (gElement == null) {
          return;
        }
        String queueName = gElement.getQueueName();
        Element element = gElement.getElement();
        String jobIdStr = element.getValue();

        LOG.debug("Got job {} to schedule", jobIdStr);
        JobId jobId = JobId.fromString(jobIdStr);
        ZKInterProcessReentrantLock lock = lockService.getJobLock(queueName, jobId.getClusterId());
        try {
          lock.acquire();
          ClusterJob job = clusterStore.getClusterJob(jobId);
          Cluster cluster = clusterStore.getCluster(job.getClusterId());
          // this can happen if 2 tasks complete around the same time and the first one places the job in the queue,
          // sees 0 in progress tasks, and sets the cluster status. The job is still in the queue as another element
          // from the 2nd task and gets here.  In that case, no need to go further.
          if (cluster.getStatus() != Cluster.Status.PENDING) {
            continue;
          }
          LOG.trace("Scheduling job {}", job);
          Set<String> currentStage = job.getCurrentStage();

          // Check how many tasks are completed/not-submitted
          boolean jobFailed = job.getJobStatus() == ClusterJob.Status.FAILED;
          int completedTasks = 0;
          int inProgressTasks = 0;
          Set<ClusterTask> notSubmittedTasks = Sets.newHashSet();
          Set<ClusterTask> retryTasks = Sets.newHashSet();
          // TODO: avoid looking up every single task every time
          LOG.debug("Verifying task statuses for stage {} for job {}", job.getCurrentStageNumber(), jobIdStr);
          for (String taskId : currentStage) {
            ClusterTask task = clusterStore.getClusterTask(TaskId.fromString(taskId));
            job.setTaskStatus(task.getTaskId(), task.getStatus());
            LOG.debug("Status of task {} is {}", taskId, task.getStatus());
            if (task.getStatus() == ClusterTask.Status.COMPLETE) {
              ++completedTasks;
            } else if (task.getStatus() == ClusterTask.Status.NOT_SUBMITTED) {
              notSubmittedTasks.add(task);
            } else if (task.getStatus() == ClusterTask.Status.FAILED) {
              // If max retries has not reached, retry task. Else, fail job.
              if (task.getNumAttempts() < maxTaskRetries) {
                retryTasks.add(task);
              } else {
                jobFailed = true;
              }
            } else if (task.getStatus() == ClusterTask.Status.IN_PROGRESS) {
              ++inProgressTasks;
            }
          }

          // If the job has not failed continue with scheduling other tasks.
          if (!jobFailed) {
            Set<Node> clusterNodes = clusterStore.getClusterNodes(job.getClusterId());
            Map<String, Node> nodeMap = Maps.newHashMap();
            for (Node node : clusterNodes) {
              nodeMap.put(node.getId(), node);
            }

            // Handle retry tasks if any
            if (!retryTasks.isEmpty()) {
              for (ClusterTask task : retryTasks) {
                notSubmittedTasks.add(scheduleRetry(job, task));
              }
            }

            // Submit any tasks not yet submitted
            if (!notSubmittedTasks.isEmpty()) {
              submitTasks(notSubmittedTasks, cluster, nodeMap, clusterNodes, job, queueName);
            }

            // Note: before moving cluster out of pending state, make sure that all in progress tasks are done.
            // If all tasks are completed then move to next stage
            if (completedTasks == currentStage.size()) {
              if (job.hasNextStage()) {
                LOG.debug("Advancing to next stage {} for job {}", job.getCurrentStageNumber(), job.getJobId());
                job.advanceStage();
                jobQueues.add(queueName, new Element(jobIdStr));
              } else {
                taskService.completeJob(job, cluster);
              }
            }
            clusterStore.writeClusterJob(job);
          } else if (inProgressTasks == 0) {
            // Job failed and no in progress tasks remaining, update cluster status
            taskService.failJobAndSetClusterStatus(job, cluster);
          } else {
            // Job failed but tasks are still in progress, wait for them to finish before setting cluster status
            taskService.failJob(job);
          }
        } finally {
          lock.release();
          jobQueues.recordProgress(consumerId, queueName, element.getId(),
                                  TrackingQueue.ConsumingStatus.FINISHED_SUCCESSFULLY, "");
        }
      }
    } catch (Throwable e) {
      LOG.error("Got exception: ", e);
    }
  }

  private void submitTasks(Set<ClusterTask> notSubmittedTasks, Cluster cluster, Map<String, Node> nodeMap,
                           Set<Node> clusterNodes, ClusterJob job, String queueName) throws Exception {
    JsonObject unexpandedClusterConfig = cluster.getConfig();

    for (final ClusterTask task : notSubmittedTasks) {
      Node taskNode = nodeMap.get(task.getNodeId());
      JsonObject clusterConfig = unexpandedClusterConfig;

      // TODO: do this only once and save it
      if (!task.getTaskName().isHardwareAction()) {
        try {
          // expansion does not modify the original input, but creates a new object
          clusterConfig = Expander.expand(clusterConfig, null, cluster, clusterNodes, taskNode).getAsJsonObject();
        } catch (Throwable e) {
          LOG.error("Exception while expanding macros for task {}", task.getTaskId(), e);
          taskService.failTask(task, -1);
          job.setStatusMessage("Exception while expanding macros: " + e.getMessage());
          // no need to schedule more tasks since the job is considered failed even if one task fails.
          jobQueues.add(queueName, new Element(job.getJobId()));
          break;
        }
      }

      Service tService = null;
      for (Service service : taskNode.getServices()) {
        if (service.getName().equals(task.getService())) {
          tService = service;
          break;
        }
      }
      TaskConfig taskConfig = TaskConfig.from(cluster, taskNode, tService, clusterConfig,
                                              task.getTaskName(), clusterNodes);
      LOG.debug("Submitting task {}", task.getTaskId());
      LOG.trace("Task {}", task);
      SchedulableTask schedulableTask = new SchedulableTask(task, taskConfig);
      LOG.trace("Schedulable task {}", schedulableTask);

      // Submit task
      // Note: the job has to be scheduled for processing when the task is complete.
      provisionerQueues.add(
        queueName, new Element(task.getTaskId(), gson.toJson(schedulableTask)));

      job.setTaskStatus(task.getTaskId(), ClusterTask.Status.IN_PROGRESS);
      taskService.startTask(task);
    }
  }

  ClusterTask scheduleRetry(ClusterJob job, ClusterTask task) throws Exception {
    task.addAttempt();
    List<ClusterTask> retryTasks = taskService.getRetryTask(task);

    if (retryTasks.size() == 1) {
      LOG.trace("Only one retry task for job {} for task {}", job, task);
      return retryTasks.get(0);
    }

    // store all retry tasks
    for (ClusterTask t : retryTasks) {
      clusterStore.writeClusterTask(t);
    }

    // Remove self from current stage
    job.getCurrentStage().remove(task.getTaskId());
    // Add first retry task to current stage
    job.getCurrentStage().add(retryTasks.get(0).getTaskId());
    // Add the rest of retry tasks after current stage. TODO: this needs to be revisited.
    job.insertTasksAfterCurrentStage(ImmutableList.copyOf(Iterables.transform(Iterables.skip(retryTasks, 1),
                                                                              CLUSTER_TASK_STRING_FUNCTION)));
    LOG.trace("Retry job {} for task {}", job, task);

    return retryTasks.get(0);
  }

  private static final Function<ClusterTask, String> CLUSTER_TASK_STRING_FUNCTION =
    new Function<ClusterTask, String>() {
      @Override
      public String apply(ClusterTask clusterTask) {
        return clusterTask.getTaskId();
      }
    };
}