/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ProgrammaticHazelcastClusterManagerTest extends AsyncTestBase {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");

    // this is only checked once every 10 seconds by Hazelcast on client disconnect
    System.setProperty("hazelcast.client.max.no.heartbeat.seconds", "9");
  }

  @Override
  public void setUp() throws Exception {
    Random random = new Random();
    System.setProperty("vertx.hazelcast.test.group.name", new BigInteger(128, random).toString(32));
    System.setProperty("vertx.hazelcast.test.group.password", new BigInteger(128, random).toString(32));
    super.setUp();
  }

  private void testProgrammatic(HazelcastClusterManager mgr, Config config) throws Exception {
    mgr.setConfig(config);
    assertEquals(config, mgr.getConfig());
    VertxOptions options = new VertxOptions().setClusterManager(mgr).setClustered(true);
    Vertx.clusteredVertx(options, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr.getHazelcastInstance());
      res.result().close(res2 -> {
        assertTrue(res2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testProgrammaticSetConfig() throws Exception {
    Config config = new Config();
    HazelcastClusterManager mgr = new HazelcastClusterManager();
    mgr.setConfig(config);
    testProgrammatic(mgr, config);
  }

  @Test
  public void testProgrammaticSetWithConstructor() throws Exception {
    Config config = new Config();
    HazelcastClusterManager mgr = new HazelcastClusterManager(config);
    testProgrammatic(mgr, config);
  }

  @Test
  public void testCustomHazelcastInstance() throws Exception {
    HazelcastInstance instance = Hazelcast.newHazelcastInstance(new Config());
    HazelcastClusterManager mgr = new HazelcastClusterManager(instance);
    testProgrammatic(mgr, instance.getConfig());
  }

  @Test
  public void testEventBusWhenUsingACustomHazelcastInstance() throws Exception {
    HazelcastInstance instance1 = Hazelcast.newHazelcastInstance(new Config());
    HazelcastInstance instance2 = Hazelcast.newHazelcastInstance(new Config());

    HazelcastClusterManager mgr1 = new HazelcastClusterManager(instance1);
    HazelcastClusterManager mgr2 = new HazelcastClusterManager(instance2);
    VertxOptions options1 = new VertxOptions().setClusterManager(mgr1).setClustered(true).setClusterHost("127.0.0.1");
    VertxOptions options2 = new VertxOptions().setClusterManager(mgr2).setClustered(true).setClusterHost("127.0.0.1");

    AtomicReference<Vertx> vertx1 = new AtomicReference<>();
    AtomicReference<Vertx> vertx2 = new AtomicReference<>();

    Vertx.clusteredVertx(options1, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr1.getHazelcastInstance());
      res.result().eventBus().consumer("news", message -> {
        assertNotNull(message);
        assertTrue(message.body().equals("hello"));
        testComplete();
      });
      vertx1.set(res.result());
    });

    waitUntil(() -> vertx1.get() != null);

    Vertx.clusteredVertx(options2, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr2.getHazelcastInstance());
      vertx2.set(res.result());
      res.result().eventBus().send("news", "hello");
    });

    await();

    vertx1.get().close(ar -> vertx1.set(null));
    vertx2.get().close(ar -> vertx2.set(null));

    assertTrue(instance1.getLifecycleService().isRunning());
    assertTrue(instance2.getLifecycleService().isRunning());

    waitUntil(() -> vertx1.get() == null  && vertx2.get() == null);

    instance1.shutdown();
    instance2.shutdown();

  }

  @Test
  public void testSharedDataUsingCustomHazelcast() throws Exception {
    HazelcastInstance instance1 = Hazelcast.newHazelcastInstance(new Config());
    HazelcastInstance instance2 = Hazelcast.newHazelcastInstance(new Config());

    HazelcastClusterManager mgr1 = new HazelcastClusterManager(instance1);
    HazelcastClusterManager mgr2 = new HazelcastClusterManager(instance2);
    VertxOptions options1 = new VertxOptions().setClusterManager(mgr1).setClustered(true).setClusterHost("127.0.0.1");
    VertxOptions options2 = new VertxOptions().setClusterManager(mgr2).setClustered(true).setClusterHost("127.0.0.1");

    AtomicReference<Vertx> vertx1 = new AtomicReference<>();
    AtomicReference<Vertx> vertx2 = new AtomicReference<>();

    Vertx.clusteredVertx(options1, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr1.getHazelcastInstance());
      res.result().sharedData().getClusterWideMap("mymap1", ar -> {
        ar.result().put("news", "hello", v -> {
          vertx1.set(res.result());
        });
      });
    });

    waitUntil(() -> vertx1.get() != null);

    Vertx.clusteredVertx(options2, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr2.getHazelcastInstance());
      vertx2.set(res.result());
      res.result().sharedData().getClusterWideMap("mymap1", ar -> {
        ar.result().get("news", r -> {
          assertEquals("hello", r.result());
          testComplete();
        });
      });
    });

    await();

    vertx1.get().close(ar -> vertx1.set(null));
    vertx2.get().close(ar -> vertx2.set(null));

    waitUntil(() -> vertx1.get() == null  && vertx2.get() == null);

    // be sure stopping vertx did not cause or require our custom hazelcast to shutdown

    assertTrue(instance1.getLifecycleService().isRunning());
    assertTrue(instance2.getLifecycleService().isRunning());

    instance1.shutdown();
    instance2.shutdown();

  }

  @Test
  public void testThatExternalHZInstanceCanBeShutdown() {
    // This instance won't be used by vert.x
    GroupConfig groupConfig = new GroupConfig()
      .setName(System.getProperty("vertx.hazelcast.test.group.name"))
      .setPassword(System.getProperty("vertx.hazelcast.test.group.password"));
    HazelcastInstance instance = Hazelcast.newHazelcastInstance(new Config().setGroupConfig(groupConfig));
    String nodeID = instance.getCluster().getLocalMember().getUuid();
    instance.getCluster().addMembershipListener(new MembershipListener() {
      @Override
      public void memberAdded(MembershipEvent membershipEvent) {

      }

      @Override
      public void memberRemoved(MembershipEvent membershipEvent) {

      }

      @Override
      public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {

      }
    });

    HazelcastClusterManager mgr = new HazelcastClusterManager();
    VertxOptions options = new VertxOptions().setClusterManager(mgr).setClustered(true).setClusterHost("127.0.0.1");

    AtomicReference<Vertx> vertx1 = new AtomicReference<>();

    Vertx.clusteredVertx(options, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr.getHazelcastInstance());
      res.result().sharedData().getClusterWideMap("mymap1", ar -> {
        ar.result().put("news", "hello", v -> {
          vertx1.set(res.result());
        });
      });
    });

    waitUntil(() -> vertx1.get() != null);
    int size = mgr.getNodes().size();
    assertTrue(mgr.getNodes().contains(nodeID));

    // Retrieve the value inserted by vert.x
    Map<Object, Object> map = instance.getMap("mymap1");
    Map<Object, Object> anotherMap = instance.getMap("mymap2");
    assertEquals(map.get("news"), "hello");
    map.put("another-key", "stuff");
    anotherMap.put("another-key", "stuff");
    map.remove("news");
    map.remove("another-key");
    anotherMap.remove("another-key");

    instance.shutdown();

    waitUntil(() -> mgr.getNodes().size() == size - 1);
    vertx1.get().close();
    vertx1.get().close(ar -> vertx1.set(null));

    waitUntil(() -> vertx1.get() == null);
  }

  @Test
  public void testSharedDataUsingCustomHazelcastClients() throws Exception {
    HazelcastInstance dataNode1 = Hazelcast.newHazelcastInstance(new Config());
    HazelcastInstance dataNode2 = Hazelcast.newHazelcastInstance(new Config());

    ClientConfig clientConfig = new ClientConfig().setGroupConfig(new GroupConfig("dev", "dev-pass"));

    HazelcastInstance clientNode1 = HazelcastClient.newHazelcastClient(clientConfig);
    HazelcastInstance clientNode2 = HazelcastClient.newHazelcastClient(clientConfig);

    HazelcastClusterManager mgr1 = new HazelcastClusterManager(clientNode1);
    HazelcastClusterManager mgr2 = new HazelcastClusterManager(clientNode2);
    VertxOptions options1 = new VertxOptions().setClusterManager(mgr1).setClustered(true).setClusterHost("127.0.0.1");
    VertxOptions options2 = new VertxOptions().setClusterManager(mgr2).setClustered(true).setClusterHost("127.0.0.1");

    AtomicReference<Vertx> vertx1 = new AtomicReference<>();
    AtomicReference<Vertx> vertx2 = new AtomicReference<>();

    Vertx.clusteredVertx(options1, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr1.getHazelcastInstance());
      res.result().sharedData().getClusterWideMap("mymap1", ar -> {
        ar.result().put("news", "hello", v -> {
          vertx1.set(res.result());
        });
      });
    });

    waitUntil(() -> vertx1.get() != null);

    Vertx.clusteredVertx(options2, res -> {
      assertTrue(res.succeeded());
      assertNotNull(mgr2.getHazelcastInstance());
      vertx2.set(res.result());
      res.result().sharedData().getClusterWideMap("mymap1", ar -> {
        ar.result().get("news", r -> {
          assertEquals("hello", r.result());
          testComplete();
        });
      });
    });

    await();

    vertx1.get().close(ar -> vertx1.set(null));
    vertx2.get().close(ar -> vertx2.set(null));

    waitUntil(() -> vertx1.get() == null && vertx2.get() == null);

    // be sure stopping vertx did not cause or require our custom hazelcast to shutdown

    assertTrue(clientNode1.getLifecycleService().isRunning());
    assertTrue(clientNode2.getLifecycleService().isRunning());

    clientNode1.shutdown();
    clientNode2.shutdown();

    assertTrue(dataNode1.getLifecycleService().isRunning());
    assertTrue(dataNode2.getLifecycleService().isRunning());

    dataNode1.shutdown();
    dataNode2.shutdown();
  }
}
