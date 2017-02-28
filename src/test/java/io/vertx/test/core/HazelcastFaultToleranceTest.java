/*
 * Copyright 2017 Red Hat, Inc.
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

package io.vertx.test.core;

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author Thomas Segismont
 */
public class HazelcastFaultToleranceTest extends FaultToleranceTest {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  private String groupName;
  private String password;

  @Override
  public void setUp() throws Exception {
    Random random = new Random();
    groupName = new BigInteger(128, random).toString(32);
    System.setProperty("vertx.hazelcast.test.group.name", groupName);
    password = new BigInteger(128, random).toString(32);
    System.setProperty("vertx.hazelcast.test.group.password", password);
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

  @Override
  protected List<String> getExternalNodeSystemProperties() {
    return Arrays.asList("-Djava.net.preferIPv4Stack=true", "-Dhazelcast.wait.seconds.before.join=0", "-Dhazelcast.local.localAddress=127.0.0.1",
      "-Dvertx.hazelcast.test.group.name=" + groupName, "-Dvertx.hazelcast.test.group.password=" + password);
  }

  @Override
  protected void waitForClusterStability(VertxInternal vertx) throws Exception {
    super.waitForClusterStability(vertx);
    // Additionnal wait to make sure all nodes noticed the shutdowns
    Thread.sleep(15_000);
  }
}
