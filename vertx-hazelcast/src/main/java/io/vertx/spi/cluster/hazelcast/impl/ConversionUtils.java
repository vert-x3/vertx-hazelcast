package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.ClusterSerializable;

public class ConversionUtils {

  private static class JsonObjectClusterSerializable implements ClusterSerializable {
    private JsonObject delegate;
    JsonObjectClusterSerializable(JsonObject jsonObject) {
      this.delegate = jsonObject;
    }
    public JsonObjectClusterSerializable() {
      this.delegate = new JsonObject();
    }
    @Override
    public void writeToBuffer(Buffer buffer) {
      delegate.writeToBuffer(buffer);
    }
    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
      return delegate.readFromBuffer(pos, buffer);
    }
  }

  private static class JsonArrayClusterSerializable implements ClusterSerializable {
    private JsonArray delegate;
    JsonArrayClusterSerializable(JsonArray jsonObject) {
      this.delegate = jsonObject;
    }
    JsonArrayClusterSerializable() {
      this.delegate = new JsonArray();
    }
    @Override
    public void writeToBuffer(Buffer buffer) {
      delegate.writeToBuffer(buffer);
    }
    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
      return delegate.readFromBuffer(pos, buffer);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T convertParam(T obj) {
    if (obj instanceof JsonObject) {
      obj = (T)new JsonObjectClusterSerializable((JsonObject)obj);
    } else if (obj instanceof JsonArray) {
      obj = (T)new JsonArrayClusterSerializable((JsonArray)obj);
    }
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
      ClusterSerializable ret = cobj.clusterSerializable();
      if (ret instanceof JsonObjectClusterSerializable) {
        return (T)((JsonObjectClusterSerializable)ret).delegate;
      } else if (ret instanceof JsonArrayClusterSerializable) {
        return (T)((JsonArrayClusterSerializable)ret).delegate;
      } else {
        return (T)ret;
      }
    } else {
      return (T) obj;
    }
  }
}
