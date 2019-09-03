package com.ittera.cometa.core.exec.java;

public abstract class AbstractMethodDispatcherTest extends AbstractDispatcherTest {

	public abstract void dispatch_noArgs_ok() throws Throwable;
	public abstract void dispatchIncoming_noArgs_ok() throws Exception;

	public abstract void dispatch_withArgs_ok() throws Throwable;
	public abstract void dispatchIncoming_withArgs_ok() throws Exception;

	public abstract void dispatch_withPrimitiveArgs_ok() throws Throwable;
	public abstract void dispatchIncoming_withPrimitiveArgs_ok() throws Exception;

//	public abstract void dispatch_withObjectRefArgs_ok(); TODO
	public abstract void dispatchIncoming_withObjectRefArgs_ok() throws Exception;

//	public abstract void dispatch_withNullArgs_ok(); TODO
	public abstract void dispatchIncoming_withNullArgs_ok() throws Exception;

	public abstract void dispatch_varargs_ok() throws Throwable;
	public abstract void dispatchIncoming_varargs_ok() throws Exception;

	public abstract void dispatch_throwsException_exceptionThrown() throws Throwable;
	public abstract void dispatchIncoming_throwsException_exceptionThrown() throws Exception;
}
