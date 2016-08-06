package com.ittera.cometa.demos;

/**
 * Hello world!
 *
 */
public class App 
{

    Integer x=null;
    String s=null;
    App b=null;

    void doSomething() {
        x=Integer.valueOf(60);
        s=new String("hello there");
        b=new App();
    }

    Integer giveMeX() {
        return x;
    }

    private void test() {
        System.out.println("un test");
    }

    private void testArg(String arg) {
        System.out.println(arg);
    }

    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        App a=new App();
        a.test();
        a.doSomething();
        a.giveMeX();
        a.testArg("dummy val");
    }
}
