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

package io.vertx.spi.cluster.hazelcast.impl;

import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.spi.cluster.ChoosableIterable;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ChoosableSet<T> implements ChoosableIterable<T> {

  private static final ChoosableSet<?> EMPTY_SET = new ChoosableSet<>(0);

  private volatile boolean initialised;
  private final Set<T> ids;
  private volatile Iterator<T> iter;

  @SuppressWarnings("unchecked")
  public static <T> ChoosableSet<T> empty() {
    return (ChoosableSet<T>) EMPTY_SET;
  }

  public ChoosableSet(int initialSize) {
    ids = new ConcurrentHashSet<>(initialSize);
  }

  public ChoosableSet(Collection<T> c) {
    ids = new ConcurrentHashSet<>(c.size());
    ids.addAll(c);
  }

  public boolean isInitialised() {
    return initialised;
  }

  public ChoosableSet<T> setInitialised() {
    this.initialised = true;
    return this;
  }

  public boolean add(T elem) {
    return ids.add(elem);
  }

  public boolean remove(T elem) {
    return ids.remove(elem);
  }

  public boolean addAll(Collection<T> c) {
    return ids.addAll(c);
  }

  @Override
  public boolean isEmpty() {
    return ids.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return ids.iterator();
  }

  @Override
  public synchronized T choose() {
    if (ids.isEmpty()) {
      return null;
    }
    if (iter == null || !iter.hasNext()) {
      iter = ids.iterator();
    }
    try {
      return iter.next();
    } catch (NoSuchElementException ignored) {
      return null;
    }
  }
}
