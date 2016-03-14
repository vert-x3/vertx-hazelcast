package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.ExecutionCallback;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.convertReturn;

class HandlerCallBackAdapter<V> implements ExecutionCallback<V> {

    private final Handler<AsyncResult<V>> asyncResultHandler;

    public HandlerCallBackAdapter(Handler<AsyncResult<V>> asyncResultHandler) {
        this.asyncResultHandler = asyncResultHandler;
    }

    @Override
    public void onResponse(V v) {
        setResult(succeededFuture(convertReturn(v)));
    }

    @Override
    public void onFailure(Throwable throwable) {
        setResult(failedFuture(throwable));
    }

    protected void setResult(AsyncResult<V> object) {
        asyncResultHandler.handle(object);
    }
}
