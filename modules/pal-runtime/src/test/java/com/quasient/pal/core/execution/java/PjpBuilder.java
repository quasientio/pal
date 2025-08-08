/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static org.mockito.Mockito.*;

import com.quasient.pal.common.runtime.Context;
import org.aspectj.lang.*;

/** Builds a ProceedingJoinPoint stub for dispatcher tests. */
public final class PjpBuilder {

  private final ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class, withSettings().lenient());
  private final JoinPoint.StaticPart sp =
      mock(JoinPoint.StaticPart.class, withSettings().lenient());

  @SuppressWarnings("unused")
  private PjpBuilder(Context ctx) {
    // final/default methods must be stubbed with doReturn/when
    doReturn(sp).when(pjp).getStaticPart();
  }

  public static PjpBuilder forContext(Context ctx) {
    return new PjpBuilder(ctx);
  }

  public PjpBuilder sender(Object sender) {
    doReturn(sender).when(pjp).getThis();
    return this;
  }

  public PjpBuilder target(Object target) {
    doReturn(target).when(pjp).getTarget();
    return this;
  }

  public PjpBuilder args(Object[] args) {
    doReturn(args).when(pjp).getArgs();
    return this;
  }

  public ProceedingJoinPoint build() throws Throwable {
    doThrow(new IllegalStateException("pjp.proceed() must not be used")).when(pjp).proceed();
    return pjp;
  }
}
