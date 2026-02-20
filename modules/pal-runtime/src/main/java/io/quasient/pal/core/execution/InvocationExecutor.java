/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution;

import java.util.concurrent.Callable;

/**
 * Strategy for executing method invocations with thread-affinity control.
 *
 * <p>Implementations marshal execution onto specific threads (e.g., JavaFX Application Thread)
 * while blocking the calling thread for the result.
 */
@FunctionalInterface
public interface InvocationExecutor {

  /**
   * Executes the given invocation, potentially on a different thread.
   *
   * @param invocation the callable representing the method invocation to execute
   * @return the result of the invocation
   * @throws Exception if the invocation throws an exception
   */
  Object execute(Callable<Object> invocation) throws Exception;
}
