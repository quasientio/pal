package net.ittera.pal.apps;

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
