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

package net.ittera.pal.apps;

import java.util.ArrayList;

public class NonVoidStaticMethods {

  private static Thread singleton;

  public static Integer nonVoidSumUpList(ArrayList<Integer> listOfInts) {
    int sum = 0;
    if (listOfInts != null) {
      for (Integer listOfInt : listOfInts) {
        sum += listOfInt;
      }
    }
    return sum;
  }

  private static String testNonVoidStatic(String arg) {
    return arg.toLowerCase();
  }

  protected static Integer highFive() {
    return 5;
  }

  public static Integer throwMeAnException() {
    throw new RuntimeException("Here you go");
  }

  public static Object giveMeANull() {
    return null;
  }

  static char[] toCharArray(String whateverString) {
    return whateverString.toCharArray();
  }

  public static Long[] giveMeAnEmptyLongArray() {
    return new Long[0];
  }

  public static Boolean[] giveMeANullBoolArray() {
    return null;
  }

  static Thread fetchMeAThreadSingleton() {
    if (singleton == null) {
      singleton = new Thread();
    }
    return singleton;
  }

  static Thread[] fetchMeAThreadArray() {
    int arraySize = 2;
    Thread[] threads = new Thread[arraySize];
    for (int i = 0; i < arraySize; i++) {
      threads[i] = new Thread();
    }

    return threads;
  }
}
