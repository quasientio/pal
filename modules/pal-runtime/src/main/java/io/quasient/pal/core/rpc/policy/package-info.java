/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
