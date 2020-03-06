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

import com.hazelcast.core.IAtomicLong;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Counter;

import java.util.Objects;

public class HazelcastCounter implements Counter {

  private final Vertx vertx;
  private final IAtomicLong atomicLong;


  public HazelcastCounter(Vertx vertx, IAtomicLong atomicLong) {
    this.vertx = vertx;
    this.atomicLong = atomicLong;
  }

  @Override
  public Future<Long> get() {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.get()));
  }

  @Override
  public Future<Long> incrementAndGet() {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.incrementAndGet()));
  }

  @Override
  public Future<Long> getAndIncrement() {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.getAndIncrement()));
  }

  @Override
  public Future<Long> decrementAndGet() {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.decrementAndGet()));
  }

  @Override
  public Future<Long> addAndGet(long value) {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.addAndGet(value)));
  }

  @Override
  public Future<Long> getAndAdd(long value) {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.getAndAdd(value)));
  }

  @Override
  public Future<Boolean> compareAndSet(long expected, long value) {
    return vertx.executeBlocking(fut -> fut.complete(atomicLong.compareAndSet(expected, value)));
  }

  @Override
  public void get(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    get().setHandler(resultHandler);
  }

  @Override
  public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    incrementAndGet().setHandler(resultHandler);
  }

  @Override
  public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndIncrement().setHandler(resultHandler);
  }

  @Override
  public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    decrementAndGet().setHandler(resultHandler);
  }

  @Override
  public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    addAndGet(value).setHandler(resultHandler);
  }

  @Override
  public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndAdd(value).setHandler(resultHandler);
  }

  @Override
  public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    compareAndSet(expected, value).setHandler(resultHandler);
  }
}
