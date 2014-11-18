/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastTestProperties {
  public static void setProperties() {
//    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
//    System.setProperty("hazelcast.icmp.enabled","true");
//    System.setProperty("hazelcast.icmp.timeout","5000");
//    System.setProperty("hazelcast.icmp.ttl","3");
  }
}
