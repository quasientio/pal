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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("unused")
public class VoidInstanceMethods {

  void doSomething() {
    int chars = 19;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < chars; i++) {
      sb.append(i);
    }
    if (sb.toString().length() != 28) {
      throw new RuntimeException("OMG not 28?!!");
    }
  }

  private void testArg(String arg) {
    System.out.println(arg);
  }

  private void testNonNullArg(String arg) {
    System.out.println(arg.concat("and stuff"));
  }

  protected void printDate() {
    LocalDate date = LocalDate.now(ZoneId.of("UTC"));
    System.out.println(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
  }
}
