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

package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.MapEvent;
import com.hazelcast.core.MultiMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V>, EntryListener<K, V> {

  private final VertxInternal vertx;
  private final MultiMap<K, V> map;
  private final TaskQueue taskQueue = new TaskQueue();


  /*
   The Hazelcast near cache is very slow so we use our own one.
   Keeping it in sync is a little tricky. As entries are added or removed the EntryListener will be called
   but when the node joins the cluster it isn't provided the initial state via the EntryListener
   Therefore the first time get is called for a subscription we *always* get the subs from
   Hazelcast (this is what the initialised flag is for), then consider that the initial state.
   While the get is in progress the entry listener may be being called, so we merge any
   pre-existing entries so we don't lose any. Hazelcast doesn't seem to have any consistent
   way to get an initial state plus a stream of updates.
    */
  private ConcurrentMap<K, ChoosableSet<V>> cache = new ConcurrentHashMap<>();

  public HazelcastAsyncMultiMap(Vertx vertx, MultiMap<K, V> map) {
    this.vertx = (VertxInternal) vertx;
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void removeAllForValue(V val, Handler<AsyncResult<Void>> completionHandler) {
    removeAllMatching(val::equals, completionHandler);
  }

  @Override
  public void removeAllMatching(Predicate<V> p, Handler<AsyncResult<Void>> completionHandler) {
    vertx.getOrCreateContext().executeBlocking(future -> {
      final Set<Map.Entry<K, V>> entries = map.entrySet();
      for (Map.Entry<K, V> entry : entries) {
        final V v = entry.getValue();
        if (p.test(v)) {
          map.remove(entry.getKey(), v);
        }
      }
      future.complete();
    }, taskQueue, completionHandler);
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    vertx.getOrCreateContext().executeBlocking(future -> {
      map.put(k, HazelcastClusterNodeInfo.convertClusterNodeInfo(v));
      future.complete();
    }, taskQueue, completionHandler);
  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> resultHandler) {
    ContextInternal context = vertx.getOrCreateContext();
    @SuppressWarnings("unchecked")
    Queue<GetRequest<K, V>> getRequests = (Queue<GetRequest<K, V>>) context.contextData().computeIfAbsent(this, ctx -> new ArrayDeque<>());
    synchronized (getRequests) {
      ChoosableSet<V> entries = cache.get(k);
      if (entries != null && entries.isInitialised() && getRequests.isEmpty()) {
        context.runOnContext(v -> resultHandler.handle(Future.succeededFuture(entries)));
      } else {
        getRequests.add(new GetRequest<>(k, resultHandler));
        if (getRequests.size() == 1) {
          dequeueGet(context, getRequests);
        }
      }
    }
  }

  private void dequeueGet(ContextInternal context, Queue<GetRequest<K, V>> getRequests) {
    GetRequest<K, V> getRequest;
    while (true) {
      getRequest = getRequests.peek();
      ChoosableSet<V> entries = cache.get(getRequest.key);
      if (entries == null || !entries.isInitialised()) {
        break;
      }
      Handler<AsyncResult<ChoosableIterable<V>>> handler = getRequest.handler;
      context.runOnContext(v -> handler.handle(Future.succeededFuture(entries)));
      getRequests.remove();
      if (getRequests.isEmpty()) {
        return;
      }
    }
    K key = getRequest.key;
    Handler<AsyncResult<ChoosableIterable<V>>> handler = getRequest.handler;
    context.<ChoosableIterable<V>>executeBlocking(fut -> {
      Collection<V> entries = map.get(key);
      ChoosableSet<V> sids = entries != null ? new ChoosableSet<>(entries) : new ChoosableSet<V>(0);
      ChoosableSet<V> prev = sids.isEmpty() ? null : cache.putIfAbsent(key, sids);
      if (prev != null) {
        // Merge them
        prev.merge(sids);
        sids = prev;
      }
      sids.setInitialised();
      fut.complete(sids);
    }, taskQueue, res -> {
      synchronized (getRequests) {
        context.runOnContext(v -> handler.handle(res));
        getRequests.remove();
        if (!getRequests.isEmpty()) {
          dequeueGet(context, getRequests);
        }
      }
    });
  }

  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
    vertx.getOrCreateContext().executeBlocking(future ->
        future.complete(map.remove(k, HazelcastClusterNodeInfo.convertClusterNodeInfo(v))),
      taskQueue, completionHandler
    );
  }

  @Override
  public void entryAdded(EntryEvent<K, V> entry) {
    cache.computeIfAbsent(entry.getKey(), key -> new ChoosableSet<>(1)).add(entry.getValue());
  }

  @Override
  public void entryRemoved(EntryEvent<K, V> entry) {
    final V oldValue = entry.getOldValue();
    if (oldValue == null) {
      // We forbid `null` values, but it can comes from another application using Hazelcast
      // (but not in the context of vert.x)
      return;
    }
    cache.compute(entry.getKey(), (key, entries) -> {
      if (entries == null) {
        return null;
      }
      if (entries.remove(oldValue) && entries.isEmpty()) {
        return null;
      }
      return entries;
    });
  }

  @Override
  public void entryUpdated(EntryEvent<K, V> entry) {
    final ChoosableSet<V> entries = cache.get(entry.getKey());
    if (entries != null) {
      entries.add(entry.getValue());
    }
  }

  @Override
  public void entryEvicted(EntryEvent<K, V> entry) {
    entryRemoved(entry);
  }

  @Override
  public void mapEvicted(MapEvent mapEvent) {
    clearCache();
  }

  @Override
  public void mapCleared(MapEvent mapEvent) {
    clearCache();
  }

  public void clearCache() {
    cache.clear();
  }

  private static class GetRequest<K, V> {
    final K key;
    final Handler<AsyncResult<ChoosableIterable<V>>> handler;

    GetRequest(K key, Handler<AsyncResult<ChoosableIterable<V>>> handler) {
      this.key = key;
      this.handler = handler;
    }
  }
}
