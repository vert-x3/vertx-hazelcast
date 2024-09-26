package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.shareddata.ClusterSerializable;

public class ConversionUtils {

  @SuppressWarnings("unchecked")
  public <T> T convertParam(T obj) {
    if (obj instanceof ClusterSerializable) {
      return (T) (new DataSerializableHolder((ClusterSerializable) obj));
    } else {
      return obj;
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T convertReturn(Object obj) {
    if (obj instanceof DataSerializableHolder) {
      DataSerializableHolder cobj = (DataSerializableHolder) obj;
      return (T) cobj.clusterSerializable();
    } else {
      return (T) obj;
    }
  }
}
