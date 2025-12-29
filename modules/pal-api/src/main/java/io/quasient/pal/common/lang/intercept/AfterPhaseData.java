/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Data from the AFTER phase of an AROUND intercept.
 *
 * <p>Contains the return value and/or exception from the intercepted method execution. This is an
 * internal data structure used to communicate between {@link AroundSocketAccessor} and {@link
 * InterceptContext#proceed()}.
 *
 * @param returnValue the return value from method execution (null if void or exception)
 * @param thrownException the exception thrown by the method (null if normal completion)
 * @param isVoid whether the method has void return type
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Internal API - exception must be accessible to callback handlers")
public record AfterPhaseData(
    @Nullable Object returnValue, @Nullable Throwable thrownException, boolean isVoid) {

  /**
   * Returns whether the method threw an exception.
   *
   * @return {@code true} if the method threw, {@code false} if it completed normally
   */
  public boolean hasException() {
    return thrownException != null;
  }
}
