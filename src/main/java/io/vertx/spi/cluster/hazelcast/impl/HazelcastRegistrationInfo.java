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
import java.util.Objects;

/**
 * @author Thomas Segismont
 */
public class HazelcastRegistrationInfo implements DataSerializable {

  private String address;
  private RegistrationInfo registrationInfo;

  public HazelcastRegistrationInfo() {
  }

  public HazelcastRegistrationInfo(String address, RegistrationInfo registrationInfo) {
    this.address = Objects.requireNonNull(address);
    this.registrationInfo = Objects.requireNonNull(registrationInfo);
  }

  public String address() {
    return address;
  }

  public RegistrationInfo registrationInfo() {
    return registrationInfo;
  }

  @Override
  public void writeData(ObjectDataOutput dataOutput) throws IOException {
    dataOutput.writeUTF(address);
    dataOutput.writeUTF(registrationInfo.nodeId());
    dataOutput.writeLong(registrationInfo.seq());
    dataOutput.writeBoolean(registrationInfo.localOnly());
  }

  @Override
  public void readData(ObjectDataInput dataInput) throws IOException {
    address = dataInput.readUTF();
    registrationInfo = new RegistrationInfo(dataInput.readUTF(), dataInput.readLong(), dataInput.readBoolean());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HazelcastRegistrationInfo that = (HazelcastRegistrationInfo) o;

    if (!address.equals(that.address)) return false;
    return registrationInfo.equals(that.registrationInfo);
  }

  @Override
  public int hashCode() {
    int result = address.hashCode();
    result = 31 * result + registrationInfo.hashCode();
    return result;
  }
}
