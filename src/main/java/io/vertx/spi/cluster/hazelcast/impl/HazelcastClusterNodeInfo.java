/*
 * Copyright 2017 Red Hat, Inc.
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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.eventbus.impl.clustered.ClusterNodeInfo;
import io.vertx.core.net.impl.ServerID;

import java.io.IOException;

/**
 * @author Thomas Segismont
 */
public class HazelcastClusterNodeInfo extends ClusterNodeInfo implements DataSerializable {

  public HazelcastClusterNodeInfo() {
  }

  public HazelcastClusterNodeInfo(ClusterNodeInfo clusterNodeInfo) {
    super(clusterNodeInfo.nodeId, clusterNodeInfo.serverID);
  }

  @Override
  public void writeData(ObjectDataOutput dataOutput) throws IOException {
    dataOutput.writeUTF(nodeId);
    dataOutput.writeInt(serverID.port);
    dataOutput.writeUTF(serverID.host);
  }

  @Override
  public void readData(ObjectDataInput dataInput) throws IOException {
    nodeId = dataInput.readUTF();
    serverID = new ServerID(dataInput.readInt(), dataInput.readUTF());
  }

  // We replace any ClusterNodeInfo instances with HazelcastClusterNodeInfo
  // This allows them to be serialized more optimally using DataSerializable
  @SuppressWarnings("unchecked")
  public static <V> V convertClusterNodeInfo(V val) {
    if (val.getClass() == ClusterNodeInfo.class) {
      ClusterNodeInfo cni = (ClusterNodeInfo) val;
      HazelcastClusterNodeInfo hcni = new HazelcastClusterNodeInfo(cni);
      return (V) hcni;
    } else {
      return val;
    }
  }
}
