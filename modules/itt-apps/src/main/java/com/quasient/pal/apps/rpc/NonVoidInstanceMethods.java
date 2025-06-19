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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class NonVoidInstanceMethods {

  public final Integer anInt = 4;

  Integer giveMeX() {
    return anInt;
  }

  public List<String> getListOfStrings() {
    List<String> myList = new ArrayList<>();
    myList.add("hello");
    myList.add(" ");
    myList.add("world");
    myList.add("!");
    return myList;
  }

  public List<String> getListOfStringsShorthand() {
    return Arrays.asList("hello", " ", "world", "!");
  }

  protected Integer addOffsetToListAndSumUp(int offset, List<Integer> listOfIntegers) {
    if (listOfIntegers != null) {
      listOfIntegers.replaceAll(integer -> integer + offset);
    }
    if (listOfIntegers == null) {
      return 0;
    }
    return listOfIntegers.stream().reduce(0, Integer::sum);
  }

  public String throwsCheckedException(long someLongValue) throws Exception {
    if (someLongValue > Integer.MAX_VALUE) {
      throw new Exception("long is really long!");
    }
    return "I'm fine";
  }
}
