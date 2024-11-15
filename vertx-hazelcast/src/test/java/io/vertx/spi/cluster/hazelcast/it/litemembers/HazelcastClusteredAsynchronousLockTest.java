/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.spi.cluster.hazelcast.it.litemembers;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import io.vertx.spi.cluster.hazelcast.tests.Lifecycle;
import io.vertx.spi.cluster.hazelcast.tests.LoggingTestWatcher;
import io.vertx.tests.shareddata.ClusteredAsynchronousLockTest;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class HazelcastClusteredAsynchronousLockTest extends ClusteredAsynchronousLockTest {

  private static final int DATA_NODES = Integer.getInteger("litemembers.datanodes.count", 1);

  @Rule
  public LoggingTestWatcher watchman = new LoggingTestWatcher();

  private final List<HazelcastInstance> dataNodes = new ArrayList<>();

  @Override
  public void setUp() throws Exception {
    for (int i = 0; i < DATA_NODES; i++) {
      Config conf = ConfigUtil.loadConfig();
      dataNodes.add(Hazelcast.newHazelcastInstance(conf));
    }
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager(ConfigUtil.loadConfig().setLiteMember(true));
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    Lifecycle.closeDataNodes(dataNodes);
  }

  @Override
  protected void close(List<Vertx> clustered) throws Exception {
    Lifecycle.closeClustered(clustered);
  }

  @Override
  @Test
  public void testLockReleasedForClosedNode() throws Exception {
    super.testLockReleasedForClosedNode();
  }

  @Override
  @Test
  public void testLockReleasedForKilledNode() throws Exception {
    super.testLockReleasedForKilledNode();
  }
}
