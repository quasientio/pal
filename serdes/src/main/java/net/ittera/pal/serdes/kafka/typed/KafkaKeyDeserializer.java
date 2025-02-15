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

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Custom key deserializer for Kafka that maintains the original package and class name after Maven
 * shading.
 *
 * <p>This deserializer extends {@link StringDeserializer} to allow Kafka properties to reference it
 * directly without requiring package name changes. Only dependencies are relocated during shading,
 * ensuring that the deserializer remains in its original location.
 *
 * @see StringDeserializer
 */
public final class KafkaKeyDeserializer extends StringDeserializer {}
