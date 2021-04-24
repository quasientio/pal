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

package net.ittera.pal.apps.rmi.explicit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NonVoidInstanceMethods {

  public final Integer anInt = 4;

  Integer giveMeX() {
    return anInt;
  }

  public List<String> getListOfStrings() {
    List<String> aList = new ArrayList<>();
    aList.add("hello");
    aList.add(" ");
    aList.add("world");
    aList.add("!");
    return aList;
  }

  public List<String> getListOfStringsShorthand() {
    return Arrays.asList("hello", " ", "world", "!");
  }

  protected Integer addOffsetToListAndSumUp(int offset, ArrayList<Integer> listOfInts) {
    if (listOfInts != null) {
      for (int i = 0; i < listOfInts.size(); i++) {
        listOfInts.set(i, listOfInts.get(i) + offset);
      }
    }

    return listOfInts.stream().reduce(0, Integer::sum);
  }

  public String throwMeACheckedException(long aLongValue) throws Exception {
    if (aLongValue > Integer.MAX_VALUE) {
      throw new Exception("long is really long!");
    }
    return "I'm fine";
  }
}
