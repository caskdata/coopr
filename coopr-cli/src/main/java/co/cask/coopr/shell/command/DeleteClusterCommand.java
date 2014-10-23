/*
 * Copyright © 2012-2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.cask.coopr.shell.command;

import co.cask.common.cli.Arguments;
import co.cask.common.cli.Command;
import co.cask.coopr.client.ClusterClient;
import co.cask.coopr.http.request.ClusterOperationRequest;
import co.cask.coopr.shell.util.CliUtil;

import java.io.PrintStream;
import com.google.inject.Inject;

import static co.cask.coopr.shell.util.Constants.CLUSTER_ID_KEY;
import static co.cask.coopr.shell.util.Constants.PROVIDER_FIELDS_KEY;

/**
 * Deletes a cluster.
 */
public class DeleteClusterCommand implements Command {

  private final ClusterClient clusterClient;

  @Inject
  private DeleteClusterCommand(ClusterClient clusterClient) {
    this.clusterClient = clusterClient;
  }

  @Override
  public void execute(Arguments arguments, PrintStream printStream) throws Exception {
    String id = arguments.get(CLUSTER_ID_KEY);
    ClusterOperationRequest clusterOperationRequest = CliUtil.getObjectFromJson(arguments, PROVIDER_FIELDS_KEY,
                                                                                ClusterOperationRequest.class);
    if (clusterOperationRequest == null) {
      clusterClient.deleteCluster(id);
    } else {
      clusterClient.deleteCluster(id, clusterOperationRequest);
    }
  }

  @Override
  public String getPattern() {
    return String.format("delete cluster <%s> [with provider fields <%s>]", CLUSTER_ID_KEY, PROVIDER_FIELDS_KEY);
  }

  @Override
  public String getDescription() {
    return "Deletes a cluster";
  }
}