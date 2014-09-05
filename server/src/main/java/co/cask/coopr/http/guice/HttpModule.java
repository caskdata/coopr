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
package co.cask.coopr.http.guice;

import com.continuuity.http.HttpHandler;
import co.cask.coopr.http.handler.AdminHandler;
import co.cask.coopr.http.handler.ClusterHandler;
import co.cask.coopr.http.handler.NodeHandler;
import co.cask.coopr.http.handler.PluginHandler;
import co.cask.coopr.http.handler.ProvisionerHandler;
import co.cask.coopr.http.handler.RPCHandler;
import co.cask.coopr.http.handler.StatusHandler;
import co.cask.coopr.http.handler.SuperadminHandler;
import co.cask.coopr.http.handler.TaskHandler;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Guice bindings for http related classes.
 */
public class HttpModule extends AbstractModule {

  @Override
  protected void configure() {

    Multibinder<HttpHandler> handlerBinder = Multibinder.newSetBinder(binder(), HttpHandler.class);
    handlerBinder.addBinding().to(AdminHandler.class);
    handlerBinder.addBinding().to(ClusterHandler.class);
    handlerBinder.addBinding().to(NodeHandler.class);
    handlerBinder.addBinding().to(TaskHandler.class);
    handlerBinder.addBinding().to(StatusHandler.class);
    handlerBinder.addBinding().to(RPCHandler.class);
    handlerBinder.addBinding().to(SuperadminHandler.class);
    handlerBinder.addBinding().to(ProvisionerHandler.class);
    handlerBinder.addBinding().to(PluginHandler.class);
  }
}
