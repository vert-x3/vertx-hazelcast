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

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.serialization.DataSerializableFactory;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.*;
import io.vertx.spi.cluster.hazelcast.impl.*;
import io.vertx.spi.cluster.hazelcast.spi.HazelcastObjectProvider;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * A cluster manager that uses Hazelcast
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager, MembershipListener, LifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  private static final String NODE_ID_ATTRIBUTE = "__vertx.nodeId";

  private VertxInternal vertx;

  private HazelcastInstance hazelcast;
  private HazelcastObjectProvider objectProvider;
  private volatile String nodeId;
  private NodeInfo nodeInfo;
  private SubsMapHelper subsMapHelper;
  private IMap<String, HazelcastNodeInfo> nodeInfoMap;
  private UUID membershipListenerId;
  private UUID lifecycleListenerId;
  private boolean customHazelcastCluster;
  private Set<String> nodeIds = new HashSet<>();

  private NodeListener nodeListener;
  private RegistrationListener registrationListener;
  private volatile boolean active;

  private Config conf;

  private ExecutorService lockReleaseExec;

  /**
   * Constructor - gets config from classpath
   */
  public HazelcastClusterManager() {
    ServiceLoader<HazelcastObjectProvider> loader = ServiceLoader.load(HazelcastObjectProvider.class);
    Iterator<HazelcastObjectProvider> it = loader.iterator();
    while (it.hasNext()) {
      objectProvider = it.next();
    }
    if (objectProvider == null) {
      objectProvider = new HazelcastObjectProviderImpl();
    }
  }

  /**
   * Constructor - config supplied
   *
   * @param conf Hazelcast config, not null
   */
  public HazelcastClusterManager(Config conf) {
    this();
    Objects.requireNonNull(conf, "The Hazelcast config cannot be null.");
    this.conf = conf;
  }

  public HazelcastClusterManager(HazelcastInstance instance) {
    this();
    Objects.requireNonNull(instance, "The Hazelcast instance cannot be null.");
    hazelcast = instance;
    customHazelcastCluster = true;
  }

  @Override
  public void init(Vertx vertx) {
    this.vertx = (VertxInternal) vertx;
  }

  @Override
  public void join(Promise<Void> promise) {
    vertx.<Void>executeBlocking(() -> {
      if (!active) {
        active = true;

        lockReleaseExec = Executors.newCachedThreadPool(r -> new Thread(r, "vertx-hazelcast-service-release-lock-thread"));

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

          nodeId = UUID.randomUUID().toString();
          conf.getMemberAttributeConfig().setAttribute(NODE_ID_ATTRIBUTE, nodeId);

          hazelcast = Hazelcast.newHazelcastInstance(conf);
        } else {
          nodeId = hazelcast.getCluster().getLocalMember().getAttribute(NODE_ID_ATTRIBUTE);
          if (nodeId == null) {
            throw new VertxException("Vert.x node id not defined in Hazelcast member attributes", true);
          }
        }


        Module hzMod = NetworkConfig.class.getModule();
        if (PlatformDependent.isOsx() && hzMod.isNamed()) {
          NetworkConfig cfg = hazelcast.getConfig().getNetworkConfig();
          if (cfg.getJoin().getMulticastConfig().isEnabled()) {
            throw new VertxException("Hazelcast detected on module path multicast join not supported on Mac");
          }
        }

        subsMapHelper = new SubsMapHelper(hazelcast, registrationListener);

        membershipListenerId = hazelcast.getCluster().addMembershipListener(this);
        lifecycleListenerId = hazelcast.getLifecycleService().addLifecycleListener(this);

        nodeInfoMap = hazelcast.getMap("__vertx.nodeInfo");

        objectProvider.onJoin(vertx, new ConversionUtils(), hazelcast, lockReleaseExec);

      }
      return null;
    }).onComplete(promise);
  }

  @Override
  public String getNodeId() {
    return nodeId;
  }

  @Override
  public List<String> getNodes() {
    List<String> list = new ArrayList<>();
    for (Member member : hazelcast.getCluster().getMembers()) {
      String nodeId = member.getAttribute(NODE_ID_ATTRIBUTE);
      if (nodeId != null) { // don't add data-only members
        list.add(nodeId);
      }
    }
    return list;
  }

  @Override
  public void registrationListener(RegistrationListener listener) {
    this.registrationListener = listener;
  }

  @Override
  public void nodeListener(NodeListener listener) {
    this.nodeListener = listener;
  }

  @Override
  public void setNodeInfo(NodeInfo nodeInfo, Promise<Void> promise) {
    synchronized (this) {
      this.nodeInfo = nodeInfo;
    }
    HazelcastNodeInfo value = wrapNodeInfo(nodeInfo);
    vertx.<Void>executeBlocking(() -> {
      nodeInfoMap.put(nodeId, value);
      return null;
    }, false).onComplete(promise);
  }

  private HazelcastNodeInfo wrapNodeInfo(NodeInfo nodeInfo) {
    return new HazelcastNodeInfo(nodeInfo);
  }

  @Override
  public synchronized NodeInfo getNodeInfo() {
    return nodeInfo;
  }

  @Override
  public void getNodeInfo(String nodeId, Promise<NodeInfo> promise) {
    vertx.executeBlocking(() -> {
      HazelcastNodeInfo value = nodeInfoMap.get(nodeId);
      if (value != null) {
        return value.unwrap();
      } else {
        throw new VertxException("Not a member of the cluster", true);
      }
    }, false).onComplete(promise);
  }

  @Override
  public <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise) {
    promise.complete(objectProvider.getAsyncMap(name));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    return objectProvider.getSyncMap(name);
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Promise<Lock> promise) {
    vertx.executeBlocking(() -> objectProvider.getLockWithTimeout(name, timeout), false)
      .onComplete(promise);
  }

  @Override
  public void getCounter(String name, Promise<Counter> promise) {
    promise.complete(objectProvider.createCounter(name));
  }

  @Override
  public void leave(Promise<Void> promise) {
    vertx.<Void>executeBlocking(() -> {
      // We need to synchronized on the cluster manager instance to avoid other call to happen while leaving the
      // cluster, typically, memberRemoved and memberAdded
      synchronized (HazelcastClusterManager.this) {
        if (active) {
          active = false;
          lockReleaseExec.shutdown();
          subsMapHelper.close();
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
        }
      }
      return null;
    }).onComplete(promise);
  }

  @Override
  public synchronized void memberAdded(MembershipEvent membershipEvent) {
    if (!active) {
      return;
    }
    Member member = membershipEvent.getMember();
    String nid = member.getAttribute(NODE_ID_ATTRIBUTE);
    try {
      if (nodeListener != null) {
        nodeIds.add(nid);
        nodeListener.nodeAdded(nid);
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
    String nid = member.getAttribute(NODE_ID_ATTRIBUTE);
    try {
      membersRemoved(Collections.singleton(nid));
    } catch (Throwable t) {
      log.error("Failed to handle memberRemoved", t);
    }
  }

  private synchronized void membersRemoved(Set<String> ids) {
    cleanSubs(ids);
    cleanNodeInfos(ids);
    nodeInfoMap.put(nodeId, wrapNodeInfo(getNodeInfo()));
    registrationListener.registrationsLost();
    republishOwnSubs();
    if (nodeListener != null) {
      nodeIds.removeAll(ids);
      ids.forEach(nodeListener::nodeLeft);
    }
  }

  private void cleanSubs(Set<String> ids) {
    subsMapHelper.removeAllForNodes(ids);
  }

  private void cleanNodeInfos(Set<String> ids) {
    ids.forEach(nodeInfoMap::remove);
  }

  private void republishOwnSubs() {
    vertx.executeBlocking(() -> {
      subsMapHelper.republishOwnSubs();
      return null;
    }, false);
  }

  @Override
  public synchronized void stateChanged(LifecycleEvent lifecycleEvent) {
    if (!active) {
      return;
    }
    // Safeguard to make sure members list is OK after a partition merge
    if (lifecycleEvent.getState() == LifecycleEvent.LifecycleState.MERGED) {
      final List<String> currentNodes = getNodes();
      Set<String> newNodes = new HashSet<>(currentNodes);
      newNodes.removeAll(nodeIds);
      Set<String> removedMembers = new HashSet<>(nodeIds);
      removedMembers.removeAll(currentNodes);
      if (nodeListener != null) {
        for (String nodeId : newNodes) {
          nodeListener.nodeAdded(nodeId);
        }
      }
      membersRemoved(removedMembers);
      nodeIds.retainAll(currentNodes);
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public void addRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    SubsOpSerializer serializer = SubsOpSerializer.get(vertx.getOrCreateContext());
    serializer.execute(subsMapHelper::put, address, registrationInfo, promise);
  }

  @Override
  public void removeRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
    SubsOpSerializer serializer = SubsOpSerializer.get(vertx.getOrCreateContext());
    serializer.execute(subsMapHelper::remove, address, registrationInfo, promise);
  }

  @Override
  public void getRegistrations(String address, Promise<List<RegistrationInfo>> promise) {
    vertx
      .executeBlocking(() -> subsMapHelper.get(address), false)
      .onComplete(promise);
  }

  @Override
  public String clusterHost() {
    String host;
    if (!customHazelcastCluster && (host = System.getProperty("hazelcast.local.localAddress")) != null) {
      return host;
    }
    if (!customHazelcastCluster && conf.getNetworkConfig().getPublicAddress() == null) {
      return hazelcast.getCluster().getLocalMember().getAddress().getHost();
    }
    return null;
  }

  @Override
  public String clusterPublicHost() {
    String host;
    if (!customHazelcastCluster && (host = System.getProperty("hazelcast.local.publicAddress")) != null) {
      return host;
    }
    if (!customHazelcastCluster && (host = conf.getNetworkConfig().getPublicAddress()) != null) {
      return host;
    }
    return null;
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
}
