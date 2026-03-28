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
 * ZeroMQ socket utilities and RPC server infrastructure.
 *
 * <p>PAL uses ZeroMQ for peer-to-peer RPC communication. This package provides:
 *
 * <ul>
 *   <li>{@link ZmqRpcServer} - REQ-REP based RPC server
 *   <li>{@link ZmqUtils} - Socket configuration utilities
 * </ul>
 *
 * @see io.quasient.pal.core.transport.zmq.publish
 */
package io.quasient.pal.core.transport.zmq;
