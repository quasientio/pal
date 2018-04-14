package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;

public interface Dispatcher {
	Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable;
}
