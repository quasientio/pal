package com.ittera.cometa.apps;

import java.util.ArrayList;

public class StaticMethods {

	public static Integer nonVoidSumUpList(ArrayList<Integer> listOfInts) {
		int sum = 0;
		if (listOfInts != null) {
			for (int i = 0; i < listOfInts.size(); i++) {
				sum += listOfInts.get(i);
			}
		}
		return sum;
	}
}
