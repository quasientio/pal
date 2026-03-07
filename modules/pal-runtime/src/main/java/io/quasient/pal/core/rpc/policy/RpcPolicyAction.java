/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

/**
 * Action to take when an RPC policy rule matches an incoming operation.
 *
 * <p>Rules in an RPC policy evaluate to one of these actions, which determines whether the
 * operation is permitted and whether the decision is logged.
 */
public enum RpcPolicyAction {

  /** Allow the operation to proceed. */
  ALLOW,

  /** Deny the operation and throw {@code RpcAccessDeniedException}. */
  DENY,

  /** Log the operation details via SLF4J, then allow it to proceed. */
  LOG_AND_ALLOW,

  /** Log the operation details via SLF4J, then deny it. */
  LOG_AND_DENY
}
