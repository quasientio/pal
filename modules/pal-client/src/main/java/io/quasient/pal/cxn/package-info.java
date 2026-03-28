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
 * Connection and communication utilities for PAL clients.
 *
 * <p>This package provides client-side abstractions for connecting to PAL infrastructure:
 *
 * <ul>
 *   <li>{@link ThinPeer} - Lightweight peer client for RPC without full runtime
 *   <li>{@link JmxClient} - JMX client for peer monitoring
 * </ul>
 *
 * <p>Subpackages:
 *
 * <ul>
 *   <li>{@code directory} - etcd-based directory service client
 *   <li>{@code chronicle} - Chronicle Queue utilities
 * </ul>
 */
package io.quasient.pal.cxn;
