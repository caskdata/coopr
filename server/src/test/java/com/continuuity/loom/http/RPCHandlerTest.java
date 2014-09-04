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
package com.continuuity.loom.http;

import com.continuuity.loom.Entities;
import com.continuuity.loom.TestHelper;
import com.continuuity.loom.account.Account;
import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.cluster.Node;
import com.continuuity.loom.cluster.NodeProperties;
import com.continuuity.loom.common.conf.Constants;
import com.continuuity.loom.http.request.BootstrapRequest;
import com.continuuity.loom.provisioner.plugin.PluginType;
import com.continuuity.loom.provisioner.plugin.ResourceMeta;
import com.continuuity.loom.provisioner.plugin.ResourceStatus;
import com.continuuity.loom.provisioner.plugin.ResourceType;
import com.continuuity.loom.spec.HardwareType;
import com.continuuity.loom.spec.ImageType;
import com.continuuity.loom.spec.Provider;
import com.continuuity.loom.spec.ProvisionerAction;
import com.continuuity.loom.spec.Tenant;
import com.continuuity.loom.spec.TenantSpecification;
import com.continuuity.loom.spec.service.Service;
import com.continuuity.loom.spec.service.ServiceAction;
import com.continuuity.loom.spec.template.Administration;
import com.continuuity.loom.spec.template.ClusterDefaults;
import com.continuuity.loom.spec.template.ClusterTemplate;
import com.continuuity.loom.spec.template.Compatibilities;
import com.continuuity.loom.spec.template.LeaseDuration;
import com.continuuity.loom.store.entity.EntityStoreView;
import com.continuuity.loom.store.entity.SQLEntityStoreService;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Set;

/**
 *
 */
public class RPCHandlerTest extends ServiceTestBase {
  private static ClusterTemplate smallTemplate;

  @BeforeClass
  public static void initData() throws Exception {
    JsonObject defaultClusterConfig = new JsonObject();
    defaultClusterConfig.addProperty("defaultconfig", "value1");

    smallTemplate =  new ClusterTemplate("one-machine",
                                         "one machine cluster template",
                                         new ClusterDefaults(ImmutableSet.of("zookeeper"), "rackspace", null, null,
                                                             null, defaultClusterConfig),
                                         new Compatibilities(null, null, ImmutableSet.of("zookeeper")),
                                         null, new Administration(new LeaseDuration(10000, 30000, 5000)));
  }

  @Before
  public void setupTest() throws Exception {
    entityStoreService.getView(ADMIN_ACCOUNT).writeClusterTemplate(smallTemplate);
  }

  @After
  public void testCleanup() throws Exception {
    // cleanup
    solverQueues.removeAll();
    clusterQueues.removeAll();
    ((SQLEntityStoreService) entityStoreService).clearData();
  }

  @Test
  public void testNonAdminCantBootstrap() throws Exception {
    assertResponseStatus(doPost("/bootstrap", "", USER1_HEADERS), HttpResponseStatus.FORBIDDEN);
  }

