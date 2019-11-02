package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.ExecutionCallback;
import io.vertx.core.Promise;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

class HandlerCallBackAdapter<V> implements ExecutionCallback<V> {

    private final Promise<V> promise;

    public HandlerCallBackAdapter(Promise<V> promise) {
        this.promise = promise;
    }

    @Override
    public void onResponse(V v) {
        promise.complete(v);
    }

    @Override
    public void onFailure(Throwable throwable) {
        promise.fail(throwable);
    }
}
