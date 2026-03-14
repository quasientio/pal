/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
