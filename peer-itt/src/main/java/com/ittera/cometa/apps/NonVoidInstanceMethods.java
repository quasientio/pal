package com.ittera.cometa.apps;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class NonVoidInstanceMethods {

	public Integer anInt = 4;

	Integer giveMeX() {
		return anInt;
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

	protected Integer addOffsetToListAndSumUp(int offset, ArrayList<Integer> listOfInts) {
		if (listOfInts != null) {
			for (int i = 0; i < listOfInts.size(); i++) {
				listOfInts.set(i, listOfInts.get(i) + offset);
			}
		}

		int sum = listOfInts.stream().reduce(0, Integer::sum);
		return sum;
	}
}
