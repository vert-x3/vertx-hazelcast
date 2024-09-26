package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.ClusterSerializable;

import java.io.IOException;

@SuppressWarnings("unchecked")
public class DataSerializableHolder implements DataSerializable {

  private ClusterSerializable clusterSerializable;

  public DataSerializableHolder() {
  }

  DataSerializableHolder(ClusterSerializable clusterSerializable) {
    this.clusterSerializable = clusterSerializable;
  }

  @Override
  public final void writeData(ObjectDataOutput objectDataOutput) throws IOException {
    String fqn;
    if (clusterSerializable instanceof Buffer) {
      fqn = "io.vertx.core.buffer.Buffer";
    } else {
      fqn = clusterSerializable.getClass().getName();
    }
    objectDataOutput.writeUTF(fqn);
    Buffer buffer = Buffer.buffer();
    clusterSerializable.writeToBuffer(buffer);
    byte[] bytes = buffer.getBytes();
    objectDataOutput.writeInt(bytes.length);
    objectDataOutput.write(bytes);
  }

  @Override
  public final void readData(ObjectDataInput objectDataInput) throws IOException {
    String className = objectDataInput.readUTF();
    int length = objectDataInput.readInt();
    byte[] bytes = new byte[length];
    objectDataInput.readFully(bytes);
    try {
      switch (className) {
        case "io.vertx.core.buffer.Buffer":
          clusterSerializable = Buffer.buffer();
          break;
        default:
          Class<?> clazz = loadClass(className);
          clusterSerializable = (ClusterSerializable) clazz.newInstance();
          break;
      }
      clusterSerializable.readFromBuffer(0, Buffer.buffer(bytes));
    } catch (Exception e) {
      throw new IOException("Failed to load class " + className, e);
    }
  }

  private Class<?> loadClass(String className) throws ClassNotFoundException {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    try {
      if (tccl != null) {
        return tccl.loadClass(className);
      }
    } catch (ClassNotFoundException ignored) {
    }
    return ConversionUtils.class.getClassLoader().loadClass(className);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DataSerializableHolder)) {
      return false;
    }
    DataSerializableHolder that = (DataSerializableHolder) o;
    return clusterSerializable != null ? clusterSerializable.equals(that.clusterSerializable) : that.clusterSerializable == null;
  }

  @Override
  public final int hashCode() {
    return clusterSerializable != null ? clusterSerializable.hashCode() : 0;
  }

  public ClusterSerializable clusterSerializable() {
    return clusterSerializable;
  }
}
