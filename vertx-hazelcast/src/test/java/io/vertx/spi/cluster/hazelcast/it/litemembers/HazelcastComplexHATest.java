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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.spi.cluster.hazelcast.tests.Lifecycle;
import io.vertx.spi.cluster.hazelcast.tests.TestClusterManager;
import io.vertx.tests.ha.ComplexHATest;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Thomas Segismont
 */
public class HazelcastComplexHATest extends ComplexHATest {

  private static final int DATA_NODES = Integer.getInteger("litemembers.datanodes.count", 1);

  private List<HazelcastInstance> dataNodes = new ArrayList<>();

  @Override
  public void setUp() throws Exception {
    Random random = new Random();
    System.setProperty("vertx.hazelcast.test.group.name", new BigInteger(128, random).toString(32));
    for (int i = 0; i < DATA_NODES; i++) {
      dataNodes.add(Hazelcast.newHazelcastInstance(TestClusterManager.getConf(ConfigUtil.loadConfig())));
    }
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return TestClusterManager.getClusterManager(ConfigUtil.loadConfig().setLiteMember(true));
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
}
