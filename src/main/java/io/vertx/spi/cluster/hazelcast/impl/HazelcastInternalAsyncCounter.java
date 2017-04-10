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
 */package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ICompletableFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
    public void get(Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.getAsync(), resultHandler);
    }

    @Override
    public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.incrementAndGetAsync(), resultHandler);
    }

    @Override
    public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.getAndIncrementAsync(), resultHandler);
    }

    @Override
    public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.decrementAndGetAsync(), resultHandler);
    }

    @Override
    public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.addAndGetAsync(value), resultHandler);
    }

    @Override
    public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.getAndAddAsync(value), resultHandler);
    }

    @Override
    public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
        Objects.requireNonNull(resultHandler, "resultHandler");
        executeAsync(atomicLong.compareAndSetAsync(expected, value),
                        resultHandler);
    }

    private <T> void executeAsync(ICompletableFuture<T> future,
                                  Handler<AsyncResult<T>> resultHandler) {
        future.andThen(
                new HandlerCallBackAdapter(resultHandler),
                VertxExecutorAdapter.getOrCreate(vertx.getOrCreateContext())
        );
    }
}
