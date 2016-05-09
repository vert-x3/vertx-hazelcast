package io.vertx.spi.cluster.hazelcast.impl.serializable;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.impl.ClusterSerializable;

public class BufferDataSerializable extends BuiltinClusterDataSerializable {
	
	public BufferDataSerializable(Buffer buff){
		super(buff);
	}
	public BufferDataSerializable(){
		
	}

	@Override
	public int getId() {
		return 0;
	}

	@Override
	protected ClusterSerializable newInstance(byte[] bytes) {
		return Buffer.buffer(bytes);
	}

}
