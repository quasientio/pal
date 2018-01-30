package com.ittera.cometa.apps;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class NonVoidInstanceMethods {

	public final Integer anInt = 4;

	Integer giveMeX() {
		return anInt;
	}

	public List<String> getListOfStrings() {
		List<String> aList = new ArrayList<>();
		aList.add("hello");
		aList.add(" ");
		aList.add("world");
		aList.add("!");
		return aList;
	}

	public List<String> getListOfStringsShorthand() {
		return Arrays.asList("hello", " ", "world", "!");
	}

	protected Integer addOffsetToListAndSumUp(int offset, ArrayList<Integer> listOfInts) {
		if (listOfInts != null) {
			for (int i = 0; i < listOfInts.size(); i++) {
				listOfInts.set(i, listOfInts.get(i) + offset);
			}
		}

		return listOfInts.stream().reduce(0, Integer::sum);
	}
}
