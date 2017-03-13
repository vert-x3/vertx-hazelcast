package io.vertx.spi.cluster.hazelcast.impl.serializable;

import java.io.IOException;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.impl.ClusterSerializable;

public abstract class BuiltinClusterDataSerializable implements IdentifiedDataSerializable {
	
	public ClusterSerializable clusterSerializable;
	
	public BuiltinClusterDataSerializable(ClusterSerializable clusterSerializable){
		this.clusterSerializable = clusterSerializable;
	}
	public BuiltinClusterDataSerializable(){
		
	}
	@Override
	public int getFactoryId() {
		return VertxDataSerializerHook.FACTORY_ID;
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
    int length = objectDataInput.readInt();
    byte[] bytes = new byte[length];
    objectDataInput.readFully(bytes);
    clusterSerializable = newInstance(bytes);
	}
	protected abstract ClusterSerializable newInstance(byte[] bytes);

}
