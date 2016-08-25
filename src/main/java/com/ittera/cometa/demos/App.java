package com.ittera.cometa.demos;

/**
 * Hello world!
 *
 */
public class App 
{

    Integer anInt=null;
    String someString=null;
    boolean aBool = false;
    App b=null;
    static float aFloat = 8.5f;
    static float aLong = 53382303;
    static String aClassString=null;
    static double aDbl=0d;

    static {
      System.out.println("aDbl before="+aDbl);
      aDbl=5;
      System.out.println("aDbl after="+aDbl);
    }

   static void doSomethingStatically() {
        System.out.println("whatever");
        aClassString="I'm classy";
    }

    void doSomething() {
        anInt=Integer.valueOf(60);
        someString=new String("hello there");
        b=new App();
    }


    public static class MyInnerClass {
        static int anInt;
        static {
            anInt = 5;
            System.out.println("printing from <clinit>");
        }
       MyInnerClass() {
           System.out.println("printing an int from <init> : "+anInt);
       }
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

    private static void testVoidStatic(String arg) {
        System.out.println(arg);
    }

    private static String testNonVoidStatic(String arg) {
        return arg.toLowerCase();
    }

    public static void main( String[] args )
    {
       short times=1;

       for (short i=0; i<times; i++) {
         System.out.println("Hello World!");
         App a = new App();
         a.test();
         a.doSomething();
         System.out.println("X = " + a.giveMeX());
         //string > 100 chars to test trimming
         a.testArg("averylongsaverylongsaverylongsaverylongsaverylongsaverylongsaverylongstring");
         a.someString=new StringBuffer("01234567890123456789012345678901234567890123456789").append("01234567890123456789012345678901234567890123456789xxx").toString();
         doSomethingStatically();
         System.out.println("aBool is " + a.aBool);
         a.aBool=!a.aBool;
         System.out.println("aBool is now " + a.aBool);
         System.out.println("Getting someString field = " + a.someString);
         System.out.println("a class str = " + a.aClassString);
         System.out.println("aLong = " + a.aLong);
         a.aLong*=2;
         System.out.println("aLong (times 2) = " + a.aLong);
         testVoidStatic("a otra cosa");
         testNonVoidStatic("mariposa");
         //test static constructor and instance constructor
         new MyInnerClass();
        }
    }
}
