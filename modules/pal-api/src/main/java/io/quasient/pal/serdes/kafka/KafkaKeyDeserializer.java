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
package io.quasient.pal.serdes.kafka;

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Custom deserializer for Kafka keys that maintains the original fully qualified class name after
 * Maven shading. This allows Kafka properties to reference the deserializer without requiring
 * changes to the package name, ensuring compatibility and ease of configuration. See issue #168 for
 * more details.
 *
 * @see StringDeserializer
 */
public final class KafkaKeyDeserializer extends StringDeserializer {}
