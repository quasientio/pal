package net.ittera.pal.common.lang;

public interface ProxyDispatcher {
  Object constructor(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  Object getStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  Object getObject(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  void putStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;
}
