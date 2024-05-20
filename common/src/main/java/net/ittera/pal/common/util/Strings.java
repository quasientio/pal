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

package net.ittera.pal.common.util;

/** Some of these methods are inspired in commons-lang3 StringUtils class. */
public final class Strings {

  private Strings() {}

  private static boolean isEmpty(String str) {
    return (str == null || str.isEmpty());
  }

  public static String stringBefore(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (sep.isEmpty()) {
      return "";
    }
    return string.contains(sep) ? string.substring(0, string.indexOf(sep)) : string;
  }

  public static String stringAfter(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (sep.isEmpty()) {
      return "";
    }
    return string.contains(sep) ? string.substring(string.indexOf(sep) + sep.length()) : "";
  }

  public static String stringAfterLast(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (isEmpty(sep)) {
      return "";
    }
    return string.contains(sep) ? string.substring(string.lastIndexOf(sep) + sep.length()) : "";
  }
}
