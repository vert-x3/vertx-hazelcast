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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.spi.cluster.RegistrationInfo;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * @author Thomas Segismont
 */
public class HazelcastRegistrationInfo implements DataSerializable {

  private RegistrationInfo registrationInfo;

  public HazelcastRegistrationInfo() {
  }

  public HazelcastRegistrationInfo(RegistrationInfo registrationInfo) {
    this.registrationInfo = registrationInfo;
  }

  @Override
  public void writeData(ObjectDataOutput dataOutput) throws IOException {
    dataOutput.writeUTF(registrationInfo.getNodeId());
    dataOutput.writeLong(registrationInfo.getSeq());
    dataOutput.writeBoolean(registrationInfo.isLocalOnly());
  }

  @Override
  public void readData(ObjectDataInput dataInput) throws IOException {
    registrationInfo = new RegistrationInfo(dataInput.readUTF(), dataInput.readLong(), dataInput.readBoolean());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HazelcastRegistrationInfo that = (HazelcastRegistrationInfo) o;

    return Objects.equals(registrationInfo, that.registrationInfo);
  }

  @Override
  public int hashCode() {
    return registrationInfo != null ? registrationInfo.hashCode() : 0;
  }

  public RegistrationInfo unwrap() {
    return registrationInfo;
  }

  public static List<RegistrationInfo> unwrap(Collection<HazelcastRegistrationInfo> collection) {
    return collection.stream()
      .map(HazelcastRegistrationInfo::unwrap)
      .collect(toList());
  }
}
