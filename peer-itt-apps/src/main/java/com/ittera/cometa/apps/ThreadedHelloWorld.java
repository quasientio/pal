package com.ittera.cometa.apps;

public class ThreadedHelloWorld {

	private static void sayHello() {
		System.out.println(String.format("Hello world! from thread %s", Thread.currentThread().getName()));
	}

	public static void main(String[] args) {

		// say hello from main thread
		sayHello();

		// say hello from new thread
		new Thread(ThreadedHelloWorld::sayHello).start();
	}
}
