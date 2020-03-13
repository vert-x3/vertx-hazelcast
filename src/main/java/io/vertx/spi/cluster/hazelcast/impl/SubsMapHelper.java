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

import com.hazelcast.core.*;
import io.vertx.core.spi.cluster.RegistrationInfo;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Thomas Segismont
 */
public class SubsMapHelper {

  private final ReplicatedMap<HazelcastRegistrationInfo, Boolean> map;

  public SubsMapHelper(HazelcastInstance hazelcast) {
    map = hazelcast.getReplicatedMap("__vertx.subs");
  }

  public List<RegistrationInfo> get(String address) {
    return map.keySet().stream()
      .filter(key -> key.getAddress().equals(address))
      .map(HazelcastRegistrationInfo::getRegistrationInfo)
      .collect(toList());
  }

  public void put(String address, RegistrationInfo registrationInfo) {
    map.put(new HazelcastRegistrationInfo(address, registrationInfo), Boolean.TRUE);
  }

  public void remove(String address, RegistrationInfo registrationInfo) {
    map.remove(new HazelcastRegistrationInfo(address, registrationInfo));
  }

  public void removeAllForNode(String nodeId) {
    for (HazelcastRegistrationInfo key : map.keySet()) {
      if (key.getRegistrationInfo().getNodeId().equals(nodeId)) {
        map.remove(key);
      }
    }
  }

  public String addEntryListener(String address, Runnable callback) {
    return map.addEntryListener(new MapListener(address, callback));
  }

  public void removeEntryListener(String listenerId) {
    map.removeEntryListener(listenerId);
  }

  private static class MapListener implements EntryListener<HazelcastRegistrationInfo, Boolean> {

    final String address;
    final Runnable callback;

    MapListener(String address, Runnable callback) {
      this.address = address;
      this.callback = callback;
    }

    @Override
    public void mapCleared(MapEvent event) {
      callback.run();
    }

    @Override
    public void mapEvicted(MapEvent event) {
      callback.run();
    }

    @Override
    public void entryAdded(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
      if (event.getKey().getAddress().equals(address)) {
        callback.run();
      }
    }

    @Override
    public void entryEvicted(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
      if (event.getKey().getAddress().equals(address)) {
        callback.run();
      }
    }

    @Override
    public void entryRemoved(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
      if (event.getKey().getAddress().equals(address)) {
        callback.run();
      }
    }

    @Override
    public void entryUpdated(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
      if (event.getKey().getAddress().equals(address)) {
        callback.run();
      }
    }
  }
}
