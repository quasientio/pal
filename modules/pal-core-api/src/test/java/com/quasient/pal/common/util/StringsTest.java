/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.common.util;

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
