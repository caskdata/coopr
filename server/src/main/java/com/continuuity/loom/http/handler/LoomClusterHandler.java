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
package com.continuuity.loom.http.handler;

import com.continuuity.http.HttpResponder;
import com.continuuity.loom.account.Account;
import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.cluster.Node;
import com.continuuity.loom.common.conf.Configuration;
import com.continuuity.loom.common.conf.Constants;
import com.continuuity.loom.http.request.AddServicesRequest;
import com.continuuity.loom.http.request.ClusterConfigureRequest;
import com.continuuity.loom.layout.ClusterCreateRequest;
import com.continuuity.loom.layout.InvalidClusterException;
import com.continuuity.loom.provisioner.CapacityException;
import com.continuuity.loom.provisioner.QuotaException;
import com.continuuity.loom.scheduler.ClusterAction;
import com.continuuity.loom.scheduler.task.ClusterJob;
import com.continuuity.loom.scheduler.task.ClusterService;
import com.continuuity.loom.scheduler.task.ClusterTask;
import com.continuuity.loom.scheduler.task.JobId;
import com.continuuity.loom.scheduler.task.MissingClusterException;
import com.continuuity.loom.scheduler.task.MissingEntityException;
import com.continuuity.loom.scheduler.task.TaskId;
import com.continuuity.loom.store.cluster.ClusterStore;
import com.continuuity.loom.store.cluster.ClusterStoreService;
import com.continuuity.loom.store.cluster.ClusterStoreView;
import com.continuuity.loom.store.tenant.TenantStore;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for performing cluster operations.
 */
@Path("/v1/loom/clusters")
public class LoomClusterHandler extends LoomAuthHandler {
  private static final Logger LOG = LoggerFactory.getLogger(LoomClusterHandler.class);

  private final ClusterService clusterService;
  private final ClusterStoreService clusterStoreService;
  private final ClusterStore clusterStore;
  private final int maxClusterSize;
  private final Gson gson;

  @Inject
  private LoomClusterHandler(TenantStore tenantStore,
                             ClusterService clusterService,
                             ClusterStoreService clusterStoreService,
                             Configuration conf,
                             Gson gson) {
    super(tenantStore, conf);
    this.clusterService = clusterService;
    this.clusterStoreService = clusterStoreService;
    this.clusterStore = clusterStoreService.getSystemView();
    this.maxClusterSize = conf.getInt(Constants.MAX_CLUSTER_SIZE);
    this.gson = gson;
  }

  /**
   * Get all clusters visible to the user.
   *
   * @param request Request for clusters.
   * @param responder Responder for sending the response.
   */
  @GET
  public void getClusters(HttpRequest request, HttpResponder responder) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    List<Cluster> clusters = null;
    try {
      clusters = clusterStoreService.getView(account).getAllClusters();
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception getting clusters.");
    }

    JsonArray jsonArray = new JsonArray();
    for (Cluster cluster : clusters) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", cluster.getId());
      obj.addProperty("name", cluster.getName());
      obj.addProperty("createTime", cluster.getCreateTime());
      obj.addProperty("expireTime", cluster.getExpireTime());
      obj.addProperty("clusterTemplate",
                      cluster.getClusterTemplate() == null ? "..." : cluster.getClusterTemplate().getName());
      obj.addProperty("numNodes", cluster.getNodes().size());
      obj.addProperty("status", cluster.getStatus().name());
      obj.addProperty("ownerId", cluster.getAccount().getUserId());

