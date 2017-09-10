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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * @author Thomas Segismont
 */
public class IterableStream<T> implements ReadStream<T> {

  private static final int BATCH_SIZE = 10;

  private final Context context;
  private final Iterable<T> iterable;
  private final Function<T, T> converter;

  private Iterator<T> iterator;
  private Handler<T> dataHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private boolean readInProgress;

  public IterableStream(Context context, Iterable<T> iterable, Function<T, T> converter) {
    this.context = context;
    this.iterable = iterable;
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
    if (dataHandler != null && !paused) {
      doRead();
    }
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
    context.<List<T>>executeBlocking(fut -> {
      List<T> values = new ArrayList<>(BATCH_SIZE);
      for (int i = 0; i < BATCH_SIZE && iterator.hasNext(); i++) {
        values.add(iterator.next());
      }
      fut.complete(values);
    }, false, ar -> {
      if (ar.failed()) {
        Handler<Throwable> exceptionHandler = this.exceptionHandler;
        if (exceptionHandler != null) {
          exceptionHandler.handle(ar.cause());
        }
      } else {
        synchronized (this) {
          List<T> values = ar.result();
          if (values.isEmpty()) {
            readInProgress = false;
            Handler<Void> endHandler = this.endHandler;
            if (endHandler != null) {
              context.runOnContext(v -> {
                endHandler.handle(null);
              });
            }
          } else {
            Handler<T> dataHandler = this.dataHandler;
            if (dataHandler != null) {
              context.runOnContext(v -> {
                for (T value : values) {
                  dataHandler.handle(converter.apply(value));
                }
                synchronized (this) {
                  readInProgress = false;
                }
                context.runOnContext(v2 -> {
                  synchronized (this) {
                    if (this.dataHandler != null && !paused) {
                      doRead();
                    }
                  }
                });
              });
            }
          }
        }
      }
    });
  }

  @Override
  public synchronized IterableStream<T> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