  @Test
  public void testBootstrapTenant() throws Exception {
    // write superadmin entities
    EntityStoreView superadminView = entityStoreService.getView(Account.SUPERADMIN);
    Set<Provider> providers = ImmutableSet.of(Entities.ProviderExample.JOYENT, Entities.ProviderExample.RACKSPACE);
    Set<Service> services = ImmutableSet.of(Entities.ServiceExample.NAMENODE, Entities.ServiceExample.DATANODE);
    Set<HardwareType> hardwareTypes =
      ImmutableSet.of(Entities.HardwareTypeExample.SMALL, Entities.HardwareTypeExample.MEDIUM);
    Set<ImageType> imageTypes =
      ImmutableSet.of(Entities.ImageTypeExample.UBUNTU_12, Entities.ImageTypeExample.CENTOS_6);
    Set<ClusterTemplate> clusterTemplates =
      ImmutableSet.of(Entities.ClusterTemplateExample.HDFS, smallTemplate);
    for (Provider provider : providers) {
      superadminView.writeProvider(provider);
    }
    for (Service service : services) {
      superadminView.writeService(service);
    }
    for (HardwareType hardwareType : hardwareTypes) {
      superadminView.writeHardwareType(hardwareType);
    }
    for (ImageType imageType : imageTypes) {
      superadminView.writeImageType(imageType);
    }
    for (ClusterTemplate template : clusterTemplates) {
      superadminView.writeClusterTemplate(template);
    }
    // write superadmin plugin resources
    superadminView.writeAutomatorType(Entities.AutomatorTypeExample.CHEF);
    ResourceType type1 = new ResourceType(PluginType.AUTOMATOR, "chef-solo", "cookbooks");
    ResourceMeta meta1 = new ResourceMeta("name1", 3, ResourceStatus.ACTIVE);
    ResourceMeta meta2 = new ResourceMeta("name2", 2, ResourceStatus.INACTIVE);
    metaStoreService.getResourceTypeView(Account.SUPERADMIN, type1).add(meta1);
    metaStoreService.getResourceTypeView(Account.SUPERADMIN, type1).add(meta2);
    writePluginResource(Account.SUPERADMIN, type1, meta1.getName(), meta1.getVersion(), "meta1 contents");
    writePluginResource(Account.SUPERADMIN, type1, meta2.getName(), meta2.getVersion(), "meta2 contents");

    Account account = new Account(Constants.ADMIN_USER, "tenant-id123");
    tenantStore.writeTenant(new Tenant("tenant-id123", new TenantSpecification("tenantX", 0, 10, 100)));
    EntityStoreView tenantView = entityStoreService.getView(account);

    // bootstrap
    Header[] headers = {
      new BasicHeader(Constants.USER_HEADER, account.getUserId()),
      new BasicHeader(Constants.API_KEY_HEADER, API_KEY),
      new BasicHeader(Constants.TENANT_HEADER, "tenantX")
    };
    assertResponseStatus(doPost("/bootstrap", "", headers), HttpResponseStatus.OK);

    // make sure tenant account has copied superadmin entities
    Assert.assertEquals(providers, ImmutableSet.copyOf(tenantView.getAllProviders()));
    Assert.assertEquals(services, ImmutableSet.copyOf(tenantView.getAllServices()));
    Assert.assertEquals(hardwareTypes, ImmutableSet.copyOf(tenantView.getAllHardwareTypes()));
    Assert.assertEquals(imageTypes, ImmutableSet.copyOf(tenantView.getAllImageTypes()));
    Assert.assertEquals(clusterTemplates, ImmutableSet.copyOf(tenantView.getAllClusterTemplates()));
    // check tenant account has copied superadmin plugin resources
    Assert.assertEquals(meta1,
                        metaStoreService.getResourceTypeView(account, type1).get(meta1.getName(), meta1.getVersion()));
    Assert.assertEquals(meta2,
                        metaStoreService.getResourceTypeView(account, type1).get(meta2.getName(), meta2.getVersion()));
    Assert.assertEquals("meta1 contents", readPluginResource(account, type1, meta1.getName(), meta1.getVersion()));
    Assert.assertEquals("meta2 contents", readPluginResource(account, type1, meta2.getName(), meta2.getVersion()));
  }

