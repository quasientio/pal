package com.ittera.cometa.common.lang;

import javax.inject.Inject;

/**
 * This class decouples the ConcentrateAspect from the dispatch calls in AspectProxyDispatcher, by means of the
 * ProxyDispatcher interface, so that the aspect (and the peer-aj module) is not directly dependent on the *Dispatcher
 * classes (in the core module), imported by AspectProxyDispatcher. Therefore, peer-aj only needs to import peer-common.
 * This avoids a circular dependency at runtime, where peer-core depends on peer-aj (to classload the aspect)
 */
public class DispatchForwarder {

	@Inject
	private static ProxyDispatcher dispatcher;

	public static Object constructor(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return dispatcher.constructor(ctxt, sender, target, args);
	}

	public static void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		dispatcher.voidInstanceMethod(ctxt, sender, target, args);
	}

	public static void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		dispatcher.voidClassMethod(ctxt, sender, target, args);
	}

	public static Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {
		return dispatcher.nonVoidInstanceMethod(ctxt, sender, target, args);
	}

	public static Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return dispatcher.nonVoidClassMethod(ctxt, sender, target, args);
	}

	public static Object getStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return dispatcher.getStatic(ctxt, sender, target, args);
	}

	public static Object getObject(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return dispatcher.getObject(ctxt, sender, target, args);
	}

	public static void putStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		dispatcher.putStatic(ctxt, sender, target, args);
	}

	public static void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		dispatcher.putField(ctxt, sender, target, args);
	}
}
