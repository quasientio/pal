package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AspectProxyDispatcher {

	// constructor & method dispatchers
	@Inject
	private static ConstructorDispatcher constructorDispatcher;
	@Inject
	private static ClassMethodDispatcher classMethodDispatcher;
	@Inject
	private static InstanceMethodDispatcher instanceMethodDispatcher;

	// fieldop dispatchers
	@Inject
	private static GetClassVariableDispatcher getClassVariableDispatcher;
	@Inject
	private static SetClassVariableDispatcher setClassVariableDispatcher;
	@Inject
	private static GetInstanceVariableDispatcher getInstanceVariableDispatcher;
	@Inject
	private static SetInstanceVariableDispatcher setInstanceVariableDispatcher;


	public static Object constructor(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return constructorDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		classMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {
		return instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return classMethodDispatcher.dispatch(ctxt, sender, target, args);
	}


	public static Object getStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return getClassVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static Object getObject(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return getInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static void putStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		setClassVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public static void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		setInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
	}
}
