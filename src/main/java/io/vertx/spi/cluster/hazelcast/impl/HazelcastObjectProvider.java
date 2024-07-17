/*
 * Copyright 2020 Red Hat, Inc.
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

package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * SPI to allow different implementations of Map, AsyncMap, Lock and Counter
 */
public interface HazelcastObjectProvider {

  /**
   * Lifecycle method to initialize this provider when all dependencies become available,
   */
  void onJoin(VertxInternal vertx, HazelcastInstance hazelcast, ExecutorService lockReleaseExec);

  /**
   * Return {@link AsyncMap} for the given name
   */
  <K, V> AsyncMap<K, V> getAsyncMap(String name);

  /**
   * Return synchronous map for the given name
   */
  <K, V> Map<K,V> getSyncMap(String name);

  /**
   * Return Lock with the given name within specified timeout (in ms)
   */
  Lock getLockWithTimeout(String name, long timeout);

  /**
   * Return Counter with given name, if the Counter doesn't exist it is initialized to 0
   */
  Counter createCounter(String name);
}
