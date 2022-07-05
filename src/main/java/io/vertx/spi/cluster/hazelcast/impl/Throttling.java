/*
 * Copyright 2021 Red Hat, Inc.
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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Throttling {

  // @formatter:off
  private enum State {
    NEW {
      State pending() { return PENDING; }
      State start() { return RUNNING; }
      State done() { throw new IllegalStateException(); }
      State next() { throw new IllegalStateException(); }
    },
    PENDING {
      State pending() { return this; }
      State start() { return RUNNING; }
      State done() { throw new IllegalStateException(); }
      State next() { throw new IllegalStateException(); }
    },
    RUNNING {
      State pending() { return RUNNING_PENDING; }
      State start() { throw new IllegalStateException(); }
      State done() { return FINISHED; }
      State next() { throw new IllegalStateException(); }
    },
    RUNNING_PENDING {
      State pending() { return this; }
      State start() { throw new IllegalStateException(); }
      State done() { return FINISHED_PENDING; }
      State next() { throw new IllegalStateException(); }
    },
    FINISHED {
      State pending() { return FINISHED_PENDING; }
      State start() { throw new IllegalStateException(); }
      State done() { throw new IllegalStateException(); }
      State next() { return null; }
    },
    FINISHED_PENDING {
      State pending() { return this; }
      State start() { throw new IllegalStateException(); }
      State done() { throw new IllegalStateException(); }
      State next() { return NEW; }
    };

    abstract State pending();
    abstract State start();
    abstract State done();
    abstract State next();
  }
  // @formatter:on

  private final Consumer<String> action;
  private final ScheduledExecutorService executorService;
  private final ConcurrentMap<String, State> map;
  /*
  The counter is incremented when a new event is received.
  It is decremented:
   - immediately if the map already contains an entry for the corresponding address, or
   - when the map entry is removed
  When the close method is invoked, the counter is set to -1 and the previous value (N) is stored.
  A negative counter value prevents new events from being handled.
  The close method blocks until the counter reaches the value -(1 + N).
  This allows to stop the throttling gracefully.
   */
  private final AtomicInteger counter;
  private final Object condition;

  public Throttling(Consumer<String> action) {
    this.action = action;
    this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "vertx-hazelcast-service-throttling-thread");
      thread.setDaemon(true);
      return thread;
    });
    map = new ConcurrentHashMap<>();
    counter = new AtomicInteger();
    condition = new Object();
  }

  public void onEvent(String address) {
    if (!tryIncrementCounter()) {
      return;
    }
    State curr = map.compute(address, (s, state) -> state == null ? State.NEW : state.pending());
    if (curr == State.NEW) {
      executorService.execute(() -> {
        run(address);
      });
    } else {
      decrementCounter();
    }
  }

  private void run(String address) {
    map.computeIfPresent(address, (s, state) -> state.start());
    try {
      action.accept(address);
    } finally {
      map.computeIfPresent(address, (s, state) -> state.done());
      executorService.schedule(() -> {
        checkState(address);
      }, 20, TimeUnit.MILLISECONDS);
    }
  }

  private void checkState(String address) {
    State curr = map.computeIfPresent(address, (s, state) -> state.next());
    if (curr == State.NEW) {
      run(address);
    } else {
      decrementCounter();
    }
  }

  private boolean tryIncrementCounter() {
    int i;
    do {
      i = counter.get();
      if (i < 0) {
        return false;
      }
    } while (!counter.compareAndSet(i, i + 1));
    return true;
  }

  private void decrementCounter() {
    if (counter.decrementAndGet() < 0) {
      synchronized (condition) {
        condition.notify();
      }
    }
  }

  public void close() {
    synchronized (condition) {
      int i = counter.getAndSet(-1);
      if (i == 0) {
        return;
      }
      boolean interrupted = false;
      do {
        try {
          condition.wait();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      } while (counter.get() != -(i + 1));
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
