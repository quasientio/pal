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
