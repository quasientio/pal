package com.ittera.cometa.concentrator.exec.java;

public class Void {

	private static Void instance = new Void();

	private Void() {
	}

	static Void getInstance() {
		return instance;
	}
}
