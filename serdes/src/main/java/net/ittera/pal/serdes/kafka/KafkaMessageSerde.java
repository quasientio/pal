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

package net.ittera.pal.serdes.kafka;

import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class KafkaMessageSerde implements Serde<byte[]> {

  private final Serde<byte[]> inner;

  public KafkaMessageSerde() {
    inner = Serdes.serdeFrom(new KafkaMessageSerializer(), new KafkaMessageDeserializer());
  }

  @Override
  public void configure(Map<String, ?> map, boolean b) {
    inner.serializer().configure(map, b);
    inner.deserializer().configure(map, b);
  }

  @Override
  public void close() {
    inner.serializer().close();
    inner.deserializer().close();
  }

  @Override
  public Serializer<byte[]> serializer() {
    return inner.serializer();
  }

  @Override
  public Deserializer<byte[]> deserializer() {
    return inner.deserializer();
  }
}
