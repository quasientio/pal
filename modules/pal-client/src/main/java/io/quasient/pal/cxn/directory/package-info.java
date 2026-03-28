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
 * etcd-based directory service client implementation.
 *
 * <p>{@link PalDirectory} is the main entry point for interacting with the PAL directory:
 *
 * <ul>
 *   <li>Peer registration and discovery
 *   <li>Log metadata management
 *   <li>Intercept registration and watching
 * </ul>
 *
 * @see PalDirectory Main directory service interface
 * @see PeerLease Peer registration lease management
 */
package io.quasient.pal.cxn.directory;
