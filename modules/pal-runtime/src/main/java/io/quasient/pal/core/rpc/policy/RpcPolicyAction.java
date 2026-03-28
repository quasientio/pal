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