  @Test
  public void testForceBootstrap() throws Exception {
    // superadmin has a slightly different version than tenant
    String name = "template";
    ClusterTemplate template1 = new ClusterTemplate(
      name, "description1",
      new ClusterDefaults(ImmutableSet.of("zookeeper"), "rackspace", null, null, null, null),
      null, null, null);
    ClusterTemplate template2 = new ClusterTemplate(
      name, "description2",
      new ClusterDefaults(ImmutableSet.of("zookeeper"), "rackspace", null, null, null, null),
      null, null, null);

    Account account = new Account(Constants.ADMIN_USER, "tenant-id123");
    tenantStore.writeTenant(new Tenant("tenant-id123", new TenantSpecification("tenantX", 0, 10, 100)));

    EntityStoreView superadminView = entityStoreService.getView(Account.SUPERADMIN);
    EntityStoreView tenantView = entityStoreService.getView(account);

    superadminView.writeClusterTemplate(template1);
    tenantView.writeClusterTemplate(template2);

    // check that with force false, bootstrap is not allowed
    Header[] headers = {
      new BasicHeader(Constants.USER_HEADER, account.getUserId()),
      new BasicHeader(Constants.API_KEY_HEADER, API_KEY),
      new BasicHeader(Constants.TENANT_HEADER, "tenantX")
    };
    BootstrapRequest body = new BootstrapRequest(false);
    assertResponseStatus(doPost("/bootstrap", gson.toJson(body), headers), HttpResponseStatus.CONFLICT);
    Assert.assertEquals(template2, tenantView.getClusterTemplate(name));

    // check that with force true, the template is overwritten
    body = new BootstrapRequest(true);
    assertResponseStatus(doPost("/bootstrap", gson.toJson(body), headers), HttpResponseStatus.OK);
    Assert.assertEquals(template1, tenantView.getClusterTemplate(name));
  }

