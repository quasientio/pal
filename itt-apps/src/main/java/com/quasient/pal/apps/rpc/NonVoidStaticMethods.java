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

package com.quasient.pal.apps.rpc;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class NonVoidStaticMethods {

  private static Thread singleton;

  public static Integer nonVoidSumUpList(List<Integer> listOfIntegers) {
    int sum = 0;
    if (listOfIntegers != null) {
      for (Integer anInt : listOfIntegers) {
        sum += anInt;
      }
    }
    return sum;
  }

  private static String testNonVoidStatic(String arg) {
    return arg.toLowerCase(Locale.getDefault());
  }

  protected static Integer highFive() {
    return 5;
  }

  public static Integer throwMeAnException() {
    throw new RuntimeException("Here you go");
  }

  public static Object giveMeNull() {
    return null;
  }

  static char[] toCharArray(String whateverString) {
    return whateverString.toCharArray();
  }

  public static Long[] giveMeAnEmptyLongArray() {
    return new Long[0];
  }

  public static Boolean[] giveMeNullBoolArray() {
    return null;
  }

  @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
  static Thread getThreadSingleton() {
    if (singleton == null) {
      singleton = new Thread();
    }
    return singleton;
  }

  @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
  static Thread[] getThreadArray() {
    int arraySize = 2;
    Thread[] threads = new Thread[arraySize];
    for (int i = 0; i < arraySize; i++) {
      threads[i] = new Thread();
    }

    return threads;
  }
}
