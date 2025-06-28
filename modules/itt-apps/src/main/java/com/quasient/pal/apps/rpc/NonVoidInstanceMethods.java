/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
