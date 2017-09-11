/*
 * Copyright 2017 Red Hat, Inc.
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

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Thomas Segismont
 */
public class IterableStream<T> implements ReadStream<T> {

  private static final int BATCH_SIZE = 10;

  private final Context context;
  private final Supplier<Iterable<T>> iterableSupplier;
  private final Function<T, T> converter;

  private Iterable<T> iterable;
  private Iterator<T> iterator;
  private Deque<T> queue;
  private Handler<T> dataHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private boolean readInProgress;

  public IterableStream(Context context, Supplier<Iterable<T>> iterableSupplier, Function<T, T> converter) {
    this.context = context;
    this.iterableSupplier = iterableSupplier;
    this.converter = converter;
  }

  @Override
  public synchronized IterableStream<T> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized IterableStream<T> handler(Handler<T> handler) {
    this.dataHandler = handler;
    context.<Iterable<T>>executeBlocking(fut -> fut.complete(iterableSupplier.get()), ar -> {
      synchronized (this) {
        if (ar.succeeded()) {
          iterable = ar.result();
          if (dataHandler != null && !paused) {
            doRead();
          }
        } else if (exceptionHandler != null) {
          exceptionHandler.handle(ar.cause());
        }
      }
    });
    return this;
  }

  @Override
  public synchronized IterableStream<T> pause() {
    paused = true;
    return this;
  }

  @Override
  public synchronized IterableStream<T> resume() {
    if (paused) {
      paused = false;
      if (dataHandler != null) {
        doRead();
      }
    }
    return this;
  }

  private synchronized void doRead() {
    if (readInProgress) {
      return;
    }
    readInProgress = true;
    if (iterator == null) {
      iterator = iterable.iterator();
    }
    if (queue == null) {
      queue = new ArrayDeque<>(BATCH_SIZE);
    }
    if (!queue.isEmpty()) {
      context.runOnContext(v -> emitQueued());
      return;
    }
    for (int i = 0; i < BATCH_SIZE && iterator.hasNext(); i++) {
      queue.add(iterator.next());
    }
    if (queue.isEmpty()) {
      context.runOnContext(v -> {
        synchronized (this) {
          readInProgress = false;
          if (endHandler != null) {
            endHandler.handle(null);
          }
        }
      });
      return;
    }
    context.runOnContext(v -> emitQueued());
  }

  private synchronized void emitQueued() {
    while (!queue.isEmpty() && dataHandler != null && !paused) {
      dataHandler.handle(converter.apply(queue.remove()));
    }
    readInProgress = false;
    if (dataHandler != null && !paused) {
      doRead();
    }
  }

  @Override
  public synchronized IterableStream<T> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
