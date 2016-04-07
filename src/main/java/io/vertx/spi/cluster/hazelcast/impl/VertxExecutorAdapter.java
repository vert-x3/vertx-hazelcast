package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.Context;

import java.util.concurrent.Executor;

final class VertxExecutorAdapter implements Executor {

    private static final String CONTEXT_KEY_HZ_VERTX_EXECUTOR_ADAPTER = "io.vertx.spi.cluster.hazelcast.impl.VertxExecutorAdapter";
    private final Context context;

    private VertxExecutorAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void execute(Runnable command) {
        context.runOnContext(aVoid -> command.run());
    }

    public static VertxExecutorAdapter getOrCreate(Context context) {
        // Context.contextData() is a ConcurrentHashMap so we are not doing any external synchronisation here
        VertxExecutorAdapter vertxExecutorAdapter = context.get(CONTEXT_KEY_HZ_VERTX_EXECUTOR_ADAPTER);

        if (vertxExecutorAdapter != null) {
            return vertxExecutorAdapter;
        }
        else {
            vertxExecutorAdapter = new VertxExecutorAdapter(context);
            context.put(CONTEXT_KEY_HZ_VERTX_EXECUTOR_ADAPTER, vertxExecutorAdapter);
            return vertxExecutorAdapter;
        }
    }
}
