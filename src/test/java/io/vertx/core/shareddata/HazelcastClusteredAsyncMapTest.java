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

package io.vertx.core.shareddata;

import com.hazelcast.map.impl.record.Record;
import io.vertx.Lifecycle;
import io.vertx.LoggingTestWatcher;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastAsyncMap;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusteredAsyncMapTest extends ClusteredAsyncMapTest {

  @Rule
  public LoggingTestWatcher watchman = new LoggingTestWatcher();

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

  @Test
  public void testMapSetTtl() {
    getVertx().sharedData().<String, String>getAsyncMap("foo", onSuccess(map -> {
      map.put("pipo", "molo", Record.UNSET, onSuccess(vd -> {
        getVertx().sharedData().<String, String>getAsyncMap("foo", onSuccess(map2 -> {
          HazelcastAsyncMap<String, String> hzMap2 = HazelcastAsyncMap.unwrap(map);
          hzMap2.setTtl("pipo", 10, onSuccess(bool -> {
            assertTrue(bool);
            checkMapSetTtl(map, "pipo", 15);
          }));
        }));
      }));
    }));
    await();
  }

  private void checkMapSetTtl(AsyncMap<String, String> map, String k, long delay) {
    vertx.setTimer(delay, l -> {
      map.get(k, onSuccess(value -> {
        if (value == null) {
          testComplete();
        } else {
          checkMapSetTtl(map, k, delay);
        }
      }));
    });
  }

  @Override
  @Test
  @Ignore("Hazelcast removes the binding even if a new entry is added without ttl")
  public void testMapPutTtlThenPut() {
    super.testMapPutTtlThenPut();
  }
}
