package com.ittera.cometa.concentrator.exec.java;

public abstract class AbstractFieldOpDispatcherTest extends AbstractDispatcherTest {

	public abstract void dispatch_primitive_ok() throws Throwable;
	public abstract void dispatchIncoming_primitive_ok();

	public abstract void dispatch_primitiveArray_ok() throws Throwable;
	public abstract void dispatchIncoming_primitiveArray_ok();

	public abstract void dispatch_wrapper_ok() throws Throwable;
	public abstract void dispatchIncoming_wrapper_ok();

	public abstract void dispatch_string_ok() throws Throwable;
	public abstract void dispatchIncoming_string_ok();

	public abstract void dispatch_object_ok() throws Throwable;
	public abstract void dispatchIncoming_object_ok();

	public abstract void dispatch_nullObject_ok() throws Throwable;
	public abstract void dispatchIncoming_nullObject_ok();

	public abstract void dispatch_objectArray_ok() throws Throwable;
	public abstract void dispatchIncoming_objectArray_ok();

	public abstract void dispatch_throwable_ok() throws Throwable;
	public abstract void dispatchIncoming_throwable_ok();
}
