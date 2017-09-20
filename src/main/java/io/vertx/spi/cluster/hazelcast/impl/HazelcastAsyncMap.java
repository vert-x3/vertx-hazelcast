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

import com.hazelcast.core.IMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.*;

public class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final Vertx vertx;
  private final IMap<K, V> map;

  public HazelcastAsyncMap(Vertx vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.get(kk))), resultHandler);
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> {
      map.set(kk, HazelcastServerID.convertServerID(vv));
      fut.complete();
    }, completionHandler);
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv)))),
                          resultHandler);
  }

  @Override
  public void put(K k, V v, long ttl, Handler<AsyncResult<Void>> completionHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> {
      map.set(kk, HazelcastServerID.convertServerID(vv), ttl, TimeUnit.MILLISECONDS);
      fut.complete();
    }, completionHandler);
  }

  @Override
  public void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv),
      ttl, TimeUnit.MILLISECONDS))), resultHandler);
  }

  @Override
  public void remove(K k, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.remove(kk))), resultHandler);
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> fut.complete(map.remove(kk, vv)), resultHandler);
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.replace(kk, vv))), resultHandler);
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(oldValue);
    V vvv = convertParam(newValue);
    vertx.executeBlocking(fut -> fut.complete(map.replace(kk, vv, vvv)), resultHandler);
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(fut -> {
      map.clear();
      fut.complete();
    }, resultHandler);
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> resultHandler) {
    vertx.executeBlocking(fut -> fut.complete(map.size()), resultHandler);
  }

  @Override
  public void keys(Handler<AsyncResult<Set<K>>> resultHandler) {
    vertx.executeBlocking(fut -> {
      Set<K> set = new HashSet<>();
      for (K kk : map.keySet()) {
        K k = ConversionUtils.convertReturn(kk);
        set.add(k);
      }
      fut.complete(set);
    }, resultHandler);
  }

  @Override
  public void values(Handler<AsyncResult<List<V>>> resultHandler) {
    vertx.executeBlocking(fut -> {
      List<V> list = new ArrayList<>();
      for (V vv : map.values()) {
        V v = ConversionUtils.convertReturn(vv);
        list.add(v);
      }
      fut.complete(list);
    }, resultHandler);
  }

  @Override
  public void entries(Handler<AsyncResult<Map<K, V>>> resultHandler) {
    vertx.executeBlocking(fut -> {
      Map<K, V> result = new HashMap<>();
      for (Entry<K, V> entry : map.entrySet()) {
        K k = ConversionUtils.convertReturn(entry.getKey());
        V v = ConversionUtils.convertReturn(entry.getValue());
        result.put(k, v);
      }
      fut.complete(result);
    }, resultHandler);
  }
}