      jsonArray.add(obj);
    }

    responder.sendJson(HttpResponseStatus.OK, jsonArray);
  }

  /**
   * Get a specific cluster visible to the user.
   *
   * @param request Request for a cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster to get.
   */
  @GET
  @Path("/{cluster-id}")
  public void getCluster(HttpRequest request, HttpResponder responder,
                         @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      ClusterStoreView view = clusterStoreService.getView(account);
      Cluster cluster = view.getCluster(clusterId);
      if (cluster == null) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
        return;
      }

      JsonObject jsonObject = gson.toJsonTree(cluster).getAsJsonObject();

      // Update cluster Json with node information.
      Set<Node> clusterNodes = view.getClusterNodes(clusterId);
      jsonObject.add("nodes", gson.toJsonTree(clusterNodes));

      // Add last job message if any
      ClusterJob clusterJob = clusterStore.getClusterJob(JobId.fromString(cluster.getLatestJobId()));
      if (clusterJob.getStatusMessage() != null) {
        jsonObject.addProperty("message", clusterJob.getStatusMessage());
      }

      responder.sendJson(HttpResponseStatus.OK, jsonObject);
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception getting cluster " + clusterId);
    }
  }

  /**
   * Get the config used by the cluster.
   *
   * @param request Request for config of a cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster containing the config to get.
   */
  @GET
  @Path("/{cluster-id}/config")
  public void getClusterConfig(HttpRequest request, HttpResponder responder,
                               @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      Cluster cluster = clusterStoreService.getView(account).getCluster(clusterId);
      if (cluster == null) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
        return;
      }
      responder.sendJson(HttpResponseStatus.OK, cluster.getConfig());
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          "Exception getting config for cluster " + clusterId);
    }
  }

  /**
   * Get all services on a specific cluster visible to the user.
   *
   * @param request Request for services on a cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster containing the services to get.
   */
  @GET
  @Path("/{cluster-id}/services")
  public void getClusterServices(HttpRequest request, HttpResponder responder,
                                 @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      Cluster cluster = clusterStoreService.getView(account).getCluster(clusterId);
      if (cluster == null) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
        return;
      }
      responder.sendJson(HttpResponseStatus.OK, cluster.getServices());
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          "Exception getting services for cluster " + clusterId);
    }
  }

  /**
   * Get the status of a specific cluster visible to the user.
   *
   * @param request Request for cluster status.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose status to get.
   */
  @GET
  @Path("/{cluster-id}/status")
  public void getClusterStatus(HttpRequest request, HttpResponder responder,
                               @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      ClusterStoreView view = clusterStoreService.getView(account);
      Cluster cluster = view.getCluster(clusterId);
      if (cluster == null){
        responder.sendError(HttpResponseStatus.NOT_FOUND, String.format("cluster %s not found", clusterId));
        return;
      }

      ClusterJob job = clusterStore.getClusterJob(JobId.fromString(cluster.getLatestJobId()));
      if (job == null){
        responder.sendError(HttpResponseStatus.NOT_FOUND,
                            String.format("job %s not found for cluster %s", cluster.getLatestJobId(), clusterId));
        return;
      }

      responder.sendJson(HttpResponseStatus.OK, getClusterResponseJson(cluster, job));
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception getting status of cluster " + clusterId);
    }
  }

  protected static JsonObject getClusterResponseJson(Cluster cluster, ClusterJob job) {
    Map<String, ClusterTask.Status> taskStatus = job.getTaskStatus();

    int completedTasks = 0;
    for (Map.Entry<String, ClusterTask.Status> entry : taskStatus.entrySet()) {
      if (entry.getValue().equals(ClusterTask.Status.COMPLETE)) {
        completedTasks++;
      }
    }

    JsonObject object = new JsonObject();
    object.addProperty("clusterid", cluster.getId());
    object.addProperty("stepstotal", taskStatus.size());
    object.addProperty("stepscompleted", completedTasks);
    object.addProperty("status", cluster.getStatus().name());
    object.addProperty("actionstatus", job.getJobStatus().toString());
    object.addProperty("action", job.getClusterAction().name());

    return object;
  }

  /**
   * Create a new cluster. Body must include a cluster template and a number of machines.  Optionally it can include any
   * setting that will override the corresponding template default value.
   *
   * @param request Request to add a cluster.
   * @param responder Responder for sending the response.
   */
  @POST
  public void createCluster(HttpRequest request, HttpResponder responder) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);

    try {
      ClusterCreateRequest clusterCreateRequest = gson.fromJson(reader, ClusterCreateRequest.class);

      if (clusterCreateRequest.getNumMachines() > maxClusterSize) {
        responder.sendError(HttpResponseStatus.BAD_REQUEST, "numMachines above max cluster size " + maxClusterSize);
        return;
      }

      String id = clusterService.requestClusterCreate(clusterCreateRequest, account);
      JsonObject response = new JsonObject();
      response.addProperty("id", id);
      responder.sendJson(HttpResponseStatus.OK, response);
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User not authorized to create cluster.");
    } catch (IllegalArgumentException e) {
      LOG.error("Exception trying to create cluster.", e);
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      LOG.error("Exception while trying to create cluster.", e);
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error requesting cluster create operation.");
    } catch (QuotaException e) {
      responder.sendError(HttpResponseStatus.CONFLICT, e.getMessage());
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        LOG.warn("Exception while closing request reader", e);
      }
    }
  }

  /**
   * Delete a specific cluster that is deletable by the user.
   *
   * @param request Request to delete cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster to delete.
   */
  @DELETE
  @Path("/{cluster-id}")
  public void deleteCluster(HttpRequest request, HttpResponder responder,
                            @PathParam("cluster-id") String clusterId) {
    LOG.debug("Received a request to delete cluster {}", clusterId);
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      Cluster cluster = clusterStoreService.getView(account).getCluster(clusterId);
      if (cluster == null) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
        return;
      }

      if (cluster.getStatus() == Cluster.Status.TERMINATED) {
        responder.sendStatus(HttpResponseStatus.OK);
        return;
      }

      ClusterJob clusterJob = clusterStore.getClusterJob(JobId.fromString(cluster.getLatestJobId()));
      // If previous job on a cluster is still underway, don't accept new jobs
      if (cluster.getStatus() == Cluster.Status.PENDING) {
        String message = String.format("Job %s is still underway for cluster %s",
                                       clusterJob.getJobId(), clusterId);
        LOG.error(message);
        responder.sendError(HttpResponseStatus.CONFLICT, message);
        return;
      }
      clusterService.requestClusterDelete(clusterId, account);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error deleting cluster.");
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User unauthorized to perform delete.");
    }
  }

  /**
   * Abort the cluster operation that is currently running for the given cluster.
   *
   * @param request Request to abort the cluster operation.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster to abort.
   */
  @POST
  @Path("/{cluster-id}/abort")
  public void abortClusterJob(HttpRequest request, HttpResponder responder,
                              @PathParam("cluster-id") String clusterId) {
    LOG.debug("Received a request to abort job on cluster {}", clusterId);
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      clusterService.requestAbortJob(clusterId, account);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (MissingClusterException e) {
      responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
    } catch (IllegalStateException e) {
      responder.sendError(HttpResponseStatus.CONFLICT, "Cannot be aborted at this time.");
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error aborting cluster.");
    }
  }

  /**
   * Changes a cluster parameter like lease time.
   *
   * @param request Request to change cluster parameter.
   * @param responder Responder to send the response.
   * @param clusterId Id of the cluster to change.
   */
  @POST
  @Path("/{cluster-id}")
  public void changeClusterParameter(HttpRequest request, HttpResponder responder,
                                     @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      JsonObject jsonObject = gson.fromJson(request.getContent().toString(Charsets.UTF_8), JsonObject.class);
      if (jsonObject == null || !jsonObject.has("expireTime")) {
        responder.sendError(HttpResponseStatus.BAD_REQUEST, "expire time not specified");
        return;
      }

      long expireTime = jsonObject.get("expireTime").getAsLong();

      clusterService.changeExpireTime(clusterId, account, expireTime);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (IllegalArgumentException e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (JsonSyntaxException e) {
      LOG.error("Exception while parsing JSON.", e);
      responder.sendError(HttpResponseStatus.BAD_REQUEST, "Invalid JSON");
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User does not have permission to change cluster parameter.");
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception changing cluster parameter.");
    }
  }

  /**
   * Get the task plan for an operation that has taken place or is currently taking place on a cluster.
   *
   * @param request Request for the plan.
   * @param responder Responder to send the response.
   * @param clusterId Id of the cluster whose plan we want to get.
   * @param planId Id of the plan for the cluster.
   */
  @GET
  @Path("/{cluster-id}/plans/{plan-id}")
  public void getPlanForJob(HttpRequest request, HttpResponder responder,
                            @PathParam("cluster-id") String clusterId,
                            @PathParam("plan-id") String planId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      ClusterStoreView view = clusterStoreService.getView(account);
      Cluster cluster = view.getCluster(clusterId);
      if (cluster == null) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "cluster " + clusterId + " not found.");
        return;
      }

      JobId jobId = JobId.fromString(planId);
      ClusterJob clusterJob = clusterStore.getClusterJob(jobId);

      if (!clusterJob.getClusterId().equals(clusterId)) {
        throw new IllegalArgumentException(String.format("Job %s does not belong to cluster %s", planId, clusterId));
      }

      responder.sendJson(HttpResponseStatus.OK, formatJobPlan(clusterJob));
    } catch (IllegalArgumentException e) {
      LOG.error("Exception get plan {} for cluster {}.", planId, clusterId, e);
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception getting plan.");
    }
  }

  /**
   * Get all plans for cluster operations that have taken place or are currently taking place on a cluster.
   *
   * @param request Request for cluster plans.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose plans we have to fetch.
   */
  @GET
  @Path("/{cluster-id}/plans")
  public void getPlansForCluster(HttpRequest request, HttpResponder responder,
                                 @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {

      JsonArray jobsJson = new JsonArray();

      List<ClusterJob> jobs = clusterStoreService.getView(account).getClusterJobs(clusterId, -1);
      if (jobs.isEmpty()) {
        responder.sendError(HttpResponseStatus.NOT_FOUND, "Plans for cluster " + clusterId + " not found.");
        return;
      }
      for (ClusterJob clusterJob : jobs) {
        jobsJson.add(formatJobPlan(clusterJob));
      }

      responder.sendJson(HttpResponseStatus.OK, jobsJson);
    } catch (IllegalArgumentException e) {
      LOG.error("Exception getting plans for cluster {}.", clusterId, e);
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Exception getting cluster plans.");
    }
  }

  /**
   * Overwrite the config used by an active cluster. The POST body should contain a "config" key containing the new
   * cluster config. Additionally, the body can contain a "restart" key whose value is true or false, indicating
   * whether or not cluster services should be restarted along with being reconfigured. If restart is not specified,
   * it defaults to true.
   *
   * @param request Request for config of a cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster containing the config to get.
   */
  @PUT
  @Path("/{cluster-id}/config")
  public void putClusterConfig(HttpRequest request, HttpResponder responder,
                               @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    ClusterConfigureRequest configRequest;
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);
    try {
      configRequest = gson.fromJson(reader, ClusterConfigureRequest.class);
    } catch (Exception e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, "Invalid request body.");
      return;
    }

    try {
      clusterService.requestClusterReconfigure(clusterId, account,
                                               configRequest.getRestart(), configRequest.getConfig());
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (MissingClusterException e) {
      responder.sendError(HttpResponseStatus.NOT_FOUND, "Cluster " + clusterId + " not found.");
    } catch (IllegalStateException e) {
      responder.sendError(HttpResponseStatus.CONFLICT, "Cluster is not in a configurable state.");
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User is not authorized to perform a reconfigure.");
    } catch (IOException e) {
      LOG.error("Exception requesting reconfigure on cluster {}.", clusterId, e);
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          "Internal error while requesting cluster reconfigure");
    }
  }

  /**
   * Add specific services to a cluster. The POST body must be a JSON Object with a 'services' key whose value is a
   * JSON Array of service names. Services must be compatible with the template used when the cluster was created,
   * and any dependencies of services to add must either already be on the cluster, or also in the list of services
   * to add. If any of these rules are violated, a BAD_REQUEST status is returned back. Otherwise, the request to add
   * services is queued up.
   *
   * @param request Request to add services to a cluster.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster to add services to.
   */
  @POST
  @Path("/{cluster-id}/services")
  public void addClusterServices(HttpRequest request, HttpResponder responder,
                                 @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    AddServicesRequest addServicesRequest;
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);
    try {
      addServicesRequest = gson.fromJson(reader, AddServicesRequest.class);
    } catch (Exception e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, "Invalid request body.");
      return;
    }

    try {
      clusterService.requestAddServices(clusterId, account, addServicesRequest);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (IllegalArgumentException e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (MissingClusterException e) {
      responder.sendError(HttpResponseStatus.NOT_FOUND, "Cluster " + clusterId + " not found.");
    } catch (IllegalStateException e) {
      responder.sendError(HttpResponseStatus.CONFLICT,
                          "Cluster is not in a state where service actions can be performed.");
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User is not authorized to add services.");
    } catch (IOException e) {
      LOG.error("Exception requesting to add services to cluster {}.", clusterId, e);
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          "Internal error while requesting service action.");
    }
  }

  /**
   * Starts all services on the cluster, taking into account service dependencies for order of service starts.
   *
   * @param request Request to start cluster services.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose services should be started.
   */
  @POST
  @Path("/{cluster-id}/services/start")
  public void startAllClusterServices(HttpRequest request, HttpResponder responder,
                                      @PathParam("cluster-id") String clusterId) {
    requestServiceAction(request, responder, clusterId, null, ClusterAction.START_SERVICES);
  }

  /**
   * Stops all services on the cluster, taking into account service dependencies for order of service stops.
   *
   * @param request Request to stop cluster services.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose services should be stopped.
   */
  @POST
  @Path("/{cluster-id}/services/stop")
  public void stopAllClusterServices(HttpRequest request, HttpResponder responder,
                                     @PathParam("cluster-id") String clusterId) {
    requestServiceAction(request, responder, clusterId, null, ClusterAction.STOP_SERVICES);
  }

  /**
   * Restarts all services on the cluster, taking into account service dependencies for order of service stops
   * and starts.
   *
   * @param request Request to restart cluster services.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose services should be restarted.
   */
  @POST
  @Path("/{cluster-id}/services/restart")
  public void restartAllClusterServices(HttpRequest request, HttpResponder responder,
                                        @PathParam("cluster-id") String clusterId) {
    requestServiceAction(request, responder, clusterId, null, ClusterAction.RESTART_SERVICES);
  }

  /**
   * Starts the specified service, plus all services it depends on, on the cluster.
   *
   * @param request Request to start cluster service.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose services should be started.
   */
  @POST
  @Path("/{cluster-id}/services/{service-id}/start")
  public void startClusterService(HttpRequest request, HttpResponder responder,
                                  @PathParam("cluster-id") String clusterId,
                                  @PathParam("service-id") String serviceId) {
    requestServiceAction(request, responder, clusterId, serviceId, ClusterAction.START_SERVICES);
  }

  /**
   * Stops the specified service on the cluster, plus all services that depend on it,
   * taking into account service dependencies for order of service stops.
   *
   * @param request Request to stop cluster services.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose services should be stopped.
   */
  @POST
  @Path("/{cluster-id}/services/{service-id}/stop")
  public void stopClusterService(HttpRequest request, HttpResponder responder,
                                 @PathParam("cluster-id") String clusterId,
                                 @PathParam("service-id") String serviceId) {
    requestServiceAction(request, responder, clusterId, serviceId, ClusterAction.STOP_SERVICES);
  }

  /**
   * Restarts the specified service on the cluster, plus all services that depend on it,
   * taking into account service dependencies for order of service stops and starts.
   *
   * @param request Request to restart cluster service.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster whose service should be restarted.
   */
  @POST
  @Path("/{cluster-id}/services/{service-id}/restart")
  public void restartClusterService(HttpRequest request, HttpResponder responder,
                                    @PathParam("cluster-id") String clusterId,
                                    @PathParam("service-id") String serviceId) {
    requestServiceAction(request, responder, clusterId, serviceId, ClusterAction.RESTART_SERVICES);
  }

  /**
   * Sync the cluster template of the cluster to the current version of the cluster template. The cluster must be
   * active in order for this to work, and the cluster must be modifiable by the user making the request.
   *
   * @param request Request to sync the cluster template.
   * @param responder Responder for sending the response.
   * @param clusterId Id of the cluster that should be synced.
   */
  @POST
  @Path("/{cluster-id}/clustertemplate/sync")
  public void syncClusterTemplate(HttpRequest request, HttpResponder responder,
                                  @PathParam("cluster-id") String clusterId) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      clusterService.syncClusterToCurrentTemplate(clusterId, account);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (IllegalStateException e) {
      responder.sendError(HttpResponseStatus.CONFLICT, "Cluster is not in a state where the template can by synced");
    } catch (MissingEntityException e) {
      responder.sendError(HttpResponseStatus.NOT_FOUND, e.getMessage());
    } catch (InvalidClusterException e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN,
                          "User not authorized to perform template sync on cluster " + clusterId);
    } catch (IOException e) {
      LOG.error("Exception syncing template for cluster {}", clusterId, e);
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal error while syncing cluster template");
    }
  }

  private void requestServiceAction(HttpRequest request, HttpResponder responder, String clusterId,
                                    String service, ClusterAction action) {
    Account account = getAndAuthenticateAccount(request, responder);
    if (account == null) {
      return;
    }

    try {
      clusterService.requestServiceRuntimeAction(clusterId, account, action, service);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (MissingClusterException e) {
      responder.sendError(HttpResponseStatus.NOT_FOUND, "Cluster " + clusterId + " not found.");
    } catch (IllegalStateException e) {
      responder.sendError(HttpResponseStatus.CONFLICT,
                          "Cluster is not in a state where service actions can be performed.");
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.FORBIDDEN, "User not authorized to perform service action.");
    } catch (IOException e) {
      LOG.error("Exception performing service action for cluster {}", clusterId, e);
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal error performing service action");
    }
  }

  private JsonObject formatJobPlan(ClusterJob job) throws IOException {
    JsonObject jobJson = new JsonObject();
    jobJson.addProperty("id", job.getJobId());
    jobJson.addProperty("clusterId", job.getClusterId());
    jobJson.addProperty("action", job.getClusterAction().name());
    jobJson.addProperty("currentStage", job.getCurrentStageNumber());

    JsonArray stagesJson = new JsonArray();
    for (Set<String> stage : job.getStagedTasks()) {
      JsonArray stageJson = new JsonArray();
      for (String taskId : stage) {
        ClusterTask task = clusterStore.getClusterTask(TaskId.fromString(taskId));

        JsonObject taskJson = new JsonObject();
        taskJson.addProperty("id", task.getTaskId());
        taskJson.addProperty("taskName", task.getTaskName().name());
        taskJson.addProperty("nodeId", task.getNodeId());
        taskJson.addProperty("service", task.getService());

        stageJson.add(taskJson);
      }

      stagesJson.add(stageJson);
    }

    jobJson.add("stages", stagesJson);

    return jobJson;
  }
}
