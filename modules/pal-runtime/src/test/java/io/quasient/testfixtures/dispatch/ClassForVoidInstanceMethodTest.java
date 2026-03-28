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
