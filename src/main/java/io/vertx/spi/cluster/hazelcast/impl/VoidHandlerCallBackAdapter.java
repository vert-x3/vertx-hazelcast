package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;


final class VoidHandlerCallBackAdapter<T> extends HandlerCallBackAdapter<T> {
    public VoidHandlerCallBackAdapter(Handler<AsyncResult<Void>> asyncResultHandler) {
        super((Handler)asyncResultHandler);
    }

    @Override
    public void onResponse(T v) {
        setResult(Future.succeededFuture());
    }
}
