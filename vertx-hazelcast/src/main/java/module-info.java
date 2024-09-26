/*
 * Copyright 2024 Red Hat, Inc.
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

module io.vertx.clustermanager.hazelcast {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires static io.vertx.healthcheck;

  requires io.vertx.core;
  requires io.vertx.core.logging;
  requires com.hazelcast.core;

  exports io.vertx.spi.cluster.hazelcast;
  exports io.vertx.spi.cluster.hazelcast.spi;
  exports io.vertx.spi.cluster.hazelcast.impl to io.vertx.clustermanager.hazelcast.tests, com.hazelcast.core;

  uses io.vertx.spi.cluster.hazelcast.spi.HazelcastObjectProvider;

  provides io.vertx.core.spi.VertxServiceProvider with io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

}
