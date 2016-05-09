package io.vertx.spi.cluster.hazelcast.impl.serializable;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class VertxDataSerializableFactory implements DataSerializableFactory {
	@Override
	public IdentifiedDataSerializable create(int id) {
		switch (id) {
		case 0:
			return new BufferDataSerializable();
		case 1:
			return new JsonDataSerializable();
		default:
			return new JsonArrayDataSerializable();
		}
	}

}
