/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.kafka.typed;

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
