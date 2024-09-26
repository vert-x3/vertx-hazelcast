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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final VertxInternal vertx;
  private final IMap<K, V> map;
  private final ConversionUtils conversionUtils;

  public HazelcastAsyncMap(VertxInternal vertx, ConversionUtils conversionUtils, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
    this.conversionUtils = conversionUtils;
  }

  @Override
  public Future<V> get(K k) {
    K kk = conversionUtils.convertParam(k);
    ContextInternal context = vertx.getOrCreateContext();
    return Future.fromCompletionStage(map.getAsync(kk), context).map(conversionUtils::convertReturn);
  }

  @Override
  public Future<Void> put(K k, V v) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    ContextInternal context = vertx.getOrCreateContext();
    return Future.fromCompletionStage(map.setAsync(kk, vv), context);
  }

  @Override
  public Future<V> putIfAbsent(K k, V v) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    return vertx.executeBlocking(() -> conversionUtils.convertReturn(map.putIfAbsent(kk, vv)), false);
  }

  @Override
  public Future<Void> put(K k, V v, long ttl) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    ContextInternal context = vertx.getOrCreateContext();
    CompletionStage<Void> completionStage = map.setAsync(kk, vv, ttl, MILLISECONDS);
    return Future.fromCompletionStage(completionStage, context);
  }

  @Override
  public Future<V> putIfAbsent(K k, V v, long ttl) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    return vertx.executeBlocking(() -> conversionUtils.convertReturn(map.putIfAbsent(kk, vv, ttl, MILLISECONDS)), false);
  }

  @Override
  public Future<V> remove(K k) {
    K kk = conversionUtils.convertParam(k);
    ContextInternal context = vertx.getOrCreateContext();
    CompletionStage<V> completionStage = map.removeAsync(kk);
    return Future.fromCompletionStage(completionStage, context).map(conversionUtils::convertReturn);
  }

  @Override
  public Future<Boolean> removeIfPresent(K k, V v) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    return vertx.executeBlocking(() -> map.remove(kk, vv), false);
  }

  @Override
  public Future<V> replace(K k, V v) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(v);
    return vertx.executeBlocking(() -> conversionUtils.convertReturn(map.replace(kk, vv)), false);
  }

  @Override
  public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
    K kk = conversionUtils.convertParam(k);
    V vv = conversionUtils.convertParam(oldValue);
    V vvv = conversionUtils.convertParam(newValue);
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
        K k = conversionUtils.convertReturn(kk);
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
        V v = conversionUtils.convertReturn(vv);
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
        K k = conversionUtils.convertReturn(entry.getKey());
        V v = conversionUtils.convertReturn(entry.getValue());
        result.put(k, v);
      }
      return result;
    }, false);
  }
}
