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
package io.quasient.pal.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StringsTest {

  @Test
  public void stringBefore() {
    String str = "some rather long nonsensical-sentence-about nothing really";
    String sep = "-sentence-";
    assertEquals("some rather long nonsensical", Strings.stringBefore(str, sep));

    assertEquals(str, Strings.stringBefore(str, "notfound"));
    assertNull(Strings.stringBefore(null, sep));
    assertEquals("", Strings.stringBefore("", sep));
    assertEquals("", Strings.stringBefore("blah", ""));
    assertEquals("blah", Strings.stringBefore("blah", null));
  }

  @Test
  public void stringAfter() {
    String str = "some rather long nonsensical-sentence-about nothing really";
    String sep = "-sentence-";
    assertEquals("about nothing really", Strings.stringAfter(str, sep));

    assertEquals("", Strings.stringAfter(str, "notfound"));
    assertNull(Strings.stringAfter(null, sep));
    assertEquals("", Strings.stringAfter("", sep));
    assertEquals(str, Strings.stringAfter(str, null));
    assertEquals("", Strings.stringAfter(str, ""));
  }

  @Test
  public void stringAfterLast() {
    String str = "some rather long nonsensical--sentence--about nothing-- really";
    String sep = "--";

    assertEquals("", Strings.stringAfterLast(str, "notfound"));
    assertEquals(
        "",
        Strings.stringAfterLast("some rather long nonsensical--sentence--about nothing--", sep));
    assertEquals(" really", Strings.stringAfterLast(str, sep));
    assertEquals("", Strings.stringAfterLast("", sep));
    assertNull(Strings.stringAfterLast(null, sep));
    assertEquals(str, Strings.stringAfterLast(str, null));
    assertEquals("", Strings.stringAfterLast(str, ""));
  }
}
