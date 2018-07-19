package io.vertx.spi.cluster.hazelcast;

import com.hazelcast.core.PartitionService;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.healthchecks.Status;

import java.util.Objects;

/**
 * A helper to create Vert.x cluster {@link io.vertx.ext.healthchecks.HealthChecks} procedures.
 */
@VertxGen
public interface ClusterHealthCheck {

  /**
   * Creates a ready-to-use Vert.x cluster {@link io.vertx.ext.healthchecks.HealthChecks} procedure.
   *
   * @param vertx the instance of Vert.x, must not be {@code null}
   * @return a Vert.x cluster {@link io.vertx.ext.healthchecks.HealthChecks} procedure
   */
  static Handler<Future<Status>> createProcedure(Vertx vertx) {
    Objects.requireNonNull(vertx);
    return future -> {
      VertxInternal vertxInternal = (VertxInternal) vertx;
      HazelcastClusterManager clusterManager = (HazelcastClusterManager) vertxInternal.getClusterManager();
      PartitionService partitionService = clusterManager.getHazelcastInstance().getPartitionService();
      future.complete(new Status().setOk(partitionService.isClusterSafe()));
    };
  }
}
