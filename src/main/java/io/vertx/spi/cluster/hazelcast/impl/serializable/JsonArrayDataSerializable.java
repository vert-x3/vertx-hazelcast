package io.vertx.spi.cluster.hazelcast.impl.serializable;

import java.nio.charset.StandardCharsets;

import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.impl.ClusterSerializable;

public class JsonArrayDataSerializable extends BuiltinClusterDataSerializable {
	public JsonArrayDataSerializable(){
		
	}
	public JsonArrayDataSerializable(JsonArray obj){
		super(obj);
	}
	@Override
	public int getId() {
		return 2;
	}

	@Override
	protected ClusterSerializable newInstance(byte[] bytes) {
		return new JsonArray(new String(bytes,StandardCharsets.UTF_8));
	}

}
