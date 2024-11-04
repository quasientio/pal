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

package net.ittera.pal.serdes.kafka.typed;

import net.ittera.pal.messages.LogMessage;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class KafkaLogMessageSerde implements Serde<LogMessage<?>> {

  private final Serializer<LogMessage<?>> serializer;
  private final Deserializer<LogMessage<?>> deserializer;

  public KafkaLogMessageSerde() {
    this.serializer = new KafkaLogMessageSerializer();
    this.deserializer = new KafkaLogMessageDeserializer();
  }

  @Override
  public Serializer<LogMessage<?>> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<LogMessage<?>> deserializer() {
    return deserializer;
  }

  @Override
  public void configure(java.util.Map<String, ?> configs, boolean isKey) {
    serializer.configure(configs, isKey);
    deserializer.configure(configs, isKey);
  }

  @Override
  public void close() {
    serializer.close();
    deserializer.close();
  }
}
