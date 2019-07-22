package com.ittera.cometa.common.lang;

public interface Dispatcher {

	Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable;
}
