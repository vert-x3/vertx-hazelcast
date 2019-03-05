/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.spi.cluster.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastAsyncMap;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastAsyncMultiMap;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastInternalAsyncCounter;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastInternalAsyncMap;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A cluster manager that uses Hazelcast
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager, MembershipListener, LifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  private static final String LOCK_SEMAPHORE_PREFIX = "__vertx.";
  private static final String NODE_ID_ATTRIBUTE = "__vertx.nodeId";

  /**
   * Set "vertx.hazelcast.async-api" system property to {@code true} to use the
   * (currently) non-public Hazelcast async API. When {@code true}, the {@link AsyncMap} implementation
   * will be backed by {@link HazelcastInternalAsyncMap} and the {@link Counter} is supplied by
   * {@link HazelcastInternalAsyncCounter}, otherwise default to {@link HazelcastAsyncMap}
   * and {@link HazelcastCounter}.
   */
  private static final String OPTION_USE_HZ_ASYNC_API = "vertx.hazelcast.async-api";

  private static final boolean USE_HZ_ASYNC_API = Boolean.getBoolean(OPTION_USE_HZ_ASYNC_API);

  private Vertx vertx;

  private HazelcastInstance hazelcast;
  private String nodeID;
  private String membershipListenerId;
  private String lifecycleListenerId;
  private boolean customHazelcastCluster;
  private Set<String> nodeIds = new HashSet<>();
  // Guarded by this
  private Set<HazelcastAsyncMultiMap> multimaps = Collections.newSetFromMap(new WeakHashMap<>(1));

  private NodeListener nodeListener;
  private volatile boolean active;

  private Config conf;

  /**
   * Constructor - gets config from classpath
   */
  public HazelcastClusterManager() {
  }

  /**
   * Constructor - config supplied
   *
   * @param conf Hazelcast config, not null
   */
  public HazelcastClusterManager(Config conf) {
    Objects.requireNonNull(conf, "The Hazelcast config cannot be null.");
    this.conf = conf;
  }

  public HazelcastClusterManager(HazelcastInstance instance) {
    Objects.requireNonNull(instance, "The Hazelcast instance cannot be null.");
    hazelcast = instance;
    customHazelcastCluster = true;
  }

  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  public synchronized void join(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(fut -> {
      if (!active) {
        active = true;

        // The hazelcast instance has not been passed using the constructor.
        if (!customHazelcastCluster) {
          if (conf == null) {
            conf = loadConfig();
            if (conf == null) {
              log.warn("Cannot find cluster configuration on 'vertx.hazelcast.config' system property, on the classpath, " +
                "or specified programmatically. Using default hazelcast configuration");
              conf = new Config();
            }
          }

          // We have our own shutdown hook and need to ensure ours runs before Hazelcast is shutdown
          conf.setProperty("hazelcast.shutdownhook.enabled", "false");

          hazelcast = Hazelcast.newHazelcastInstance(conf);
        }

        Member localMember = hazelcast.getCluster().getLocalMember();
        nodeID = localMember.getUuid();
        localMember.setStringAttribute(NODE_ID_ATTRIBUTE, nodeID);
        membershipListenerId = hazelcast.getCluster().addMembershipListener(this);
        lifecycleListenerId = hazelcast.getLifecycleService().addLifecycleListener(this);
        fut.complete();
      }
    }, resultHandler);
  }

  /**
   * Every eventbus handler has an ID. SubsMap (subscriber map) is a MultiMap which
   * maps handler-IDs with server-IDs and thus allows the eventbus to determine where
   * to send messages.
   *
   * @param name          A unique name by which the the MultiMap can be identified within the cluster.
   *                      See the cluster config file (e.g. cluster.xml in case of HazelcastClusterManager) for
   *                      additional MultiMap config parameters.
   * @param resultHandler handler receiving the multimap
   */
  @Override
  public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler) {
    vertx.executeBlocking(fut -> {
      com.hazelcast.core.MultiMap<K, V> multiMap = hazelcast.getMultiMap(name);
      HazelcastAsyncMultiMap<K, V> asyncMultiMap = new HazelcastAsyncMultiMap<>(vertx, multiMap);
      synchronized (this) {
        multimaps.add(asyncMultiMap);
      }
      fut.complete(asyncMultiMap);
    }, resultHandler);
  }

  @Override
  public String getNodeID() {
    return nodeID;
  }

  @Override
  public List<String> getNodes() {
    List<String> list = new ArrayList<>();
    for (Member member : hazelcast.getCluster().getMembers()) {
      String nodeIdAttribute = member.getStringAttribute(NODE_ID_ATTRIBUTE);
      list.add(nodeIdAttribute != null ? nodeIdAttribute : member.getUuid());
    }
    return list;
  }

  @Override
  public void nodeListener(NodeListener listener) {
    this.nodeListener = listener;
  }

  @Override
  public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
    vertx.executeBlocking(fut -> {
      IMap<K, V> map = hazelcast.getMap(name);
      fut.complete(USE_HZ_ASYNC_API ? new HazelcastInternalAsyncMap<>(vertx, map) : new HazelcastAsyncMap<>(vertx, map));
    }, resultHandler);
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    IMap<K, V> map = hazelcast.getMap(name);
    return map;
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    vertx.executeBlocking(fut -> {
      ISemaphore iSemaphore = hazelcast.getSemaphore(LOCK_SEMAPHORE_PREFIX + name);
      boolean locked = false;
      long remaining = timeout;
      do {
        long start = System.nanoTime();
        try {
          locked = iSemaphore.tryAcquire(remaining, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          // OK continue
        }
        remaining = remaining - MILLISECONDS.convert(System.nanoTime() - start, NANOSECONDS);
      } while (!locked && remaining > 0);
      if (locked) {
        fut.complete(new HazelcastLock(iSemaphore));
      } else {
        throw new VertxException("Timed out waiting to get lock " + name);
      }
    }, false, resultHandler);
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
    vertx.executeBlocking(fut ->
        fut.complete(
          USE_HZ_ASYNC_API ?
            new HazelcastInternalAsyncCounter(vertx, hazelcast.getAtomicLong(name)) :
            new HazelcastCounter(hazelcast.getAtomicLong(name))
        )
      , resultHandler);
  }

  public void leave(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(fut -> {
      // We need to synchronized on the cluster manager instance to avoid other call to happen while leaving the
      // cluster, typically, memberRemoved and memberAdded
      synchronized (HazelcastClusterManager.this) {
        if (active) {
          try {
            active = false;
            boolean left = hazelcast.getCluster().removeMembershipListener(membershipListenerId);
            if (!left) {
              log.warn("No membership listener");
            }
            hazelcast.getLifecycleService().removeLifecycleListener(lifecycleListenerId);

            // Do not shutdown the cluster if we are not the owner.
            while (!customHazelcastCluster && hazelcast.getLifecycleService().isRunning()) {
              try {
                // This can sometimes throw java.util.concurrent.RejectedExecutionException so we retry.
                hazelcast.getLifecycleService().shutdown();
              } catch (RejectedExecutionException ignore) {
                log.debug("Rejected execution of the shutdown operation, retrying");
              }
              try {
                Thread.sleep(1);
              } catch (InterruptedException t) {
                // Manage the interruption in another handler.
                Thread.currentThread().interrupt();
              }
            }

            if (customHazelcastCluster) {
              hazelcast.getCluster().getLocalMember().removeAttribute(NODE_ID_ATTRIBUTE);
            }

          } catch (Throwable t) {
            fut.fail(t);
          }
        }
      }
      fut.complete();
    }, resultHandler);
  }

  @Override
  public synchronized void memberAdded(MembershipEvent membershipEvent) {
    if (!active) {
      return;
    }
    Member member = membershipEvent.getMember();
    String memberNodeId = member.getStringAttribute(NODE_ID_ATTRIBUTE);
    if (memberNodeId == null) {
      memberNodeId = member.getUuid();
    }
    try {
      multimaps.forEach(HazelcastAsyncMultiMap::clearCache);
      if (nodeListener != null) {
        nodeIds.add(memberNodeId);
        nodeListener.nodeAdded(memberNodeId);
      }
    } catch (Throwable t) {
      log.error("Failed to handle memberAdded", t);
    }
  }

  @Override
  public synchronized void memberRemoved(MembershipEvent membershipEvent) {
    if (!active) {
      return;
    }
    Member member = membershipEvent.getMember();
    String memberNodeId = member.getStringAttribute(NODE_ID_ATTRIBUTE);
    if (memberNodeId == null) {
      memberNodeId = member.getUuid();
    }
    try {
      multimaps.forEach(HazelcastAsyncMultiMap::clearCache);
      if (nodeListener != null) {
        nodeIds.remove(memberNodeId);
        nodeListener.nodeLeft(memberNodeId);
      }
    } catch (Throwable t) {
      log.error("Failed to handle memberRemoved", t);
    }
  }

  @Override
  public synchronized void stateChanged(LifecycleEvent lifecycleEvent) {
    if (!active) {
      return;
    }
    multimaps.forEach(HazelcastAsyncMultiMap::clearCache);
    // Safeguard to make sure members list is OK after a partition merge
    if(lifecycleEvent.getState() == LifecycleEvent.LifecycleState.MERGED) {
      final List<String> currentNodes = getNodes();
      Set<String> newNodes = new HashSet<>(currentNodes);
      newNodes.removeAll(nodeIds);
      Set<String> removedMembers = new HashSet<>(nodeIds);
      removedMembers.removeAll(currentNodes);
      for (String nodeId : newNodes) {
        nodeListener.nodeAdded(nodeId);
      }
      for (String nodeId : removedMembers) {
        nodeListener.nodeLeft(nodeId);
      }
      nodeIds.retainAll(currentNodes);
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
  }

  /**
   * Get the Hazelcast config.
   *
   * @return a config object
   */
  public Config getConfig() {
    return conf;
  }

  /**
   * Set the Hazelcast config.
   *
   * @param config a config object
   */
  public void setConfig(Config config) {
    this.conf = config;
  }

  /**
   * Load Hazelcast config XML and transform it into a {@link Config} object.
   * The content is read from:
   * <ol>
   * <li>the location denoted by the {@code vertx.hazelcast.config} sysprop, if present, or</li>
   * <li>the {@code cluster.xml} file on the classpath, if present, or</li>
   * <li>the default config file</li>
   * </ol>
   * <p>
   * The cluster manager uses this method to load the config when the node joins the cluster, if no config was provided upon creation.
   * </p>
   * <p>
   * You may use this method to get a base config and customize it before the node joins the cluster.
   * In this case, don't forget to invoke {@link #setConfig(Config)} after you applied your changes.
   * </p>
   *
   * @return a config object
   */
  public Config loadConfig() {
    return ConfigUtil.loadConfig();
  }

  public HazelcastInstance getHazelcastInstance() {
    return hazelcast;
  }

  private class HazelcastCounter implements Counter {
    private IAtomicLong atomicLong;


    private HazelcastCounter(IAtomicLong atomicLong) {
      this.atomicLong = atomicLong;
    }

    @Override
    public void get(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.get()), resultHandler);
    }

    @Override
    public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.incrementAndGet()), resultHandler);
    }

    @Override
    public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.getAndIncrement()), resultHandler);
    }

    @Override
    public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.decrementAndGet()), resultHandler);
    }

    @Override
    public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.addAndGet(value)), resultHandler);
    }

    @Override
    public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.getAndAdd(value)), resultHandler);
    }

    @Override
    public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(fut -> fut.complete(atomicLong.compareAndSet(expected, value)), resultHandler);
    }
  }

  private class HazelcastLock implements Lock {

    private final ISemaphore semaphore;
    private final AtomicBoolean released = new AtomicBoolean();

    private HazelcastLock(ISemaphore semaphore) {
      this.semaphore = semaphore;
    }

    @Override
    public void release() {
      if (released.compareAndSet(false, true)) {
        vertx.executeBlocking(future -> {
          semaphore.release();
          future.complete();
        }, false, null);
      }
    }
  }
}
