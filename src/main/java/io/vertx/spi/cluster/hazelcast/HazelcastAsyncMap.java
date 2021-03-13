/*
 * Copyright 2020 Red Hat, Inc.
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

package io.vertx.spi.cluster.hazelcast;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.impl.SharedDataImpl.WrappedAsyncMap;

/**
 * Extensions to the generic {@link AsyncMap}.
 */
@VertxGen
public interface HazelcastAsyncMap<K, V> {

  /**
   * Unwraps a generic {@link AsyncMap} to an {@link HazelcastAsyncMap}.
   *
   * @throws IllegalArgumentException if underlying implementation is not Hazelcast
   */
  @SuppressWarnings("unchecked")
  static <K, V> HazelcastAsyncMap<K, V> unwrap(AsyncMap asyncMap) {
    if (asyncMap instanceof WrappedAsyncMap) {
      WrappedAsyncMap wrappedAsyncMap = (WrappedAsyncMap) asyncMap;
      AsyncMap delegate = wrappedAsyncMap.getDelegate();
      if (delegate instanceof HazelcastAsyncMap) {
        return (HazelcastAsyncMap<K, V>) delegate;
      }
    }
    throw new IllegalArgumentException(String.valueOf(asyncMap != null ? asyncMap.getClass() : null));
  }

  /**
   * Updates the TTL (time to live) value of the entry specified by {@code k}
   * with a new TTL value.
   *
   * @param k  the key
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  default void setTtl(K k, long ttl, Handler<AsyncResult<Boolean>> completionHandler) {
    setTtl(k, ttl).onComplete(completionHandler);
  }

  /**
   * Same as {@link #setTtl(K, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Boolean> setTtl(K k, long ttl);
}
