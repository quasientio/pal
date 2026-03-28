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
