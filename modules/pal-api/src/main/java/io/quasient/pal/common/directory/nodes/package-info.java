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
 * Data structures stored in the PAL directory (etcd).
 *
 * <ul>
 *   <li>{@link PeerInfo} - Peer registration metadata (UUID, RPC endpoints, status)
 *   <li>{@link LogInfo} - Log metadata (name, backend type, partition info)
 *   <li>{@link InterceptRequest} - Dynamic intercept registration
 * </ul>
 *
 * <p>All nodes extend {@link InfoNode} which tracks creation and modification times.
 */
package io.quasient.pal.common.directory.nodes;
