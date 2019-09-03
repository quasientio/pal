package com.ittera.cometa.core;

import org.zeromq.ZContext;

public abstract class ZmqEnabledTest {

	protected ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

}
