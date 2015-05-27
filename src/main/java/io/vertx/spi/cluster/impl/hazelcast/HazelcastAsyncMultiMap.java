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

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.MapEvent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V>, EntryListener<K, V> {

  private static final Logger log = LoggerFactory.getLogger(HazelcastAsyncMultiMap.class);

  private final Vertx vertx;
  private final com.hazelcast.core.MultiMap<K, V> map;

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

  public HazelcastAsyncMultiMap(Vertx vertx, com.hazelcast.core.MultiMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void removeAllForValue(V val, Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(fut -> {
      for (Map.Entry<K, V> entry : map.entrySet()) {
        V v = entry.getValue();
        if (val.equals(v)) {
          map.remove(entry.getKey(), v);
        }
      }
      fut.complete();
    }, completionHandler);
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(fut -> {
      map.put(k, HazelcastServerID.convertServerID(v));
      fut.complete();
    }, completionHandler);
  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> resultHandler) {
    while (true) {
      ChoosableSet<V> entries = cache.get(k);
      if (entries != null) {
        Context ctx = vertx.getOrCreateContext();
        if (entries.isInitialized(ctx, resultHandler)) {
          resultHandler.handle(Future.succeededFuture(entries));
        }
        break;
      } else {
        ChoosableSet<V> inserted = new ChoosableSet<>(0);
        if (cache.putIfAbsent(k, inserted) == null) {
          vertx.<Collection<V>>executeBlocking(
              fut -> fut.complete(map.get(k)),
              ar -> {
            if (ar.succeeded()) {
              Collection<V> entries2 = ar.result();
              if (entries2 != null) {
                entries2.forEach(inserted::add);
              }
              resultHandler.handle(Future.succeededFuture(inserted));
              inserted.setInitialised();
            } else {
              resultHandler.handle(Future.failedFuture(ar.cause()));
            }
          });
          break;
        }
      }
    }
  }

  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
    vertx.executeBlocking(fut -> fut.complete(map.remove(k, HazelcastServerID.convertServerID(v))), completionHandler);
  }

  @Override
  public void entryAdded(EntryEvent<K, V> entry) {
    addEntry(entry.getKey(), entry.getValue());
  }

  private void addEntry(K k, V v) {
    ChoosableSet<V> entries = cache.get(k);
    if (entries == null) {
      entries = new ChoosableSet<>(1);
      ChoosableSet<V> prev = cache.putIfAbsent(k, entries);
      entries.add(v);
      entries.setInitialised();
      if (prev == null) {
        return;
      } else {
        entries = prev;
      }
    }
    entries.add(v);
  }

  @Override
  public void entryRemoved(EntryEvent<K, V> entry) {
    removeEntry(entry.getKey(), entry.getValue());
  }

  private void removeEntry(K k, V v) {
    ChoosableSet<V> entries = cache.get(k);
    if (entries != null) {
      entries.remove(v);
      if (entries.isEmpty()) {
        cache.remove(k);
      }
    }
  }

  @Override
  public void entryUpdated(EntryEvent<K, V> entry) {
    K k = entry.getKey();
    ChoosableSet<V> entries = cache.get(k);
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
    cache.clear();
  }

  @Override
  public void mapCleared(MapEvent mapEvent) {
    cache.clear();
  }

}
