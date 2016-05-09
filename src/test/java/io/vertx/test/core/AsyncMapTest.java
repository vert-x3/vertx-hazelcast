package io.vertx.test.core;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
@RunWith(VertxUnitRunner.class)
public class AsyncMapTest {

	Vertx vertx;

	public void init(Handler<Vertx> handler) {
		VertxOptions opts = new VertxOptions();
		opts.setClustered(true);
		Vertx.clusteredVertx(opts, rs -> {
			handler.handle(vertx = rs.result());
		});
	}

	@After
	public void after(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}

	@Test
	public void testAsyncMap(TestContext context) {
		Async async = context.async();
		init(vertx1 -> {
			init(vert2 -> {
				vertx1.sharedData().<String, Buffer> getClusterWideMap("test-map", rs -> {
					Assert.assertTrue(rs.succeeded());
					if (rs.succeeded()) {
						AsyncMap<String,Buffer> map = rs.result();
						map.put("key",Buffer.buffer().appendString("ok"),put->{
							Assert.assertTrue(put.succeeded());
							map.get("key", get-> {
								if(get.failed()){
									get.cause().printStackTrace();
								}
								Assert.assertTrue(get.succeeded());						
								async.complete();
							});
						});						
					} else {
						async.complete();
					}
				});
			});
		});
	}
	//@Test
	public void testAsyncMap2(TestContext context) {
		Async async = context.async();
		init(vertx1 -> {
			init(vert2 -> {
				vertx1.sharedData().<String, Buffer> getClusterWideMap("test-map", rs -> {
					Assert.assertTrue(rs.succeeded());
					if (rs.succeeded()) {
						AsyncMap<String,Buffer> map = rs.result();
						map.put("key",Buffer.buffer().appendString("ok"),put->{
							Assert.assertTrue(put.succeeded());
							map.get("key", get-> {								
								get.cause().printStackTrace();
								Assert.assertTrue(get.succeeded());						
								async.complete();
							});
						});						
					} else {
						async.complete();
					}
				});
			});
		});
	}
}
