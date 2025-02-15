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

import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Custom Kafka key serializer that extends {@link StringSerializer} to preserve package naming
 * after mvn-shading. This ensures Kafka properties can reference this serializer without requiring
 * changes to its package name, as only external dependencies are relocated during the shading
 * process.
 *
 * @see <a href="https://gitlab.com/cometera/pal/-/issues/168">issue #168 for details</a>
 * @see StringSerializer
 */
public final class KafkaKeySerializer extends StringSerializer {

  /** Constructs a new {@code KafkaKeySerializer}. */
  public KafkaKeySerializer() {}
}
