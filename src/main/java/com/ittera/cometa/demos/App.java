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

   static void doSomethingStatically() {
        System.out.println("whatever");
        aClassString="I'm classy";
    }

    void doSomething() {
        anInt=Integer.valueOf(60);
        someString=new String("hello there");
        b=new App();
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

    public static void main( String[] args )
    {
       short times=2000;

       for (short i=0; i<times; i++) {
         System.out.println("Hello World!");
         App a = new App();
         a.test();
         a.doSomething();
         System.out.println("X = " + a.giveMeX());
         a.testArg("dummy val");
         doSomethingStatically();
         System.out.println("aBool is " + a.aBool);
         a.aBool=!a.aBool;
         System.out.println("aBool is now " + a.aBool);
         System.out.println("Getting someString field = " + a.someString);
         System.out.println("a class str = " + a.aClassString);
         System.out.println("aLong = " + a.aLong);
         a.aLong*=2;
         System.out.println("aLong (times 2) = " + a.aLong);
        }
    }
}
