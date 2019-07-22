package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ProxyDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AspectProxyDispatcher implements ProxyDispatcher {

	// constructor & method dispatchers
	@Inject
	private ConstructorDispatcher constructorDispatcher;
	@Inject
	private ClassMethodDispatcher classMethodDispatcher;
	@Inject
	private InstanceMethodDispatcher instanceMethodDispatcher;

	// fieldop dispatchers
	@Inject
	private GetClassVariableDispatcher getClassVariableDispatcher;
	@Inject
	private SetClassVariableDispatcher setClassVariableDispatcher;
	@Inject
	private GetInstanceVariableDispatcher getInstanceVariableDispatcher;
	@Inject
	private SetInstanceVariableDispatcher setInstanceVariableDispatcher;


	public Object constructor(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return constructorDispatcher.dispatch(ctxt, sender, target, args);
	}

	public void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		classMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {
		return instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return classMethodDispatcher.dispatch(ctxt, sender, target, args);
	}

	public Object getStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return getClassVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public Object getObject(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		return getInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public void putStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		setClassVariableDispatcher.dispatch(ctxt, sender, target, args);
	}

	public void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
		setInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
	}
}
