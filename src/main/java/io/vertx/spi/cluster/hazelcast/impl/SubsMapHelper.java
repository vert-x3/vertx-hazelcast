/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapEvent;
import com.hazelcast.multimap.MultiMap;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Thomas Segismont
 */
public class SubsMapHelper implements EntryListener<String, HazelcastRegistrationInfo> {

  private static final Logger log = LoggerFactory.getLogger(SubsMapHelper.class);

  private final Throttling throttling;
  private final MultiMap<String, HazelcastRegistrationInfo> map;
  private final NodeSelector nodeSelector;
  private final UUID listenerId;

  private final ConcurrentMap<String, Set<RegistrationInfo>> ownSubs = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Set<RegistrationInfo>> localSubs = new ConcurrentHashMap<>();
  private final ReadWriteLock republishLock = new ReentrantReadWriteLock();

  public SubsMapHelper(HazelcastInstance hazelcast, NodeSelector nodeSelector) {
    throttling = new Throttling(this::getAndUpdate);
    map = hazelcast.getMultiMap("__vertx.subs");
    this.nodeSelector = nodeSelector;
    listenerId = map.addEntryListener(this, false);
  }

  public List<RegistrationInfo> get(String address) {
    Lock readLock = republishLock.readLock();
    readLock.lock();
    try {
      List<RegistrationInfo> list;
      int size;
      Collection<HazelcastRegistrationInfo> remote = map.get(address);
      size = remote.size();
      Set<RegistrationInfo> local = localSubs.get(address);
      if (local != null) {
        synchronized (local) {
          size += local.size();
          if (size == 0) {
            return Collections.emptyList();
          }
          list = new ArrayList<>(size);
          list.addAll(local);
        }
      } else if (size == 0) {
        return Collections.emptyList();
      } else {
        list = new ArrayList<>(size);
      }
      for (HazelcastRegistrationInfo hazelcastRegistrationInfo : remote) {
        RegistrationInfo unwrap = hazelcastRegistrationInfo.unwrap();
        list.add(unwrap);
      }
      return list;
    } finally {
      readLock.unlock();
    }
  }

  public void put(String address, RegistrationInfo registrationInfo) {
    Lock readLock = republishLock.readLock();
    readLock.lock();
    try {
      if (registrationInfo.localOnly()) {
        localSubs.compute(address, (add, curr) -> addToSet(registrationInfo, curr));
        fireRegistrationUpdateEvent(address);
      } else {
        ownSubs.compute(address, (add, curr) -> addToSet(registrationInfo, curr));
        map.put(address, new HazelcastRegistrationInfo(registrationInfo));
      }
    } finally {
      readLock.unlock();
    }
  }

  private Set<RegistrationInfo> addToSet(RegistrationInfo registrationInfo, Set<RegistrationInfo> curr) {
    Set<RegistrationInfo> res = curr != null ? curr : Collections.synchronizedSet(new LinkedHashSet<>());
    res.add(registrationInfo);
    return res;
  }

  public void remove(String address, RegistrationInfo registrationInfo) {
    Lock readLock = republishLock.readLock();
    readLock.lock();
    try {
      if (registrationInfo.localOnly()) {
        localSubs.computeIfPresent(address, (add, curr) -> removeFromSet(registrationInfo, curr));
        fireRegistrationUpdateEvent(address);
      } else {
        ownSubs.computeIfPresent(address, (add, curr) -> removeFromSet(registrationInfo, curr));
        map.remove(address, new HazelcastRegistrationInfo(registrationInfo));
      }
    } finally {
      readLock.unlock();
    }
  }

  private Set<RegistrationInfo> removeFromSet(RegistrationInfo registrationInfo, Set<RegistrationInfo> curr) {
    curr.remove(registrationInfo);
    return curr.isEmpty() ? null : curr;
  }

  public void removeAllForNodes(Set<String> nodeIds) {
    for (Map.Entry<String, HazelcastRegistrationInfo> entry : map.entrySet()) {
      HazelcastRegistrationInfo registrationInfo = entry.getValue();
      if (nodeIds.contains(registrationInfo.unwrap().nodeId())) {
        map.remove(entry.getKey(), registrationInfo);
      }
    }
  }

  public void republishOwnSubs() {
    Lock writeLock = republishLock.writeLock();
    writeLock.lock();
    try {
      for (Map.Entry<String, Set<RegistrationInfo>> entry : ownSubs.entrySet()) {
        String address = entry.getKey();
        for (RegistrationInfo registrationInfo : entry.getValue()) {
          map.put(address, new HazelcastRegistrationInfo(registrationInfo));
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void entryAdded(EntryEvent<String, HazelcastRegistrationInfo> event) {
    fireRegistrationUpdateEvent(event.getKey());
  }

  private void fireRegistrationUpdateEvent(String address) {
    throttling.onEvent(address);
  }

  private void getAndUpdate(String address) {
    if (nodeSelector.wantsUpdatesFor(address)) {
      List<RegistrationInfo> registrationInfos;
      try {
        registrationInfos = get(address);
      } catch (Exception e) {
        log.trace("A failure occurred while retrieving the updated registrations", e);
        registrationInfos = Collections.emptyList();
      }
      nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(address, registrationInfos));
    }
  }

  @Override
  public void entryEvicted(EntryEvent<String, HazelcastRegistrationInfo> event) {
  }

  @Override
  public void entryRemoved(EntryEvent<String, HazelcastRegistrationInfo> event) {
    fireRegistrationUpdateEvent(event.getKey());
  }

  @Override
  public void entryUpdated(EntryEvent<String, HazelcastRegistrationInfo> event) {
    fireRegistrationUpdateEvent(event.getKey());
  }

  @Override
  public void mapCleared(MapEvent event) {
  }

  @Override
  public void mapEvicted(MapEvent event) {
  }

  @Override
  public void entryExpired(EntryEvent<String, HazelcastRegistrationInfo> event) {
  }

  public void close() {
    map.removeEntryListener(listenerId);
    throttling.close();
  }
}
