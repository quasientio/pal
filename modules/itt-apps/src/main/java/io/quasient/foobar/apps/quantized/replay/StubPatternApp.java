/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.quantized.replay;

import java.util.concurrent.CountDownLatch;

/**
 * Test application with clearly separated classes: one that should be stubbed and one that should
 * be re-executed.
 *
 * <p>Used by {@code StubFromWalReplayIT} (stub pattern test) and {@code YamlPolicyReplayIT} to
 * verify that Ant-style patterns correctly stub matching operations while re-executing non-matching
 * ones.
 *
 * <p>The {@link ExternalService} class represents code that should be stubbed (e.g., external API
 * calls). The {@link Calculator} class represents deterministic code that should be re-executed.
 *
 * <p>Supports a 2-thread RPC variant: launch with {@code "service"} argument and call {@link
 * #computeViaRpc(String[])} via RPC. Call {@link #shutdown(String[])} to release the latch.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * // Single-thread
 * StubPatternApp.main(new String[]{"5", "3"});
 * // Output: "StubPattern: sum=8 external=VALUE_5_3 combined=8_VALUE_5_3"
 * }</pre>
 */
public class StubPatternApp {

  /**
   * Latch that keeps the peer alive in service mode until {@link #shutdown(String[])} is called.
   */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * A deterministic calculator class whose operations should be re-executed during replay.
   *
   * <p>All methods are purely deterministic — given the same inputs they produce the same outputs.
   */
  public static class Calculator {

    /**
     * Adds two integers.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum
     */
    public int add(int a, int b) {
      return a + b;
    }

    /**
     * Multiplies two integers.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the product
     */
    public int multiply(int a, int b) {
      return a * b;
    }
  }

  /**
   * An external service class whose operations should be stubbed during replay.
   *
   * <p>Simulates an external dependency that returns values not reproducible during replay. In a
   * real scenario, this might be a database call, HTTP request, or other I/O operation.
   */
  public static class ExternalService {

    /**
     * Fetches a value that depends on the inputs. In real usage, this would be non-deterministic.
     *
     * @param key1 the first key
     * @param key2 the second key
     * @return a value string
     */
    public String fetchValue(String key1, String key2) {
      return "VALUE_" + key1 + "_" + key2;
    }

    /**
     * Validates a result string. Simulates an external validation call.
     *
     * @param result the result to validate
     * @return true if the result is non-null and non-empty
     */
    public boolean validate(String result) {
      return result != null && !result.isEmpty();
    }
  }

  /**
   * Entry point that uses both Calculator (re-execute) and ExternalService (stub).
   *
   * <p>If the first argument is {@code "service"}, waits for RPC calls. Otherwise, performs
   * computations using both classes.
   *
   * @param args command-line arguments: first is "service" for RPC mode, or [a, b] operands
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "service".equals(args[0])) {
      SHUTDOWN_LATCH.await();
      return;
    }

    String a = args.length > 0 ? args[0] : "5";
    String b = args.length > 1 ? args[1] : "3";

    Calculator calc = new Calculator();
    ExternalService ext = new ExternalService();

    int sum = calc.add(Integer.parseInt(a), Integer.parseInt(b));
    String externalValue = ext.fetchValue(a, b);
    boolean valid = ext.validate(externalValue);
    int product = calc.multiply(Integer.parseInt(a), Integer.parseInt(b));

    System.out.println(
        "StubPattern: sum="
            + sum
            + " product="
            + product
            + " external="
            + externalValue
            + " valid="
            + valid
            + " combined="
            + sum
            + "_"
            + externalValue);
  }

  /**
   * RPC-callable method that uses both Calculator and ExternalService.
   *
   * @param args two-element array: [a, b] operands
   * @return a string with computation results
   */
  public static String computeViaRpc(String[] args) {
    String a = args.length > 0 ? args[0] : "5";
    String b = args.length > 1 ? args[1] : "3";

    Calculator calc = new Calculator();
    ExternalService ext = new ExternalService();

    int sum = calc.add(Integer.parseInt(a), Integer.parseInt(b));
    String externalValue = ext.fetchValue(a, b);

    return "sum=" + sum + " external=" + externalValue;
  }

  /**
   * Signals the peer to shut down by releasing the latch in {@link #main(String[])}.
   *
   * @param args ignored (required for {@code pal call -m} compatibility)
   */
  public static void shutdown(String[] args) {
    SHUTDOWN_LATCH.countDown();
  }
}
