/*
 * Copyright (c) 2011-2016 The original author or authors
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
package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ICompletableFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.shareddata.Counter;

import java.util.Objects;

/**
 *
 */
public class HazelcastInternalAsyncCounter
  implements Counter {

  private final IAtomicLong atomicLong;
  private final Vertx vertx;


  public HazelcastInternalAsyncCounter(Vertx vertx, IAtomicLong atomicLong) {
    this.vertx = vertx;
    this.atomicLong = atomicLong;
  }

  @Override
  public Future<Long> get() {
    return executeAsync(atomicLong.getAsync());
  }

  @Override
  public void get(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    get().setHandler(resultHandler);
  }

  @Override
  public Future<Long> incrementAndGet() {
    return executeAsync(atomicLong.incrementAndGetAsync());
  }

  @Override
  public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    incrementAndGet().setHandler(resultHandler);
  }

  @Override
  public Future<Long> getAndIncrement() {
    return executeAsync(atomicLong.getAndIncrementAsync());
  }

  @Override
  public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndIncrement().setHandler(resultHandler);
  }

  @Override
  public Future<Long> decrementAndGet() {
    return executeAsync(atomicLong.decrementAndGetAsync());
  }

  @Override
  public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    decrementAndGet().setHandler(resultHandler);
  }

  @Override
  public Future<Long> addAndGet(long value) {
    return executeAsync(atomicLong.addAndGetAsync(value));
  }

  @Override
  public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    addAndGet(value).setHandler(resultHandler);
  }

  @Override
  public Future<Long> getAndAdd(long value) {
    return executeAsync(atomicLong.getAndAddAsync(value));
  }

  @Override
  public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndAdd(value).setHandler(resultHandler);
  }

  @Override
  public Future<Boolean> compareAndSet(long expected, long value) {
    return executeAsync(atomicLong.compareAndSetAsync(expected, value));
  }

  @Override
  public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    compareAndSet(expected, value).setHandler(resultHandler);
  }

  private <T> Future<T> executeAsync(ICompletableFuture<T> future) {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    io.vertx.core.Promise<T> promise = ctx.promise();
    future.andThen(new HandlerCallBackAdapter<>(promise));
    return promise.future();
  }
}
