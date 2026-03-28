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
package io.quasient.foobar.apps.quantized.replay;

import java.util.concurrent.CountDownLatch;

/**
 * Test application with a mutating method that sets fields on a passed-in object.
 *
 * <p>Used by {@code SideEffectShieldingReplayIT} to test {@code STUB_WITH_SIDE_EFFECTS}: the {@link
 * Enricher#enrich(Order)} method sets fields on the {@link Order} object. When replayed with {@code
 * STUB_WITH_SIDE_EFFECTS}, the return value is stubbed but the field mutations (PUT_FIELD) are
 * replayed via reflection.
 *
 * <p>Also used by {@code UnsafeStubWarningReplayIT} to test unsafe stub detection: stubbing {@code
 * enrich} with plain {@code STUB_FROM_WAL} would silently drop the field mutations, making it an
 * unsafe stub.
 *
 * <p>Supports a 2-thread RPC variant: launch with {@code "service"} argument and call {@link
 * #enrichViaRpc(String[])} via RPC. Call {@link #shutdown(String[])} to release the latch.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * // Single-thread
 * MutatingApp.main(new String[]{"widget", "100"});
 * // Output: "MutatingApp: enriched=true total=110.0 item=widget"
 * }</pre>
 */
public class MutatingApp {

  /**
   * Latch that keeps the peer alive in service mode until {@link #shutdown(String[])} is called.
   */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * An order object whose fields are mutated by the enricher.
   *
   * <p>The fields are public so PAL's field PUT interception captures them in the WAL.
   */
  public static class Order {

    /** The name of the item in this order. */
    public String item;

    /** The base price of the order. */
    public double price;

    /** Whether this order has been enriched. Set by {@link Enricher#enrich(Order)}. */
    public boolean enriched;

    /** The total price after enrichment. Set by {@link Enricher#enrich(Order)}. */
    public double total;

    /**
     * Creates a new order with the given item and price.
     *
     * @param item the item name
     * @param price the base price
     */
    public Order(String item, double price) {
      this.item = item;
      this.price = price;
      this.enriched = false;
      this.total = 0.0;
    }
  }

  /**
   * Enriches orders by setting fields on them. This is the "mutating method" that modifies the
   * passed-in Order object.
   *
   * <p>When stubbed with {@code STUB_WITH_SIDE_EFFECTS}, the return value is stubbed from the WAL
   * and the field mutations (order.enriched, order.total) are replayed via reflection.
   */
  public static class Enricher {

    /** The tax rate applied during enrichment. */
    private final double taxRate;

    /**
     * Creates a new enricher with the given tax rate.
     *
     * @param taxRate the tax rate (e.g., 0.10 for 10%)
     */
    public Enricher(double taxRate) {
      this.taxRate = taxRate;
    }

    /**
     * Enriches the given order by computing the total and marking it as enriched.
     *
     * <p>This method mutates the passed-in order object (side effects) and returns a status string.
     *
     * @param order the order to enrich
     * @return a status string describing the enrichment
     */
    public String enrich(Order order) {
      order.total = order.price * (1.0 + taxRate);
      order.enriched = true;
      return "enriched:" + order.item;
    }
  }

  /**
   * Entry point that creates an order, enriches it, and prints the results.
   *
   * <p>If the first argument is {@code "service"}, waits for RPC calls. Otherwise, uses the
   * arguments as item name and price.
   *
   * @param args command-line arguments: first is "service" for RPC mode, or [item, price]
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "service".equals(args[0])) {
      SHUTDOWN_LATCH.await();
      return;
    }

    String item = args.length > 0 ? args[0] : "item";
    double price = args.length > 1 ? Double.parseDouble(args[1]) : 50.0;

    Order order = new Order(item, price);
    Enricher enricher = new Enricher(0.10);
    String status = enricher.enrich(order);

    // Read the mutated fields — these must be correct after replay
    System.out.println(
        "MutatingApp: enriched="
            + order.enriched
            + " total="
            + order.total
            + " item="
            + order.item
            + " status="
            + status);
  }

  /**
   * RPC-callable method that performs enrichment and returns results.
   *
   * @param args two-element array: [item, price]
   * @return a string describing the enrichment result
   */
  public static String enrichViaRpc(String[] args) {
    String item = args.length > 0 ? args[0] : "item";
    double price = args.length > 1 ? Double.parseDouble(args[1]) : 50.0;

    Order order = new Order(item, price);
    Enricher enricher = new Enricher(0.10);
    String status = enricher.enrich(order);

    return "enriched=" + order.enriched + " total=" + order.total + " status=" + status;
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
