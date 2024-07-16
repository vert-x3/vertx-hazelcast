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

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import io.vertx.core.Future;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.Counter;

/**
 * {@link Counter} implementation on top of Hazelcast {@link IMap}
 *
 * @implNote
 * Uses name of the counter as a key in the IMap and stores the counter as a value.
 * Operations are impemented using {@link IMap#submitToKey(Object, EntryProcessor)}
 * which runs on the partition owner for the particular key (so it may run on a
 * remote member).
 * In Hazelcast, the operations on a specific key are executed by a single
 * partition thread on the owner of the partition for the key.
 * This removes a lot of complexity when dealing with concurrent access
 * to the same key.
 */
public class HazelcastCounter implements Counter {

  private final VertxInternal vertx;
  private final IMap<String, Long> counterMap;
  private final String name;

  public HazelcastCounter(VertxInternal vertx, IMap<String, Long> counterMap, String name) {
    this.vertx = vertx;
    this.counterMap = counterMap;
    this.name = name;
    counterMap.putIfAbsent(name, 0L);
  }

  @Override
  public Future<Long> get() {
    return Future.fromCompletionStage(counterMap.getAsync(name), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> incrementAndGet() {
    return addAndGet(1);
  }

  @Override
  public Future<Long> getAndIncrement() {
    return getAndAdd(1);
  }

  @Override
  public Future<Long> decrementAndGet() {
    return addAndGet(-1);
  }

  @Override
  public Future<Long> addAndGet(long value) {
    return Future.fromCompletionStage(counterMap.submitToKey(name, entry -> {
      long newValue = entry.getValue() + value;
      entry.setValue(newValue);
      return newValue;
    }), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> getAndAdd(long value) {
    return Future.fromCompletionStage(counterMap.submitToKey(name, entry -> {
      long oldValue = entry.getValue();
      entry.setValue(oldValue + value);
      return oldValue;
    }), vertx.getOrCreateContext());
  }

  @Override
  public Future<Boolean> compareAndSet(long expected, long value) {
    return Future.fromCompletionStage(counterMap.submitToKey(name, entry -> {
      if (entry.getValue() == expected) {
        entry.setValue(value);
        return true;
      } else {
        return false;
      }
    }), vertx.getOrCreateContext());
  }
}
