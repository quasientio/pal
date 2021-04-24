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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    LocalDate date = LocalDate.now();
    System.out.println(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
  }
}
