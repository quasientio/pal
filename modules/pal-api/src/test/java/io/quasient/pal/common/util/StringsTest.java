/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
