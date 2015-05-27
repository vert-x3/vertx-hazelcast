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

import io.vertx.core.Context;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.hazelcast.HazelcastClusterManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMapTest extends AsyncMultiMapTest {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

  @Test
  public void testKeepOrderOfGetsOnSameContext() {
    int size = 100;
    ConcurrentLinkedDeque<Integer> result = new ConcurrentLinkedDeque<>();
    ConcurrentLinkedDeque<Integer> expected = new ConcurrentLinkedDeque<>();
    Context context = vertices[0].getOrCreateContext();
    context.runOnContext(v -> {
      for (int i = 0;i < size;i++) {
        expected.add(i);
        int i2 = i;
        map.get("some-sub", onSuccess(res -> {
          assertTrue(res.isEmpty());
          result.add(i2);
          if (result.size() == size) {
            // Now it should be initialized
            for (int j = size;j < size * 2;j++) {
              int j2 = j;
              expected.add(j);
              map.get("some-sub", onSuccess(res2 -> {
                result.add(j2);
                if (result.size() == size * 2) {
                  assertEquals(new ArrayList<>(expected), new ArrayList<>(result));
                  testComplete();
                }
              }));
            }
          }
        }));
      }
    });
    await();
  }
}
