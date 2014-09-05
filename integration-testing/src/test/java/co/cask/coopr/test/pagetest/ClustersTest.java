/**
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
package co.cask.coopr.test.pagetest;

import co.cask.coopr.cluster.Cluster;
import co.cask.coopr.test.Constants;
import co.cask.coopr.test.GenericTest;
import co.cask.coopr.test.TestUtil;
import co.cask.coopr.test.drivers.Global;
import co.cask.coopr.test.input.ClusterReader;
import co.cask.coopr.test.input.TestCluster;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static co.cask.coopr.test.drivers.Global.globalDriver;
import static org.junit.Assert.assertEquals;

/**
 * Test GET /admin/clusters
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClustersTest extends GenericTest {
  private static final TestUtil TEST_UTIL = new TestUtil();
  private static final ClusterReader CLUSTER_READER = new ClusterReader();
  private static final By ACTIVE_CLUSTER = By.cssSelector("#active-clusters");
  private static final By DEL_CLUSTER = By.cssSelector("#deleted-clusters");
  private static final By TABLE = By.cssSelector(".table.table-striped");

  @BeforeClass
  public static void runInitial() throws  Exception {
    globalDriver.get(Constants.CLUSTERS_URL);
    Global.driverWait(1);
  }

  @Test
  public void test_01_leftpanel() {
    assertEquals("Leftpanel is not correct.", Constants.LEFT_PANEL, TEST_UTIL.getLeftPanel(globalDriver));
  }

  @Test
  public void test_03_activeClusters() throws Exception {
    WebElement activeTable = globalDriver.findElement(ACTIVE_CLUSTER).findElement(TABLE);
    assertEquals("active clusters is not correct.", CLUSTER_READER.getClusters(Cluster.Status.ACTIVE),
                 getActualClusters(activeTable));
  }

  @Test
  public void test_04_nonactiveClusters() throws Exception {
    // Click cluster header to make it visible.
    globalDriver.findElement(By.cssSelector(".delete-cluster-header")).click();
    WebElement delTable = globalDriver.findElement(DEL_CLUSTER).findElement(TABLE);
    assertEquals("nonactive clusters is not correct.", CLUSTER_READER.getClusters(Cluster.Status.TERMINATED),
                 getActualClusters(delTable));
  }

  @Test
  public void test_05_testClustersSelected() {
    assertEquals("Clusters menu item is not selected",
                 "active nav-item last", TEST_UTIL.getHTMLClasses("#nav-clusters-container"));
  }

  @Test
  public void test_06_topmenu() {
    ImmutableSet<String> expectedTopList = ImmutableSet.of("test-woo", "coopr-prod");
    assertEquals("The list of the topmenu is not correct.", expectedTopList, TEST_UTIL.getTopList(globalDriver));
    String uriPrefix = Constants.ROOT_URL + "/user/clusters/cluster/";
    ImmutableSet<String> clusterIds = ImmutableSet.of("00000139", "00000138");
    assertEquals("The uri of top list is not correct.", TEST_UTIL.getTopListUri(clusterIds, uriPrefix),
                 TEST_UTIL.getTopListUri(globalDriver));
  }

  private Set<TestCluster> getActualClusters(WebElement table) throws ParseException {
    Set<TestCluster> clusters = Sets.newHashSet();
    List<WebElement> tableRows = TEST_UTIL.getRows(table);
    for (int i = 0; i < tableRows.size(); i++) {
      //String name, String clusterId, String date, String template, int nodeNumber
      WebElement tr = tableRows.get(i);
      List<WebElement> tds = TEST_UTIL.geTdsFromTr(tr);
      String name = tds.get(0).getText();
      String clusterId = tds.get(2).getText();
      String date = tds.get(3).getText();
      SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      f.setTimeZone(TimeZone.getTimeZone("GMT"));
      Date d = f.parse(date);
      long timestamp = d.getTime();
      String template = tds.get(4).getText();
      int nodeNumber = Integer.parseInt(tds.get(5).getText());
      clusters.add(new TestCluster(name, clusterId, timestamp, template, nodeNumber));
    }
    return clusters;
  }

  @AfterClass
  public static void tearDown() {
    closeDriver();
  }
}
