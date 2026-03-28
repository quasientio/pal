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
package io.quasient.pal.core.transport.zmq.publish;

/**
 * Contains all required configuration for {@link MessagePublisher}
 *
 * <p>Both {@link #highWaterPercent} and {@link #keepPercent} relate to the drop policy and only
 * relevant with {@link PublishingDropPolicy#DROP_OLD}. The percentages are relative to {@link
 * #spscSize}, and must satisfy {@code keep < highWater ≤ 100}.
 *
 * @param spscSize capacity of internal SPSC queue
 * @param pubBatchSize batch size the network thread will try to push in one go
 * @param flushPubSocketOnClose indicates whether we should flush pending messages to the PUB
 *     socket, once shutdown requested and before closing
 * @param zmqPubAddress ZMQ PUB socket endpoint (e.g. inproc://internal_pub or tcp://localhost:8878)
 * @param zmqLinger sets the linger value on the ZMQ PUB socket
 * @param zmqSendTimeOut sets the send timeout value on the ZMQ PUB socket
 * @param zmqSndHWM sets the HWM value on the ZMQ PUB socket
 * @param dropPolicy the {@link PublishingDropPolicy} to enforce
 * @param highWaterPercent drops when reached, e.g. 90 (meaning “when 90 % full …”)
 * @param keepPercent keep newest %, e.g. 50 (meaning “… keep newest 50 %”)
 */
public record MessagePublisherConfig(
    int spscSize,
    int pubBatchSize,
    boolean flushPubSocketOnClose,
    String zmqPubAddress,
    int zmqLinger,
    int zmqSendTimeOut,
    int zmqSndHWM,
    PublishingDropPolicy dropPolicy,
    int highWaterPercent,
    int keepPercent) {}
