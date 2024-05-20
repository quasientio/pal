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

package net.ittera.pal.apps.rpc;

import java.util.List;

@SuppressWarnings("unused")
public class VoidStaticMethods {

  private static void testVoidStatic(String arg) {
    System.out.println(arg);
  }

  private static void printArg(int argIdx, String arg) {
    System.out.printf("Argument #%d to printArg: %s%n", argIdx, arg);
  }

  static void doSomethingStatically() {
    System.out.println("whatever");
  }

  static void throwRuntimeException() {
    throw new RuntimeException("Bastards threw me out!");
  }

  public static void sumUpList(List<Integer> listOfIntegers) {
    int sum = listOfIntegers.stream().reduce(0, Integer::sum);
    System.out.printf("The sum of integers = %d%n", sum);
  }

  public static void main(String[] args) {}
}
