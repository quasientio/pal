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

/**
 * Analyzes text to count unique words. Takes a text string as input, splits it into words, and
 * reports how many distinct words appear.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * Input:  "hello"
 * Output: Unique words: 1
 *
 * Input:  "the cat in the hat"
 * Output: Unique words: 4
 *
 * Input:  "to be or not to be that is the question"
 * Output: Unique words: 8
 * }</pre>
 */
public class DataAnalyzer {
  private static int totalAnalyses = 0;
  private static final int MAX_INPUT_LENGTH = 1000;
  private String inputData;
  private HashMap<String, Integer> wordCounts;

  public DataAnalyzer(String data) {
    this.inputData = data;
    this.wordCounts = new HashMap<>();
    totalAnalyses++;
  }

  public void analyze() {
    String[] words = splitIntoWords(inputData);
    countWords(words);
    printSummary();
  }

  private String[] splitIntoWords(String text) {
    return text.split("\\s+");
  }

  private void countWords(String[] words) {
    for (String word : words) {
      wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
    }
  }

  private void printSummary() {
    ArrayList<String> uniqueWords = new ArrayList<>(wordCounts.keySet());
    System.out.println("Unique words: " + uniqueWords.size());
    System.out.println("Total analyses performed: " + totalAnalyses);
  }

  public static boolean validateInput(String input) {
    return input != null && input.length() <= MAX_INPUT_LENGTH;
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: java DataAnalyzer <text>");
      return;
    }

    String userInput = args[0];
    if (!validateInput(userInput)) {
      System.err.println("Input too long or null");
      return;
    }

    DataAnalyzer analyzer = new DataAnalyzer(userInput);
    analyzer.analyze();
  }
}
