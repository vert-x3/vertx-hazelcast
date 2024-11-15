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

package io.vertx.spi.cluster.hazelcast.tests.shareddata;

import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import io.vertx.spi.cluster.hazelcast.tests.Lifecycle;
import io.vertx.spi.cluster.hazelcast.tests.LoggingTestWatcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusteredAsyncMapTest extends io.vertx.tests.shareddata.ClusteredAsyncMapTest {

  @Rule
  public LoggingTestWatcher watchman = new LoggingTestWatcher();


  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

  @Override
  protected void close(List<Vertx> clustered) throws Exception {
    Lifecycle.closeClustered(clustered);
  }

  @Override
  @Test
  @Ignore("Hazelcast removes the binding even if a new entry is added without ttl")
  public void testMapPutTtlThenPut() {
    super.testMapPutTtlThenPut();
  }

  @Override
  @Test
  @Ignore
  public void testMapReplaceIfPresentTtl() {
    super.testMapReplaceIfPresentTtl();
  }

  @Override
  @Test
  @Ignore
  public void testMapReplaceIfPresentTtlWhenNotPresent() {
    super.testMapReplaceIfPresentTtlWhenNotPresent();
  }

  @Override
  @Test
  @Ignore
  public void testMapReplaceTtl() {
    super.testMapReplaceTtl();
  }

  @Override
  @Test
  @Ignore
  public void testMapReplaceTtlWithPreviousValue() {
    super.testMapReplaceTtlWithPreviousValue();
  }
}
