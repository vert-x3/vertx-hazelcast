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
package io.vertx.spi.cluster.hazelcast.tests;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class TestClusterManager {

  public static Config getConf(Config conf) {
    Module hzMod = NetworkConfig.class.getModule();
    if (hzMod.isNamed()) {
      NetworkConfig networkConfig = conf.getNetworkConfig();
      networkConfig.getInterfaces().addInterface("127.0.0.1");
      networkConfig.getJoin().getMulticastConfig().setEnabled(false);
      networkConfig.getJoin().getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
    }
    return conf;
  }

  public static ClusterManager getClusterManager() {
    return getClusterManager(ConfigUtil.loadConfig());
  }

  public static ClusterManager getClusterManager(Config conf) {
    return new HazelcastClusterManager(getConf(conf));
  }
}
