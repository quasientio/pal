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
 * RPC policy system for controlling access to operations exposed via RPC.
 *
 * <p>This package provides a YAML-driven policy framework that gates inbound RPC operations using
 * pattern matching, channel awareness, member-type filtering, and built-in safety presets. Policies
 * are evaluated on every incoming RPC message in the dispatch path, enabling fine-grained access
 * control without modifying application code.
 *
 * <p>Key types:
 *
 * <ul>
 *   <li>{@link io.quasient.pal.core.rpc.policy.RpcPolicyAction} &mdash; the action to take when a
 *       rule matches (allow, deny, or log variants)
 *   <li>{@link io.quasient.pal.core.rpc.policy.MemberCategory} &mdash; classifies execution message
 *       types into broad member categories (method, constructor, field access)
 * </ul>
 */
package io.quasient.pal.core.rpc.policy;