  @Test
  public void testInvalidGetNodePropertiesReturns400() throws Exception {
    // not a json object
    assertResponseStatus(doPost("/getNodeProperties", "body", USER1_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);

    // no cluster id
    JsonObject requestBody = new JsonObject();
    assertResponseStatus(doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);

    // bad cluster id
    requestBody = new JsonObject();
    requestBody.add("clusterId", new JsonObject());
    assertResponseStatus(doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);

    // bad properties
    requestBody = new JsonObject();
    requestBody.addProperty("properties", "prop1,prop2");
    assertResponseStatus(doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);

    // bad services
    requestBody = new JsonObject();
    requestBody.addProperty("services", "service1,service2");
    assertResponseStatus(doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);
  }

  @Test
  public void testGetNodeProperties() throws Exception {
    // setup data, 4 node cluster
    Service svcA =
      new Service("svcA", "", ImmutableSet.<String>of(), ImmutableMap.<ProvisionerAction, ServiceAction>of());
    Service svcB =
      new Service("svcB", "", ImmutableSet.<String>of(), ImmutableMap.<ProvisionerAction, ServiceAction>of());
    Service svcC =
      new Service("svcC", "", ImmutableSet.<String>of(), ImmutableMap.<ProvisionerAction, ServiceAction>of());

    Node nodeA = new Node("nodeA", "123", ImmutableSet.of(svcA),
                          NodeProperties.builder()
                            .setHostname("testcluster-1-1000.local")
                            .addIPAddress("access_v4", "123.456.0.1").build());
    Node nodeAB = new Node("nodeAB", "123", ImmutableSet.of(svcA, svcB),
                           NodeProperties.builder()
                             .setHostname("testcluster-1-1001.local")
                             .addIPAddress("access_v4", "123.456.0.2").build());
    Node nodeABC = new Node("nodeABC", "123", ImmutableSet.of(svcA, svcB, svcC),
                            NodeProperties.builder()
                              .setHostname("testcluster-1-1002.local")
                              .addIPAddress("access_v4", "123.456.0.3").build());
    Node nodeBC = new Node("nodeBC", "123", ImmutableSet.of(svcB, svcC),
                           NodeProperties.builder()
                             .setHostname("testcluster-1-1003.local")
                             .addIPAddress("access_v4", "123.456.0.4").build());
    Cluster cluster = Cluster.builder()
      .setID("123")
      .setAccount(USER1_ACCOUNT)
      .setName("testcluster")
      .setProvider(Entities.ProviderExample.RACKSPACE)
      .setClusterTemplate(smallTemplate)
      .setNodes(ImmutableSet.of(nodeA.getId(), nodeAB.getId(), nodeABC.getId(), nodeBC.getId()))
      .setServices(ImmutableSet.of(svcA.getName(), svcB.getName(), svcC.getName()))
      .build();
    clusterStoreService.getView(USER1_ACCOUNT).writeCluster(cluster);
    clusterStore.writeNode(nodeA);
    clusterStore.writeNode(nodeAB);
    clusterStore.writeNode(nodeABC);
    clusterStore.writeNode(nodeBC);

    // test with nonexistant cluster
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("clusterId", "123" + cluster.getId());
    HttpResponse response = doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    JsonObject responseBody = getJsonObjectBodyFromResponse(response);
    Assert.assertTrue(responseBody.entrySet().isEmpty());

    // test with unowned cluster
    requestBody.addProperty("clusterId", cluster.getId());
    response = doPost("/getNodeProperties", requestBody.toString(), USER2_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    responseBody = getJsonObjectBodyFromResponse(response);
    Assert.assertTrue(responseBody.entrySet().isEmpty());

    // test without any filters
    response = doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    responseBody = getJsonObjectBodyFromResponse(response);
    JsonObject expected = new JsonObject();
    expected.add(nodeA.getId(), gson.toJsonTree(nodeA.getProperties()));
    expected.add(nodeAB.getId(), gson.toJsonTree(nodeAB.getProperties()));
    expected.add(nodeABC.getId(), gson.toJsonTree(nodeABC.getProperties()));
    expected.add(nodeBC.getId(), gson.toJsonTree(nodeBC.getProperties()));
    Assert.assertEquals(expected, responseBody);

    // test with filter on service A
    requestBody.add("services", TestHelper.jsonArrayOf(svcA.getName()));
    response = doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    responseBody = getJsonObjectBodyFromResponse(response);
    expected = new JsonObject();
    Set<Node> expectedNodes = ImmutableSet.of(nodeA, nodeAB, nodeABC);
    for (Node expectedNode : expectedNodes) {
      expected.add(expectedNode.getId(), gson.toJsonTree(expectedNode.getProperties()));
    }
    Assert.assertEquals(expected, responseBody);

    // test with filter on service A and property list
    requestBody.add("properties", TestHelper.jsonArrayOf("hostname", "ipaddresses"));
    response = doPost("/getNodeProperties", requestBody.toString(), USER1_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    responseBody = getJsonObjectBodyFromResponse(response);
    expected = new JsonObject();
    expectedNodes = ImmutableSet.of(nodeA, nodeAB, nodeABC);
    for (Node expectedNode : expectedNodes) {
      JsonObject value = new JsonObject();
      value.addProperty("hostname", expectedNode.getProperties().getHostname());
      value.add("ipaddresses", gson.toJsonTree(expectedNode.getProperties().getIPAddresses()));
      expected.add(expectedNode.getId(), value);
    }
    Assert.assertEquals(expected, responseBody);
  }

  private JsonObject getJsonObjectBodyFromResponse(HttpResponse response) throws IOException {
    Reader reader = new InputStreamReader(response.getEntity().getContent(), Charsets.UTF_8);
    return gson.fromJson(reader, JsonObject.class);
  }

  private void writePluginResource(Account account, ResourceType resourceType,
                                   String name, int version, String content) throws IOException {
    OutputStream outputStream = pluginStore.getResourceOutputStream(account, resourceType, name, version);
    try {
      outputStream.write(content.getBytes(Charsets.UTF_8));
    } finally {
      outputStream.close();
    }
  }

  private String readPluginResource(Account account, ResourceType resourceType,
                                    String name, int version) throws IOException {
    Reader reader = new InputStreamReader(
      pluginStore.getResourceInputStream(account, resourceType, name, version), Charsets.UTF_8);
    try {
      return CharStreams.toString(reader);
    } finally {
      reader.close();
    }
  }
}
