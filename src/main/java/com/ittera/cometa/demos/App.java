package com.ittera.cometa.demos;

import java.util.List;
import java.util.ArrayList;

/**
 * NOTE THAT UNIT TESTS in com.ittera.cometa.concentrator.messages.incoming are dependant on this class and
 * some tests have hard-coded some of the values in this class, so corresponding tests must be KEPT IN SYNC
 * when applying changes here.
 */
public class App {

  public Integer anInt = 4;
  private Integer aNullInt;
  public String aNullStr;
  protected String someString = "I'm blank";
  public boolean aBool = true;
  public App anApp;
  public List anIntList;
  Boolean aNullBool;
  private final short someShort = 233;

  public static float aFloat = 8.5f;
  public static long aLong = 53382303;
  public static String aClassString;
  private static Integer aPrivateClassInt = 39328;
  protected static Boolean aProtectedBool;
  static boolean aPackageVisibleBool = true;
  public static double aDbl;
  public static String aNullStaticStr;
  static int aStaticInteger = 3000;

  private static String[] aNullStringArray;
  private static String[] anEmptyStringArray = {};
  private static String[] aStringArray = {"hello", "world", "!"};

  static {
    System.out.println("aDbl before=" + aDbl);
    aDbl = 5;
    System.out.println("aDbl after=" + aDbl);
    aClassString = "I'm classy";
  }

  static void doSomethingStatically() {
    System.out.println("whatever");
  }


  public static class MyInnerClass {
    static int anInt;

    static {
      anInt = 5;
      System.out.println("printing from <clinit>");
    }

    MyInnerClass() {
      System.out.println("printing an int from <init> : " + anInt);
    }
  }

  public App() {
    System.out.println("Hi from the default constructor :)");
    anIntList = new ArrayList();
    for (int i = 0; i < 5; i++) {
      anIntList.add(i * i);
    }
  }

  void doSomething() {
    anInt = Integer.valueOf(60);
    someString = new String("hello there");
    anApp = new App();
  }


  Integer giveMeX() {
    return anInt;
  }

  private void test() {
    System.out.println("un test");
  }

  private void testArg(String arg) {
    System.out.println(arg);
  }

  private static void printArg(int argIdx, String arg) {
    System.out.println(String.format("Argument #%d to main: %s", argIdx, arg));
  }

  private static void testVoidStatic(String arg) {
    System.out.println(arg);
  }

  private static String testNonVoidStatic(String arg) {
    return arg.toLowerCase();
  }

  public static void main(String[] args) {
    if (args.length > 0) {
      for (int i = 0; i < args.length; i++) {
        printArg(i, args[i]);
      }
    }

    short times = 1;
    for (short i = 0; i < times; i++) {
      System.out.println("Hello World!");
      App a = new App();
      a.test();
      a.doSomething();
      System.out.println("X = " + a.giveMeX());
      //string > 100 chars to test trimming
      a.testArg("averylongsaverylongsaverylongsaverylongsaverylongsaverylongsaverylongstring");
      a.someString = new StringBuffer("01234567890123456789012345678901234567890123456789").append("01234567890123456789012345678901234567890123456789xxx").toString();
      doSomethingStatically();
      System.out.println("aBool is " + a.aBool);
      a.aBool = !a.aBool;
      System.out.println("aBool is now " + a.aBool);
      System.out.println("Getting someString field = " + a.someString);
      System.out.println("a class str = " + aClassString);
      System.out.println("aLong = " + aLong);
      aLong *= 2;
      System.out.println("aLong (times 2) = " + aLong);
      testVoidStatic("a otra cosa");
      testNonVoidStatic("mariposa");
      //test static constructor and instance constructor
      new MyInnerClass();
    }
  }
}
