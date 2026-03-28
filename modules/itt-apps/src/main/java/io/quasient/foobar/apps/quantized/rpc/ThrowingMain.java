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
package io.quasient.foobar.apps.quantized.rpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test application where {@code main()} calls a method that always throws.
 *
 * <p>Used by integration tests to verify that {@code pal print --with-return} correctly matches an
 * {@code EXEC_THROWABLE} message as the return for the operation that threw. The exception is
 * caught in {@code main()} so the peer exits cleanly.
 *
 * <p>WAL structure (offset 0 is {@code alwaysThrows()} call, its return is the THROWABLE):
 *
 * <pre>
 *   0: EXEC_CLASS_METHOD  alwaysThrows()
 *   1: EXEC_CONSTRUCTOR   new RuntimeException(...)
 *   2: EXEC_RETURN_VALUE  (constructor return)
 *   3: EXEC_THROWABLE     (alwaysThrows threw RuntimeException)
 * </pre>
 */
@SuppressFBWarnings(
    value = "REC_CATCH_EXCEPTION",
    justification = "Test app - intentional broad catch for exception testing")
public class ThrowingMain {

  /**
   * Entry point that calls {@link #alwaysThrows()} inside a try-catch.
   *
   * @param args command-line arguments (ignored)
   */
  @SuppressWarnings("CatchAndPrintStackTrace")
  public static void main(String[] args) {
    try {
      alwaysThrows();
    } catch (Exception e) {
      System.err.println("Caught expected exception: " + e.getMessage());
    }
  }

  /**
   * Always throws a {@link RuntimeException}.
   *
   * @throws RuntimeException always
   */
  static void alwaysThrows() {
    throw new RuntimeException("intentional test exception");
  }
}
