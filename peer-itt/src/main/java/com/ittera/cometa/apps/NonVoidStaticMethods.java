package com.ittera.cometa.apps;

import java.util.ArrayList;

public class NonVoidStaticMethods {

	private static Thread singleton;

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

	static char[] toCharArray(String whateverString) {
		return whateverString.toCharArray();
	}

	public static Long[] giveMeAnEmptyLongArray() {
		return new Long[0];
	}

	public static Boolean[] giveMeANullBoolArray() {
		return (Boolean[]) null;
	}

	static Thread fetchMeAThreadSingleton() {
		if (singleton == null) {
			singleton = new Thread();
		}
		return singleton;
	}

	static Thread[] fetchMeAThreadArray() {
		int arraySize = 2;
		Thread[] threads = new Thread[arraySize];
		for (int i = 0; i < arraySize; i++) {
			threads[i] = new Thread();
		}

		return threads;
	}

}
