package net.ittera.pal.apps;

import java.util.ArrayList;

public class VoidStaticMethods {

  private static void testVoidStatic(String arg) {
    System.out.println(arg);
  }

  private static void printArg(int argIdx, String arg) {
    System.out.println(String.format("Argument #%d to printArg: %s", argIdx, arg));
  }

  static void doSomethingStatically() {
    System.out.println("whatever");
  }

  static void throwRuntimeException() {
    throw new RuntimeException("Bastards threw me out!");
  }

  public static void sumUpList(ArrayList<Integer> listOfInts) {
    int sum = listOfInts.stream().reduce(0, Integer::sum);
    System.out.println(String.format("The sum of ints = %d", sum));
  }

  public static void main(String[] args) {}
}
