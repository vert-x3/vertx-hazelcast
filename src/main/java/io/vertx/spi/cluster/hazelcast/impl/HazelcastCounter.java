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

import com.hazelcast.cp.IAtomicLong;
import io.vertx.core.Future;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Counter;

public class HazelcastCounter implements Counter {

  private final VertxInternal vertx;
  private final IAtomicLong atomicLong;

  public HazelcastCounter(VertxInternal vertx, IAtomicLong atomicLong) {
    this.vertx = vertx;
    this.atomicLong = atomicLong;
  }

  @Override
  public Future<Long> get() {
    return Future.fromCompletionStage(atomicLong.getAsync(), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> incrementAndGet() {
    return Future.fromCompletionStage(atomicLong.incrementAndGetAsync(), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> getAndIncrement() {
    return Future.fromCompletionStage(atomicLong.getAndIncrementAsync(), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> decrementAndGet() {
    return Future.fromCompletionStage(atomicLong.decrementAndGetAsync(), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> addAndGet(long value) {
    return Future.fromCompletionStage(atomicLong.addAndGetAsync(value), vertx.getOrCreateContext());
  }

  @Override
  public Future<Long> getAndAdd(long value) {
    return Future.fromCompletionStage(atomicLong.getAndAddAsync(value), vertx.getOrCreateContext());
  }

  @Override
  public Future<Boolean> compareAndSet(long expected, long value) {
    return Future.fromCompletionStage(atomicLong.compareAndSetAsync(expected, value), vertx.getOrCreateContext());
  }
}
