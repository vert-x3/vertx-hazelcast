package io.vertx.spi.cluster.hazelcast.impl.serializable;

import com.hazelcast.nio.serialization.DataSerializableFactory;

public class VertxDataSerializerHook implements com.hazelcast.internal.serialization.DataSerializerHook {
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
