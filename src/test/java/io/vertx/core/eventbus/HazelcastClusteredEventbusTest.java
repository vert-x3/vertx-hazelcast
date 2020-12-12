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

package io.vertx.core.eventbus;

import io.vertx.Lifecycle;
import io.vertx.LoggingTestWatcher;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import io.vertx.test.core.Repeat;

import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusteredEventbusTest extends ClusteredEventBusTest {

  @Rule
  public LoggingTestWatcher watchman = new LoggingTestWatcher();

  @Test
  public void testRoundRobinPTPMessages() throws Exception {
    Map<String, Long> counter = new HashMap<>();
    BiFunction<String, Long, Long> add = (address, old) -> old == null ? 1 : ++old;
    startNodes(2);
    vertices[0].eventBus().consumer(ADDRESS1, messgae -> counter.compute("node1", add));
    vertices[1].eventBus().consumer(ADDRESS1, messgae -> counter.compute("node2", add));
    IntStream.range(0, 10).forEach(i -> vertices[0].eventBus().send(ADDRESS1, "wololo"));
    vertices[0].setTimer(1000, id -> {
      assertEquals(counter.get("node1"), counter.get("node2"));
      testComplete();
    });
    await();
  }
  
  @Override
  public void setUp() throws Exception {
    Random random = new Random();
    System.setProperty("vertx.hazelcast.test.group.name", new BigInteger(128, random).toString(32));
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

  @Override
  protected void closeClustered(List<Vertx> clustered) throws Exception {
    Lifecycle.closeClustered(clustered);
  }
}
