package com.ittera.cometa.apps;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.lang3.time.DatePrinter;


/**
 * NOTE THAT UNIT TESTS in com.ittera.cometa.concentrator.messages.incoming are dependant on this class and
 * some tests have hard-coded some of the values in this class, so corresponding tests must be KEPT IN SYNC
 * when applying changes here.
 */
public class App {

    public Integer anInt = 4;
    Integer anotherInt = 1;
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
    private static App aNestedApp;

    private static String[] aNullStringArray;
    private static String[] anEmptyStringArray = {};
    private static String[] aStringArray = {"hello", "world", "!"};

    static {
        System.out.println("aDbl before=" + aDbl);
        aDbl = 5;
        System.out.println("aDbl after=" + aDbl);
        aClassString = "I'm classy";
        aNestedApp = new App();
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

    //CONSTRUCTORS
    public App() {
        System.out.println("Hi from the default constructor :)");
        anIntList = new ArrayList();
        for (int i = 0; i < 5; i++) {
            anIntList.add(i * i);
        }
    }

    public App(App anotherApp) {
        this.anApp = anotherApp;
    }

    public App(Integer anInt) {
        this.aNullInt = anInt;
    }

    private App(String[] aStringArrayParam) {
        for (int i = 0; i < aStringArrayParam.length; i++) {
            System.out.println(String.format("Parameter #%d : %s", i, aStringArrayParam[i]));
        }
    }

    public App(String msg, Integer times) {
        for (int i = 0; i < times; i++) {
            System.out.println(String.format("Printing a msg for the %dth time: %s", i, msg));
        }
    }

    //METHODS
    void doSomething() {
        anInt = Integer.valueOf(60);
        anApp = new App();
    }


    Integer giveMeX() {
        return anInt;
    }

    private void test() {
        System.out.println("un test");
    }

    protected void printDate() {
        DatePrinter datePrinter = FastDateFormat.getInstance("yyyy-MM-dd");
        System.out.println(datePrinter.format(System.currentTimeMillis()));
    }

    private void testArg(String arg) {
        System.out.println(arg);
    }

    private static void printArg(int argIdx, String arg) {
        System.out.println(String.format("Argument #%d to printArg: %s", argIdx, arg));
    }

    static void doSomethingStatically() {
        System.out.println("whatever");
    }

    private static void testVoidStatic(String arg) {
        System.out.println(arg);
    }

    public static void sumUpList(ArrayList<Integer> listOfInts) {
        System.out.println(String.format("The sum of ints = %d", nonVoidSumUpList(listOfInts)));
    }

    protected Integer addOffsetToListAndSumUp(int offset, ArrayList<Integer> listOfInts) {
        if (listOfInts != null) {
            for (int i = 0; i < listOfInts.size(); i++) {
                listOfInts.set(i, listOfInts.get(i) + offset);
            }
        }
        return nonVoidSumUpList(listOfInts);
    }

    public List<String> getListOfStrings() {
        List<String> aList = new ArrayList<String>();
        aList.add("hello");
        aList.add(" ");
        aList.add("world");
        aList.add("!");
        return aList;
    }

    public List<String> getListOfStringsShorthand() {
        List<String> aList = Arrays.asList("hello", " ", "world", "!");
        return aList;
    }

    public static Integer nonVoidSumUpList(ArrayList<Integer> listOfInts) {
        int sum = 0;
        if (listOfInts != null) {
            for (int i = 0; i < listOfInts.size(); i++) {
                sum += listOfInts.get(i);
            }
        }
        return sum;
    }

    private static String testNonVoidStatic(String arg) {
        return arg.toLowerCase();
    }

    protected static Integer highFive() {
        return 5;
    }

    public static Object giveMeANull() {
        return null;
    }

    public static Long[] giveMeAnEmptyLongArray() {
        return new Long[0];
    }

    public static Boolean[] giveMeANullBoolArray() {
        return (Boolean[]) null;
    }

    static char[] toCharArray(String whateverString) {
        return whateverString.toCharArray();
    }

    static App fetchMeAnApp() {
        return aNestedApp;
    }

    static App[] fetchMeAnAppArray() {
        int arraySize = 8;
        App[] apps = new App[arraySize];
        for (int i = 0; i < arraySize; i++) {
            apps[i] = new App();
        }

        return apps;
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
            ArrayList<Integer> intList = new ArrayList();
            intList.addAll(Arrays.asList(new Integer[]{1392, 44, 539}));
            sumUpList(intList);
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
