package com.ittera.cometa.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class StringsTest {

	@Test
	public void capitalize() {
		assertEquals("Capitalized", Strings.capitalize("capitalized"));
		assertEquals("Capitalized", Strings.capitalize("Capitalized"));
		assertEquals("Capitalized", Strings.capitalize("CAPITALIZED"));
		assertEquals("Capitalized", Strings.capitalize("cApItAlIzEd"));

		assertEquals("", Strings.capitalize(""));
		assertNull(Strings.capitalize(null));
	}

	@Test
	public void stringBefore() {
		String str = "some rather long nonsensical-sentence-about nothing really";
		String sep = "-sentence-";
		assertEquals("some rather long nonsensical", Strings.stringBefore(str, sep));

		assertEquals(str, Strings.stringBefore(str, "notfound"));
		assertEquals(null, Strings.stringBefore(null, sep));
		assertEquals("", Strings.stringBefore("", sep));
	}

	@Test
	public void stringAfter() {
		String str = "some rather long nonsensical-sentence-about nothing really";
		String sep = "-sentence-";
		assertEquals("about nothing really", Strings.stringAfter(str, sep));

		assertEquals("", Strings.stringAfter(str, "notfound"));
		assertEquals(null, Strings.stringAfter(null, sep));
		assertEquals("", Strings.stringAfter("", sep));
	}

	@Test
	public void stringAfterLast() {
		String str = "some rather long nonsensical--sentence--about nothing-- really";
		String sep = "--";

		assertEquals("", Strings.stringAfterLast(str, "notfound"));
		assertEquals("",
			Strings.stringAfterLast("some rather long nonsensical--sentence--about nothing--", sep));
		assertEquals(" really", Strings.stringAfterLast(str, sep));
		assertEquals("", Strings.stringAfterLast("", sep));
		assertEquals(null, Strings.stringAfterLast(null, sep));
	}
}