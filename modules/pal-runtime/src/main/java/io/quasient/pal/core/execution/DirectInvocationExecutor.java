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
 * Default executor that invokes callables directly on the current thread with zero overhead.
 *
 * <p>This is the fallback executor used when no thread affinity is specified or when the requested
 * affinity has no matching registered executor.
 */
public class DirectInvocationExecutor implements InvocationExecutor {

  /** Creates a new direct invocation executor. */
  public DirectInvocationExecutor() {}

  @Override
  public Object execute(Callable<Object> invocation) throws Exception {
    return invocation.call();
  }
}
