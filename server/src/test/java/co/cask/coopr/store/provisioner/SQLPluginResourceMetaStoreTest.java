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
package co.cask.coopr.store.provisioner;

import co.cask.coopr.common.conf.Configuration;
import co.cask.coopr.common.conf.Constants;
import co.cask.coopr.common.conf.guice.ConfigurationModule;
import co.cask.coopr.store.guice.TestStoreModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.BeforeClass;

/**
 *
 */
public class SQLPluginResourceMetaStoreTest extends PluginResourceMetaStoreTest {
  private static SQLPluginMetaStoreService service;

  @BeforeClass
  public static void setupTestClass() throws Exception {
    Configuration conf = Configuration.create();
    conf.set(Constants.JDBC_DRIVER, "org.apache.derby.jdbc.EmbeddedDriver");
    conf.set(Constants.JDBC_CONNECTION_STRING, "jdbc:derby:memory:coopr;create=true");
    Injector injector = Guice.createInjector(
      new ConfigurationModule(conf),
      new TestStoreModule()
    );
    service = injector.getInstance(SQLPluginMetaStoreService.class);
    service.startAndWait();
  }

  @Override
  PluginMetaStoreService getPluginResourceMetaStoreService() throws Exception {
    return service;
  }

  @Override
  void clearData() throws Exception {
    service.clearData();
  }
}
