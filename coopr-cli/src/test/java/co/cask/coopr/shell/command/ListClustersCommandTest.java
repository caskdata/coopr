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

import co.cask.common.cli.exception.InvalidCommandException;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * {@link ListClustersCommand} class test.
 */
public class ListClustersCommandTest extends AbstractTest {

  private static final String INPUT = "list clusters";

  @Test
  public void executeTest() throws IOException, InvalidCommandException {
    CLI.execute(INPUT, System.out);

    Mockito.verify(CLUSTER_CLIENT).getClusters();
  }
}
