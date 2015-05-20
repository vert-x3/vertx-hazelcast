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

import com.hazelcast.core.IMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final Vertx vertx;
  private final IMap<K, V> map;

  public HazelcastAsyncMap(Vertx vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> asyncResultHandler) {
    K kk = convertParam(k);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.get(kk))), asyncResultHandler);
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> {
      map.put(kk, HazelcastServerID.convertServerID(vv));
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
  public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> {
      map.put(kk, HazelcastServerID.convertServerID(vv), timeout, TimeUnit.MILLISECONDS);
      fut.complete();
    }, completionHandler);
  }

  @Override
  public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> resultHandler) {
    K kk = convertParam(k);
    V vv = convertParam(v);
    vertx.executeBlocking(fut -> fut.complete(convertReturn(map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv),
      timeout, TimeUnit.MILLISECONDS))), resultHandler);
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

  @SuppressWarnings("unchecked")
  private <T> T convertParam(T obj) {
    if (obj instanceof ClusterSerializable) {
      ClusterSerializable cobj = (ClusterSerializable)obj;
      return (T)(new DataSerializableHolder(cobj));
    } else {
      return obj;
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T convertReturn(Object obj) {
    if (obj instanceof DataSerializableHolder) {
      DataSerializableHolder cobj = (DataSerializableHolder)obj;
      return (T)cobj.clusterSerializable();
    } else {
      return (T)obj;
    }
  }

  private static final class DataSerializableHolder implements DataSerializable {

    private ClusterSerializable clusterSerializable;

    public DataSerializableHolder() {
    }

    private DataSerializableHolder(ClusterSerializable clusterSerializable) {
      this.clusterSerializable = clusterSerializable;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
      objectDataOutput.writeUTF(clusterSerializable.getClass().getName());
      Buffer buffer = Buffer.buffer();
      clusterSerializable.writeToBuffer(buffer);
      byte[] bytes = buffer.getBytes();
      objectDataOutput.writeInt(bytes.length);
      objectDataOutput.write(bytes);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
      String className = objectDataInput.readUTF();
      int length = objectDataInput.readInt();
      byte[] bytes = new byte[length];
      objectDataInput.readFully(bytes);
      Buffer buffer = Buffer.buffer(bytes);
      try {
        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
        clusterSerializable = (ClusterSerializable)clazz.newInstance();
        clusterSerializable.readFromBuffer(0, buffer);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to load class " + e.getMessage(), e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DataSerializableHolder)) return false;
      DataSerializableHolder that = (DataSerializableHolder) o;
      if (clusterSerializable != null ? !clusterSerializable.equals(that.clusterSerializable) : that.clusterSerializable != null) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return clusterSerializable != null ? clusterSerializable.hashCode() : 0;
    }

    public ClusterSerializable clusterSerializable() {
      return clusterSerializable;
    }
  }


}
