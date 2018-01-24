package com.ittera.cometa.apps;

public class Constructors {

	private Constructors innerInstance;

	public Constructors() {
	}

	public Constructors(Integer anInt) {
	}

	Constructors(String msg, Integer times) {

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) {
			sb.append(msg).append(",");
		}
	}

	private Constructors(String[] aStringArrayParam) {

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < aStringArrayParam.length; i++) {
			sb.append(aStringArrayParam[i]).append(",");
		}
	}

	protected Constructors(Constructors aConstructor) {
		this.innerInstance = aConstructor;
	}
}
