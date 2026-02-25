/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.replay;

import java.util.*;

/**
 * Calculates shopping cart totals with multiple discount rules. Parses a cart specification string,
 * applies base prices, and computes the final total including: tax (7%), loyalty discount (5% for
 * members), bulk discount (10% for 5+ items), and diversity discount (2% for 3+ different item
 * types).
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * Input:  "milk:1"
 * Output: 1.52 (1 milk @ $1.50 + tax - loyalty)
 *
 * Input:  "bread:2,apple:3"
 * Output: 5.07 (2 bread + 3 apples + tax - loyalty - bulk)
 *
 * Input:  "milk:2,bread:1,apple:5"
 * Output: 7.40 (8 items + tax - loyalty - bulk - diversity)
 * }</pre>
 */
public class MinimalReceiptCalculator {
  private static int runCount;
  private static final HashMap<String, Double> PRICE_PER_UNIT = new HashMap<>();
  private final double taxRate;
  private final boolean loyalCustomer;

  static {
    PRICE_PER_UNIT.put("milk", 1.5);
    PRICE_PER_UNIT.put("bread", 2.0);
    PRICE_PER_UNIT.put("apple", 0.5);
  }

  public MinimalReceiptCalculator(double taxRate, boolean loyalCustomer) {
    this.taxRate = taxRate;
    this.loyalCustomer = loyalCustomer;
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Expected a single cart string.");
      return;
    }
    runCount++;
    MinimalReceiptCalculator calc = new MinimalReceiptCalculator(0.07, true);
    double total = calc.computeTotal(args[0]);
    System.out.println("Run " + runCount + ": " + total);
  }

  private static HashMap<String, Integer> parseCart(String[] pairs) {
    HashMap<String, Integer> cart = new HashMap<>();
    for (String p : pairs) {
      String[] kv = p.split(":");
      if (kv.length == 2) cart.put(kv[0], Integer.parseInt(kv[1]));
    }
    return cart;
  }

  public double computeTotal(String cartSpec) {
    HashMap<String, Integer> cart = parseCart(cartSpec.split(","));
    ArrayList<String> items = new ArrayList<>(cart.keySet());
    int totalQty = 0;
    double subtotal = 0.0;
    for (Map.Entry<String, Integer> e : cart.entrySet()) {
      Double price = PRICE_PER_UNIT.get(e.getKey());
      if (price != null) {
        int qty = e.getValue();
        subtotal += price * qty;
        totalQty += qty;
      }
    }
    subtotal = applyBulkDiscount(subtotal, new int[] {totalQty});
    double afterLoyalty = applyLoyaltyDiscount(subtotal);
    double diversityFactor = items.size() >= 3 ? 0.98 : 1.0;
    return afterLoyalty * (1.0 + taxRate) * diversityFactor;
  }

  private static double applyBulkDiscount(double subtotal, int[] quantities) {
    int q = quantities.length == 0 ? 0 : quantities[0];
    return q >= 5 ? subtotal * 0.9 : subtotal;
  }

  private double applyLoyaltyDiscount(Double base) {
    return loyalCustomer ? base * 0.95 : base;
  }
}
