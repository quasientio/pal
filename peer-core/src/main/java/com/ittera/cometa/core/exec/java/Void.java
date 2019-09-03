package com.ittera.cometa.core.exec.java;

public class Void {

	private static final Void instance = new Void();

	private Void() {
	}

	static Void getInstance() {
		return instance;
	}
}
