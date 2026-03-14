/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.testfixtures.dispatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test fixture class for void instance method dispatcher tests. */
@SuppressWarnings("unused")
public class ClassForVoidInstanceMethodTest {
  public List<String> wordsCollected = new ArrayList<>();
  private static final String WORD_REGEX = "^\\w+$";

  public ClassForVoidInstanceMethodTest() {}

  public void addHelloWorld() {
    wordsCollected.add("Hello");
    wordsCollected.add("World");
  }

  public void addWord(String word) {
    if (word == null) {
      return;
    }

    if (word.matches(WORD_REGEX)) {
      wordsCollected.add(word);
    } else {
      throw new IllegalArgumentException("Not a word: " + word);
    }
  }

  public void addWords(int n) {
    for (int i = 0; i < n; i++) {
      addWord("again");
    }
  }

  public void addWords(String... words) {
    Arrays.stream(words).filter(w -> w.matches(WORD_REGEX)).forEach(w -> wordsCollected.add(w));
  }

  public void addWordList(List<String> wordList) {
    wordsCollected.addAll(wordList);
  }
}
