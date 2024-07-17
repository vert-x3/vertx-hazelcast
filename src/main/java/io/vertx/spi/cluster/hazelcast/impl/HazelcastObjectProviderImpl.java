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
import com.hazelcast.map.IMap;
import io.vertx.core.VertxException;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Default implementation of {@link HazelcastObjectProvider} using open-source
 * Hazelcast features
 */
public class HazelcastObjectProviderImpl implements HazelcastObjectProvider {

  private static final String MAP_COUNTER_NAME = "__vertx.map.counter";
  private static final String MAP_LOCK_NAME = "__vertx.map.lock";

  private VertxInternal vertx;
  private HazelcastInstance hazelcast;
  private ExecutorService lockReleaseExec;

  @Override
  public void onJoin(VertxInternal vertx, HazelcastInstance hazelcast, ExecutorService lockReleaseExec) {
    this.vertx = vertx;
    this.hazelcast = hazelcast;
    this.lockReleaseExec = lockReleaseExec;
  }

  @Override
  public <K, V> AsyncMap<K, V> getAsyncMap(String name) {
    return new HazelcastAsyncMap<>(vertx, hazelcast.getMap(name));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    return hazelcast.getMap(name);
  }

  @Override
  public Lock getLockWithTimeout(String name, long timeout) {
    IMap<String, Object> lockMap = hazelcast.getMap(MAP_LOCK_NAME);
    boolean locked = false;
    long remaining = timeout;
    do {
      long start = System.nanoTime();
      try {
        locked = lockMap.tryLock(name, timeout, MILLISECONDS);
      } catch (InterruptedException e) {
        // OK continue
      }
      remaining = remaining - MILLISECONDS.convert(System.nanoTime() - start, NANOSECONDS);
    } while (!locked && remaining > 0);
    if (locked) {
      return new HazelcastLock(lockMap, name, lockReleaseExec);
    } else {
      throw new VertxException("Timed out waiting to get lock " + name, true);
    }
  }

  @Override
  public Counter createCounter(String name) {
    return new HazelcastCounter(vertx, hazelcast.getMap(MAP_COUNTER_NAME), name);
  }
}
