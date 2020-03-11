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
import io.vertx.core.Handler;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Thomas Segismont
 */
public class HazelcastRegistrationListener implements RegistrationListener {

  private interface InternalState {
    List<RegistrationInfo> initialState();

    void start();

    void stop();
  }

  private final VertxInternal vertx;
  private final HazelcastInstance hazelcast;
  private final MultiMap<String, HazelcastRegistrationInfo> multiMap;
  private final String address;
  private final AtomicReference<InternalState> internalState;

  private Handler<List<RegistrationInfo>> handler;
  private Handler<Void> endHandler;

  public HazelcastRegistrationListener(VertxInternal vertx, HazelcastInstance hazelcast, MultiMap<String, HazelcastRegistrationInfo> multiMap, String address, List<RegistrationInfo> infos) {
    this.vertx = vertx;
    this.hazelcast = hazelcast;
    this.multiMap = multiMap;
    this.address = address;
    internalState = new AtomicReference<>(new IdleState(infos));
  }

  @Override
  public List<RegistrationInfo> initialState() {
    return internalState.get().initialState();
  }

  @Override
  public synchronized RegistrationListener handler(Handler<List<RegistrationInfo>> handler) {
    this.handler = handler;
    return this;
  }

  private synchronized Handler<List<RegistrationInfo>> getHandler() {
    return handler;
  }

  @Override
  public RegistrationListener exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public synchronized RegistrationListener endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  private synchronized Handler<Void> getEndHandler() {
    return endHandler;
  }

  @Override
  public void start() {
    internalState.get().start();
  }

  @Override
  public void stop() {
    internalState.get().stop();
  }

  private class IdleState implements InternalState {

    final List<RegistrationInfo> infos;

    IdleState(List<RegistrationInfo> infos) {
      this.infos = infos;
    }

    @Override
    public List<RegistrationInfo> initialState() {
      return infos;
    }

    @Override
    public void start() {
      StartedState startedState = new StartedState();
      if (internalState.compareAndSet(this, startedState)) {
        startedState.init(infos);
      }
    }

    @Override
    public void stop() {
      internalState.compareAndSet(this, new StoppedState());
    }
  }

  private class StartedState implements InternalState {

    final TaskQueue taskQueue = new TaskQueue();
    final MultiMapListener multiMapListener = new MultiMapListener(this::multimapChanged);

    String listenerId;
    List<RegistrationInfo> initial, last;

    void init(List<RegistrationInfo> infos) {
      taskQueue.execute(() -> {
        if (this != internalState.get()) {
          return;
        }
        initial = infos;
        listenerId = multiMap.addEntryListener(multiMapListener, address, true);
        multimapChanged(); // make sure state is checked if entry is removed before listener is registered
      }, vertx.getWorkerPool());
    }

    void multimapChanged() {
      taskQueue.execute(() -> {
        handleDataUpdate(HazelcastRegistrationInfo.unwrap(multiMap.get(address)));
      }, vertx.getWorkerPool());
    }

    void handleDataUpdate(List<RegistrationInfo> infos) {
      if (this != internalState.get()) {
        return;
      }
      Runnable emission;
      if (initial != null) {
        if (infos.isEmpty()) {
          emission = terminalEvent();
        } else if (!initial.equals(infos)) {
          emission = itemEvent(infos);
        } else {
          emission = null;
        }
        last = infos;
        initial = null;
      } else if (last.isEmpty() || last.equals(infos)) {
        emission = null;
      } else {
        last = infos;
        if (last.isEmpty()) {
          emission = terminalEvent();
        } else {
          emission = itemEvent(infos);
        }
      }
      if (emission != null) {
        emission.run();
      }
    }

    private Runnable itemEvent(List<RegistrationInfo> infos) {
      Handler<List<RegistrationInfo>> h = getHandler();
      return () -> {
        if (h != null) {
          h.handle(infos);
        }
      };
    }

    private synchronized Runnable terminalEvent() {
      multiMap.removeEntryListener(listenerId);
      Handler<Void> e = getEndHandler();
      return () -> {
        if (e != null) {
          e.handle(null);
        }
        stop();
      };
    }

    @Override
    public List<RegistrationInfo> initialState() {
      return null;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
      if (internalState.compareAndSet(this, new StoppedState())) {
        taskQueue.execute(() -> {
          if (listenerId != null) {
            multiMap.removeEntryListener(listenerId);
          }
        }, vertx.getWorkerPool());
      }
    }
  }

  private class StoppedState implements InternalState {

    @Override
    public List<RegistrationInfo> initialState() {
      return null;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

  }

  private static class MultiMapListener implements EntryListener<String, HazelcastRegistrationInfo> {

    final Runnable callback;

    MultiMapListener(Runnable callback) {
      this.callback = callback;
    }

    @Override
    public void entryAdded(EntryEvent<String, HazelcastRegistrationInfo> event) {
      callback.run();
    }

    @Override
    public void entryEvicted(EntryEvent<String, HazelcastRegistrationInfo> event) {
      callback.run();
    }

    @Override
    public void entryRemoved(EntryEvent<String, HazelcastRegistrationInfo> event) {
      callback.run();
    }

    @Override
    public void entryUpdated(EntryEvent<String, HazelcastRegistrationInfo> event) {
      callback.run();
    }

    @Override
    public void mapCleared(MapEvent event) {
      callback.run();
    }

    @Override
    public void mapEvicted(MapEvent event) {
      callback.run();
    }
  }
}
