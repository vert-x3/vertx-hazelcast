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
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ExtendedClusterManager;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastAsyncMap;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastAsyncMultiMap;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastInternalAsyncCounter;
import io.vertx.spi.cluster.hazelcast.impl.HazelcastInternalAsyncMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

/**
 * A cluster manager that uses Hazelcast
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ExtendedClusterManager, MembershipListener, LifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  private static final String LOCK_SEMAPHORE_PREFIX = "__vertx.";

  // Hazelcast config file
  private static final String DEFAULT_CONFIG_FILE = "default-cluster.xml";
  private static final String CONFIG_FILE = "cluster.xml";

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
  private Set<Member> members = new HashSet<>();
  // Guarded by this
  private Set<HazelcastAsyncMultiMap> multimaps = Collections.newSetFromMap(new WeakHashMap<>(1));

  private NodeListener nodeListener;
  private volatile boolean active;

  private Config conf;

  /**
   * Constructor - gets config from classpath
   */
  public HazelcastClusterManager() {
    // We have our own shutdown hook and need to ensure ours runs before Hazelcast is shutdown
    System.setProperty("hazelcast.shutdownhook.enabled", "false");
  }

  /**
   * Constructor - config supplied
   *
   * @param conf
   */
  public HazelcastClusterManager(Config conf) {
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

        // The hazelcast instance has been passed using the constructor.
        if (customHazelcastCluster) {
          nodeID = hazelcast.getLocalEndpoint().getUuid();
          membershipListenerId = hazelcast.getCluster().addMembershipListener(this);
          lifecycleListenerId = hazelcast.getLifecycleService().addLifecycleListener(this);
          fut.complete();
          return;
        }

        if (conf == null) {
          conf = loadConfig();
          if (conf == null) {
            log.warn("Cannot find cluster configuration on 'vertx.hazelcast.config' system property, on the classpath, " +
              "or specified programmatically. Using default hazelcast configuration");
          }
        }
        hazelcast = Hazelcast.newHazelcastInstance(conf);
        nodeID = hazelcast.getLocalEndpoint().getUuid();
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
    Set<Member> members = hazelcast.getCluster().getMembers();
    List<String> lMembers = new ArrayList<>();
    for (Member member : members) {
      lMembers.add(member.getUuid());
    }
    return lMembers;
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
    ContextImpl context = (ContextImpl) vertx.getOrCreateContext();
    // Ordered on the internal blocking executor
    context.executeBlocking(() -> {
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
        return new HazelcastLock(iSemaphore);
      } else {
        throw new VertxException("Timed out waiting to get lock " + name);
      }
    }, resultHandler);
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
    try {
      multimaps.forEach(HazelcastAsyncMultiMap::clearCache);
      if (nodeListener != null) {
        Member member = membershipEvent.getMember();
        members.add(member);
        nodeListener.nodeAdded(member.getUuid());
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
    try {
      multimaps.forEach(HazelcastAsyncMultiMap::clearCache);
      if (nodeListener != null) {
        Member member = membershipEvent.getMember();
        members.remove(member);
        nodeListener.nodeLeft(member.getUuid());
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
      final Set<Member> currentMembers = hazelcast.getCluster().getMembers();
      Set<Member> newMembers = new HashSet<>(currentMembers);
      newMembers.removeAll(members);
      Set<Member> removedMembers = new HashSet<>(members);
      removedMembers.removeAll(currentMembers);
      for(Member m : newMembers) {
        nodeListener.nodeAdded(m.getUuid());
      }
      for(Member m : removedMembers) {
        nodeListener.nodeLeft(m.getUuid());
      }
      members.retainAll(currentMembers);
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
  }

  private InputStream getConfigStream() {
    InputStream is = getConfigStreamFromSystemProperty();
    if (is == null) {
      is = getConfigStreamFromClasspath(CONFIG_FILE, DEFAULT_CONFIG_FILE);
    }
    return is;
  }

  private InputStream getConfigStreamFromSystemProperty() {
    String configProp = System.getProperty("vertx.hazelcast.config");
    InputStream is = null;
    if (configProp != null) {
      if (configProp.startsWith("classpath:")) {
        return getConfigStreamFromClasspath(configProp.substring("classpath:".length()), CONFIG_FILE);
      }
      File cfgFile = new File(configProp);
      if (cfgFile.exists()) {
        try {
          is = new FileInputStream(cfgFile);
        } catch (FileNotFoundException ex) {
          log.warn("Failed to open file '" + configProp + "' defined in 'vertx.hazelcast.config'. Continuing " +
            "classpath search for " + CONFIG_FILE);
        }
      }
    }
    return is;
  }

  private InputStream getConfigStreamFromClasspath(String configFile, String defaultConfig) {
    InputStream is = null;
    ClassLoader ctxClsLoader = Thread.currentThread().getContextClassLoader();
    if (ctxClsLoader != null) {
      is = ctxClsLoader.getResourceAsStream(configFile);
    }
    if (is == null) {
      is = getClass().getClassLoader().getResourceAsStream(configFile);
      if (is == null) {
        is = getClass().getClassLoader().getResourceAsStream(defaultConfig);
      }
    }
    return is;
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
    Config cfg = null;
    try (InputStream is = getConfigStream();
         InputStream bis = new BufferedInputStream(is)) {
      cfg = new XmlConfigBuilder(bis).build();
    } catch (IOException ex) {
      log.error("Failed to read config", ex);
    }
    return cfg;
  }

  public void beforeLeave() {
    vertx.executeBlocking(fut -> {
      if (isActive()) {
        if (!customHazelcastCluster && hazelcast.getLifecycleService().isRunning()) {
          ILock lock = hazelcast.getLock("vertx.shutdownlock");
          try {
            lock.tryLock(30, TimeUnit.SECONDS);
          } catch (Exception ignore) {
          }
          // The lock should be automatically released when the node is shutdown
        }
      }
    }, null);
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

    private HazelcastLock(ISemaphore semaphore) {
      this.semaphore = semaphore;
    }

    @Override
    public void release() {
      vertx.executeBlocking(future -> {
        semaphore.release();
        future.complete();
      }, false, null);
    }
  }

}
