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
 * Computes ASCII character sums for each word in a text and finds the word(s) with the highest sum.
 * Reports the maximum value, the words that achieve it, and a running total of words processed
 * across invocations.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * Input:  "a"
 * Output: Max:97 Words:[a] Total:1
 *
 * Input:  "hi there"
 * Output: Max:536 Words:[there] Total:2
 *
 * Input:  "dog god cat"
 * Output: Max:314 Words:[dog, god] Total:3
 * }</pre>
 */
public class WordStats {
  static final String DELIM = " ";
  static int globalTotal = 0;

  Map<String, Integer> sums = new HashMap<>();
  int maxSum = 0;

  WordStats(String text) {
    for (String w : text.split(DELIM)) {
      if (w.isEmpty()) continue;
      int sum = 0;
      for (char c : w.toCharArray()) sum += c;
      sums.put(w, sum);
      if (sum > maxSum) maxSum = sum;
    }
  }

  int getMax() {
    return maxSum;
  }

  static String report(int max, Map<String, Integer> sums) {
    globalTotal += sums.size();
    List<String> words = new ArrayList<>();
    for (Map.Entry<String, Integer> e : sums.entrySet())
      if (e.getValue() == max) words.add(e.getKey());
    return "Max:" + max + " Words:" + words + " Total:" + globalTotal;
  }

  public static void main(String[] args) {
    WordStats stats = new WordStats(args[0]);
    System.out.print(report(stats.getMax(), stats.sums));
  }
}
