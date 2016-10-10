/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMapTest extends AsyncMultiMapTest {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  @Override
  public void setUp() throws Exception {
    Random random = new Random();
    System.setProperty("vertx.hazelcast.test.group.name", new BigInteger(128, random).toString(32));
    System.setProperty("vertx.hazelcast.test.group.password", new BigInteger(128, random).toString(32));
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

  @Test
  public void shouldNotAddToMapCacheIfKeyDoesntAlreadyExist() throws Exception {
    String nonexistentKey = "non-existent-key." + UUID.randomUUID();

    map.get(nonexistentKey, ar -> {
      if (ar.succeeded()) {
        try {
          ChoosableIterable<ServerID> s = ar.result();
          Map<String, ChoosableIterable<ServerID>> cache = getCacheFromMap();

          // System.err.println("CACHE CONTENTS: " + cache);

          // check result
          assertNotNull(s);
          assertTrue(s.isEmpty());

          // check cache
          assertNotNull(cache);
          assertFalse(
              "Map cache should not contain key " + nonexistentKey,
              cache.containsKey(nonexistentKey));

        } catch (Exception e) {
          fail(e.toString());
        } finally {
          testComplete();
        }
      } else {
        fail(ar.cause().toString());
      }
    });

    await();
  }

  @SuppressWarnings("unchecked")
  private Map<String, ChoosableIterable<ServerID>> getCacheFromMap() throws Exception {
    Field field = map.getClass().getDeclaredField("cache");
    field.setAccessible(true);
    return (Map<String, ChoosableIterable<ServerID>>) field.get(map);
  }
}
