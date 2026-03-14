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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Calculates order totals with state-specific tax rates, percentage discounts, and additional fees.
 * Input is a pipe-delimited string: STATE|CUSTOMER|items|discount%|fees. Supported states: CA
 * (8.25%), NY (8.88%), TX (6.25%).
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * Input:  "TX|A|gum=1.00|0|"
 * Output: A total 1.06 USD (1 SKUs)
 *
 * Input:  "NY|Mia|tea=2.10,muffin=3.25|5|50"
 * Output: Mia total 5.58 USD (2 SKUs)
 *
 * Input:  "CA|Jordan|coffee=3.50,bagel=2.25,salad=8.99,coffee=3.50,cookie=1.75|12|0,25,199"
 * Output: Jordan total 21.51 USD (4 SKUs)
 * }</pre>
 */
public class OrderTotal {
  static int runs;
  static String currency = "usd";
  static final HashMap<String, Integer> TAX_BP = new HashMap<>();

  static {
    TAX_BP.put("CA", 825);
    TAX_BP.put("NY", 888);
    TAX_BP.put("TX", 625);
  }

  final String customer;
  final ArrayList<Integer> lineCents = new ArrayList<>();
  final HashMap<String, Integer> qtyByItem = new HashMap<>();

  public OrderTotal(String customer) {
    this.customer = customer;
  }

  public void addLine(String item, int cents) {
    lineCents.add(cents);
    qtyByItem.put(item, qtyByItem.getOrDefault(item, 0) + 1);
  }

  int subtotalCents() {
    int s = 0;
    for (int c : lineCents) s += c;
    return s;
  }

  long finalTotalCents(String state, Integer discountPercent, int[] feesCents) {
    long sub = subtotalCents();
    for (int f : feesCents) sub += f;
    sub -= sub * discountPercent / 100;
    int bp = TAX_BP.getOrDefault(state, 0);
    return Math.round(sub * (10000 + bp) / 10000.0);
  }

  static int cents(String price) {
    return (int) Math.round(Double.parseDouble(price) * 100);
  }

  static int[] fees(String csv) {
    if (csv == null || csv.isEmpty()) return new int[0];
    String[] p = csv.split(",");
    int[] a = new int[p.length];
    for (int i = 0; i < p.length; i++) a[i] = Integer.parseInt(p[i]);
    return a;
  }

  static String money(long cents) {
    return String.format(Locale.US, "%.2f", cents / 100.0);
  }

  public static void main(String[] args) {
    runs = runs + 1;
    currency = currency.toUpperCase(Locale.ROOT);
    String[] parts = args[0].split("\\|", -1);

    OrderTotal o = new OrderTotal(parts[1]);
    for (String it : parts[2].split(",")) {
      String[] kv = it.split("=");
      o.addLine(kv[0], cents(kv[1]));
    }

    long total =
        o.finalTotalCents(
            parts[0], Integer.valueOf(parts[3]), fees(parts.length > 4 ? parts[4] : ""));
    System.out.println(
        o.customer
            + " total "
            + money(total)
            + " "
            + currency
            + " ("
            + o.qtyByItem.size()
            + " SKUs, run "
            + runs
            + ")");
  }
}
