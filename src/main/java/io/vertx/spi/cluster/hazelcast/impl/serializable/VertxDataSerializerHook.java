package io.vertx.spi.cluster.hazelcast.impl.serializable;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.DataSerializerHook;

public class VertxDataSerializerHook implements DataSerializerHook {
	public final static int FACTORY_ID = 2906;
	@Override
	public int getFactoryId() {
		return FACTORY_ID;
	}

	@Override
	public DataSerializableFactory createFactory() {
		return new VertxDataSerializableFactory();
	}

}
