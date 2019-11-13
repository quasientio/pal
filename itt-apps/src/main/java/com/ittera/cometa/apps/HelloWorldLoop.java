package com.ittera.cometa.apps;

public class HelloWorldLoop {
  public static void main(String[] args) throws InterruptedException {
    while (true) {
      System.out.println("Hello world!");
      Thread.sleep(250);
    }
  }
}
