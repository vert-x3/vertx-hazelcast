package io.vertx.spi.cluster.hazelcast.impl.serializable;

import java.nio.charset.StandardCharsets;

import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.impl.ClusterSerializable;

public class JsonDataSerializable extends BuiltinClusterDataSerializable {

	public JsonDataSerializable(){
		
	}
	
	public JsonDataSerializable(JsonObject obj){
		super(obj);
	}
	@Override
	public int getId() {
		return 1;
	}

	@Override
	protected ClusterSerializable newInstance(byte[] bytes) {
		return new JsonObject(new String(bytes,StandardCharsets.UTF_8));
	}

}
