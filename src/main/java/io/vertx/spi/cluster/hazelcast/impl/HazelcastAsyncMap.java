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

import com.hazelcast.map.IMap;
import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;

import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.convertParam;
import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.convertReturn;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final VertxInternal vertx;
  private final IMap<K, V> map;

  public HazelcastAsyncMap(VertxInternal vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public Future<V> get(K k) {
    K kk = convertParam(k);
    ContextInternal context = vertx.getOrCreateContext();
    return Future.fromCompletionStage(map.getAsync(kk), context).map(ConversionUtils::convertReturn);
  }

  @Override
  public Future<Void> put(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    ContextInternal context = vertx.getOrCreateContext();
    return Future.fromCompletionStage(map.setAsync(kk, HazelcastServerID.convertServerID(vv)), context);
  }

  @Override
  public Future<V> putIfAbsent(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(() -> convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv))), false);
  }

  @Override
  public Future<Void> put(K k, V v, long ttl) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    ContextInternal context = vertx.getOrCreateContext();
    CompletionStage<Void> completionStage = map.setAsync(kk, HazelcastServerID.convertServerID(vv), ttl, MILLISECONDS);
    return Future.fromCompletionStage(completionStage, context);
  }

  @Override
  public Future<V> putIfAbsent(K k, V v, long ttl) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(() -> convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv), ttl, MILLISECONDS)), false);
  }

  @Override
  public Future<V> remove(K k) {
    K kk = convertParam(k);
    ContextInternal context = vertx.getOrCreateContext();
    CompletionStage<V> completionStage = map.removeAsync(kk);
    return Future.fromCompletionStage(completionStage, context).map(ConversionUtils::convertReturn);
  }

  @Override
  public Future<Boolean> removeIfPresent(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(() -> map.remove(kk, vv), false);
  }

  @Override
  public Future<V> replace(K k, V v) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    return vertx.executeBlocking(() -> convertReturn(map.replace(kk, vv)), false);
  }

  @Override
  public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
    K kk = convertParam(k);
    V vv = convertParam(oldValue);
    V vvv = convertParam(newValue);
    return vertx.executeBlocking(() -> map.replace(kk, vv, vvv), false);
  }

  @Override
  public Future<Void> clear() {
    return vertx.executeBlocking(() -> {
      map.clear();
      return null;
    }, false);
  }

  @Override
  public Future<Integer> size() {
    return vertx.executeBlocking(map::size, false);
  }

  @Override
  public Future<Set<K>> keys() {
    return vertx.executeBlocking(() -> {
      Set<K> set = new HashSet<>();
      for (K kk : map.keySet()) {
        K k = ConversionUtils.convertReturn(kk);
        set.add(k);
      }
      return set;
    }, false);
  }

  @Override
  public Future<List<V>> values() {
    return vertx.executeBlocking(() -> {
      List<V> list = new ArrayList<>();
      for (V vv : map.values()) {
        V v = ConversionUtils.convertReturn(vv);
        list.add(v);
      }
      return list;
    }, false);
  }

  @Override
  public Future<Map<K, V>> entries() {
    return vertx.executeBlocking(() -> {
      Map<K, V> result = new HashMap<>();
      for (Entry<K, V> entry : map.entrySet()) {
        K k = ConversionUtils.convertReturn(entry.getKey());
        V v = ConversionUtils.convertReturn(entry.getValue());
        result.put(k, v);
      }
      return result;
    }, false);
  }
}
