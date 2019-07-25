package com.ittera.cometa.common.util;

/**
 * Some of these methods are inspired in commons-lang3 StringUtils class
 */
public class Strings {

	private static boolean isEmpty(String str) {
		return (str == null || str.length() == 0);
	}

	public static String capitalize(String str) {
		if (isEmpty(str)) {
			return str;
		}
		String rest = str.substring(1);
		return str.substring(0, 1).toUpperCase() + (isEmpty(rest) ? "" : rest.toLowerCase());
	}

	public static String stringBefore(String string, String sep) {
		if (isEmpty(string) || sep == null) {
			return string;
		}
		if (sep.isEmpty()) {
			return "";
		}
		return string.contains(sep) ? string.substring(0, string.indexOf(sep)) : string;
	}

	public static String stringAfter(String string, String sep) {
		if (isEmpty(string)) {
			return string;
		}
		if (sep.isEmpty()) {
			return "";
		}
		return string.contains(sep) ? string.substring(string.indexOf(sep) + sep.length()) : "";
	}

	public static String stringAfterLast(String string, String sep) {
		if (isEmpty(string)) {
			return string;
		}
		if (isEmpty(sep)) {
			return "";
		}
		return string.contains(sep) ? string.substring(string.lastIndexOf(sep) + sep.length()) : "";
	}
}
