package com.ittera.cometa.apps;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.lang3.time.DatePrinter;

public class VoidInstanceMethods {

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

	protected void printDate() {
		DatePrinter datePrinter = FastDateFormat.getInstance("yyyy-MM-dd");
		System.out.println(datePrinter.format(System.currentTimeMillis()));
	}
}
