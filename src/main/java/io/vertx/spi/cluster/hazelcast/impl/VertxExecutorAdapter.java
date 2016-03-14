package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.Context;

import java.util.concurrent.Executor;

final class VertxExecutorAdapter implements Executor {

    private final Context context;

    VertxExecutorAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void execute(Runnable command) {
        context.runOnContext(aVoid -> command.run());
    }
}
