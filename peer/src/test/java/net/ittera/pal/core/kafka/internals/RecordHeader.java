/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * This class copied from kafka project to be used in unit tests with MockConsumer. Only the package
 * name has been modified.
 */
package net.ittera.pal.core.kafka.internals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.utils.Utils;

public class RecordHeader implements Header {
  private final String key;
  private ByteBuffer valueBuffer;
  private byte[] value;

  public RecordHeader(String key, byte[] value) {
    Objects.requireNonNull(key, "Null header keys are not permitted");
    this.key = key;
    this.value = value;
  }

  public RecordHeader(String key, ByteBuffer valueBuffer) {
    Objects.requireNonNull(key, "Null header keys are not permitted");
    this.key = key;
    this.valueBuffer = valueBuffer;
  }

  public String key() {
    return key;
  }

  public byte[] value() {
    if (value == null && valueBuffer != null) {
      value = Utils.toArray(valueBuffer);
      valueBuffer = null;
    }
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RecordHeader header = (RecordHeader) o;
    return Objects.equals(key, header.key) && Arrays.equals(value(), header.value());
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + Arrays.hashCode(value());
    return result;
  }

  @Override
  public String toString() {
    return "RecordHeader(key = " + key + ", value = " + Arrays.toString(value()) + ")";
  }
}
