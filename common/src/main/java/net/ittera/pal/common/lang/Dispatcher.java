package net.ittera.pal.common.lang;

public interface Dispatcher {

  Object dispatch(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;
}
