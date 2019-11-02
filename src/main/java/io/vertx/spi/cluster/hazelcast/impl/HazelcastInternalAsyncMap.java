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

import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.*;

public class HazelcastInternalAsyncMap<K, V> implements AsyncMap<K, V> {

  private final Vertx vertx;
  private final IMap<K, V> map;

  public HazelcastInternalAsyncMap(Vertx vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public Future<V> get(K k) {
    return executeAsync(map.getAsync(convertParam(k)));
  }

  @Override
  public Future<Void> put(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return executeAsync(map.setAsync(kk, vv));
  }

  @Override
  public Future<V> putIfAbsent(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(fut -> fut.complete(convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv)))));
  }

  @Override
  public Future<Void> put(K k, V v, long ttl) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return executeAsync((ICompletableFuture<Void>) map.putAsync(kk, vv, ttl, TimeUnit.MILLISECONDS));
  }

  @Override
  public Future<V> putIfAbsent(K k, V v, long ttl) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(fut -> fut.complete(convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv),
      ttl, TimeUnit.MILLISECONDS))));
  }

  @Override
  public Future<V> remove(K k) {
    K kk = convertParam(k);
    return executeAsync(map.removeAsync(kk));
  }

  @Override
  public Future<Boolean> removeIfPresent(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(fut -> fut.complete(map.remove(kk, vv)));
  }

  @Override
  public Future<V> replace(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(fut -> fut.complete(convertReturn(map.replace(kk, vv))));
  }

  @Override
  public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
    K kk = convertParam(k);
    V vv = convertParam(oldValue);
    V vvv = convertParam(newValue);
    return vertx.executeBlocking(fut -> fut.complete(map.replace(kk, vv, vvv)));
  }

  @Override
  public Future<Void> clear() {
    return vertx.executeBlocking(fut -> {
      map.clear();
      fut.complete();
    });
  }

  @Override
  public Future<Integer> size() {
    return vertx.executeBlocking(fut -> fut.complete(map.size()));
  }

  @Override
  public Future<Set<K>> keys() {
    return vertx.executeBlocking(fut -> {
      Set<K> set = new HashSet<>();
      for (K kk : map.keySet()) {
        K k = ConversionUtils.convertReturn(kk);
        set.add(k);
      }
      fut.complete(set);
    });
  }

  @Override
  public Future<List<V>> values() {
    return vertx.executeBlocking(fut -> {
      List<V> list = new ArrayList<>();
      for (V vv : map.values()) {
        V v = ConversionUtils.convertReturn(vv);
        list.add(v);
      }
      fut.complete(list);
    });
  }

  @Override
  public Future<Map<K, V>> entries() {
    return vertx.executeBlocking(fut -> {
      Map<K, V> result = new HashMap<>();
      for (Map.Entry<K, V> entry : map.entrySet()) {
        K k = ConversionUtils.convertReturn(entry.getKey());
        V v = ConversionUtils.convertReturn(entry.getValue());
        result.put(k, v);
      }
      fut.complete(result);
    });
  }

  private <T> Future<T> executeAsync(ICompletableFuture<T> future) {
    Promise<T> promise = ((VertxInternal) vertx).getOrCreateContext().promise();
    future.andThen(new HandlerCallBackAdapter<T>(promise));
    return promise.future();
  }
}
