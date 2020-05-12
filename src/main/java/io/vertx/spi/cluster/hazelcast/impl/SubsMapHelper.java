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
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class SubsMapHelper implements EntryListener<HazelcastRegistrationInfo, Boolean> {

  private final ReplicatedMap<HazelcastRegistrationInfo, Boolean> map;
  private final NodeSelector nodeSelector;
  private final String listenerId;

  public SubsMapHelper(HazelcastInstance hazelcast, NodeSelector nodeSelector) {
    map = hazelcast.getReplicatedMap("__vertx.subs");
    this.nodeSelector = nodeSelector;
    listenerId = map.addEntryListener(this);
  }

  public List<RegistrationInfo> get(String address) {
    List<RegistrationInfo> list = new ArrayList<>();
    for (HazelcastRegistrationInfo key : map.keySet()) {
      if (key.address().equals(address)) {
        RegistrationInfo registrationInfo = key.registrationInfo();
        list.add(registrationInfo);
      }
    }
    return list;
  }

  public void put(String address, RegistrationInfo registrationInfo) {
    map.put(new HazelcastRegistrationInfo(address, registrationInfo), Boolean.TRUE);
  }

  public void remove(String address, RegistrationInfo registrationInfo) {
    map.remove(new HazelcastRegistrationInfo(address, registrationInfo));
  }

  public void removeAllForNode(String nodeId) {
    for (HazelcastRegistrationInfo key : map.keySet()) {
      if (key.registrationInfo().nodeId().equals(nodeId)) {
        map.remove(key);
      }
    }
  }

  @Override
  public void entryAdded(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
    fireRegistrationUpdateEvent(event);
  }

  private void fireRegistrationUpdateEvent(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
    String address = event.getKey().address();
    List<RegistrationInfo> registrations = get(address);
    nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(address, registrations));
  }

  @Override
  public void entryEvicted(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {

  }

  @Override
  public void entryRemoved(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {
    fireRegistrationUpdateEvent(event);
  }

  @Override
  public void entryUpdated(EntryEvent<HazelcastRegistrationInfo, Boolean> event) {

  }

  @Override
  public void mapCleared(MapEvent event) {

  }

  @Override
  public void mapEvicted(MapEvent event) {

  }

  public void close() {
    map.removeEntryListener(listenerId);
  }
}
