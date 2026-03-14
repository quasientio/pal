/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.quantized.rpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
@SuppressFBWarnings(
    value = {
      "CT_CONSTRUCTOR_THROW",
      "PZLA_PREFER_ZERO_LENGTH_ARRAYS",
      "UPM_UNCALLED_PRIVATE_METHOD"
    },
    justification = "Test app - null array return and uncalled methods intentional for testing")
public class Methods {

  public final Integer anInt = 4;
  private static Thread singleton;

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

  public static Integer nonVoidSumUpList(List<Integer> listOfIntegers) {
    int sum = 0;
    if (listOfIntegers != null) {
      for (Integer anInt : listOfIntegers) {
        sum += anInt;
      }
    }
    return sum;
  }

  public static Float nonVoidSumUpMap(Map<String, Float> mapOfFloats) {
    float sum = 0;
    if (mapOfFloats != null) {
      for (Float f : mapOfFloats.values()) {
        sum += f;
      }
    }
    return sum;
  }

  private static String testNonVoidStatic(String arg) {
    return arg.toLowerCase(Locale.getDefault());
  }

  public static String staticStringWithStringArg(String input) {
    return "RESULT: " + input;
  }

  public static String staticStringWithStringArgs(String[] input) {
    return "RESULT: " + String.join(",", input);
  }

  /**
   * Test method that takes String[] parameter (like main).
   *
   * <p>Used to verify that StaticMethodCallBuilder works with non-main methods that have String[]
   * signature.
   *
   * @param args array of string arguments
   * @return concatenated string of all arguments
   */
  public static String processArgs(String[] args) {
    if (args == null || args.length == 0) {
      return "NO_ARGS";
    }
    return "PROCESSED: " + String.join(",", args);
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

  public static void main(String[] args) {
    // Create instance and call some random methods
    Methods m = new Methods();

    // Test instance methods
    m.giveMeX();
    m.getListOfStrings();
    m.getListOfStringsShorthand();
    m.doSomething();
    m.printDate();

    // Test instance method with parameters
    List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
    m.addOffsetToListAndSumUp(10, numbers);

    // Test static methods
    doSomethingStatically();
    highFive();
    sumUpList(Arrays.asList(10, 20, 30));
    nonVoidSumUpList(Arrays.asList(5, 15, 25));

    // Test field access
    Integer x = m.anInt;

    // Test array methods
    toCharArray("test");
    giveMeAnEmptyLongArray();
    getThreadArray();
  }
}
