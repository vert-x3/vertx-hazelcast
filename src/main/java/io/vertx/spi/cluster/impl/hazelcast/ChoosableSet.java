/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.spi.cluster.impl.hazelcast;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.spi.cluster.ChoosableIterable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ChoosableSet<T> implements ChoosableIterable<T> {

  private boolean initialised;
  private final LinkedList<Handler<AsyncResult<ChoosableIterable<T>>>> waiters = new LinkedList<>();
  private final Set<T> ids;
  private volatile Iterator<T> iter;

  public ChoosableSet(int initialSize) {
    ids = new ConcurrentHashSet<>(initialSize);
  }

  public int size() {
    return ids.size();
  }

  public void setInitialised() {
    setInitialised(null);
  }

  void setInitialised(Throwable failure) {
    while (true) {
      Handler<AsyncResult<ChoosableIterable<T>>> handler;
      synchronized (this) {
        if (waiters.isEmpty()) {
          initialised = true;
          return;
        }
        handler = waiters.removeFirst();
      }
      if (failure == null) {
        handler.handle(Future.succeededFuture(this));
      } else {
        handler.handle(Future.failedFuture(failure));
      }
    }
  }

  boolean isInitialized(Context context, Handler<AsyncResult<ChoosableIterable<T>>> handler) {
    synchronized (this) {
      if (!initialised) {
        waiters.add(ar -> {
          context.runOnContext(v -> {
            handler.handle(ar);
          });
        });
        return false;
      }
    }
    return true;
  }

  public void add(T elem) {
    ids.add(elem);
  }

  public void remove(T elem) {
    ids.remove(elem);
  }

  public void merge(ChoosableSet<T> toMerge) {
    ids.addAll(toMerge.ids);
  }

  public boolean isEmpty() {
    return ids.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return ids.iterator();
  }

  public synchronized T choose() {
    if (!ids.isEmpty()) {
      if (iter == null || !iter.hasNext()) {
        iter = ids.iterator();
      }
      try {
        return iter.next();
      } catch (NoSuchElementException e) {
        return null;
      }
    } else {
      return null;
    }
  }
}
