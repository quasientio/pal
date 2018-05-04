package com.ittera.cometa.concentrator.exec.java;

public abstract class AbstractMethodDispatcherTest extends AbstractDispatcherTest {

	public abstract void dispatch_noArgs_ok() throws Throwable;
	public abstract void dispatchIncoming_noArgs_ok();

	public abstract void dispatch_withArgs_ok() throws Throwable;
	public abstract void dispatchIncoming_withArgs_ok();
	public abstract void dispatchIncoming_withObjectRefArgs_ok();
	public abstract void dispatchIncoming_withNullArgs_ok();

	public abstract void dispatch_varargs_ok() throws Throwable;
	public abstract void dispatchIncoming_varargs_ok();

	public abstract void dispatch_throwsException_exceptionThrown() throws Throwable;
	public abstract void dispatchIncoming_throwsException_exceptionThrown();
}
