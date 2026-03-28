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
/**
 * ZeroMQ PUB-SUB message publishing for real-time message streaming.
 *
 * <p>{@link MessagePublisher} publishes messages to subscribers via a ZeroMQ PUB socket. This
 * enables external tools to observe message flow in real-time.
 *
 * <ul>
 *   <li>{@link MessagePublisher} - Publishes messages with configurable drop policies
 *   <li>{@link PublishingDropPolicy} - Strategy for handling queue congestion
 *   <li>{@link MessagePublisherConfig} - Publisher configuration
 * </ul>
 */
package io.quasient.pal.core.transport.zmq.publish;
