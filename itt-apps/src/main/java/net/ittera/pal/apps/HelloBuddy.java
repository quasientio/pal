package net.ittera.pal.apps;

public class HelloBuddy {
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("Please provide your name, buddy.");
      System.exit(1);
    }
    String buddy = args[0];
    System.out.println(String.format("Hello %s!", buddy));
  }
}
