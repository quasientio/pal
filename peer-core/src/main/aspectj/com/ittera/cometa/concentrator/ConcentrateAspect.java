package com.ittera.cometa.concentrator;

import com.ittera.cometa.common.lang.Context;

import com.ittera.cometa.concentrator.exec.java.AspectProxyDispatcher;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

aspect ConcentrateAspect {
	//if false, no output at all
	private static final boolean verbose=false;

	//Exception softening of calls to AspectProxyDispatcher
	declare soft: Throwable : call (Object AspectProxyDispatcher.constructor(..));
	declare soft: Throwable : call (void AspectProxyDispatcher.voidInstanceMethod(..));
	declare soft: Throwable : call (Object AspectProxyDispatcher.nonVoidInstanceMethod(..));
	declare soft: Throwable : call (void AspectProxyDispatcher.voidClassMethod(..));
	declare soft: Throwable : call (Object AspectProxyDispatcher.nonVoidClassMethod(..));
	declare soft: Throwable : call (Object AspectProxyDispatcher.getStatic(..));
	declare soft: Throwable : call (Object AspectProxyDispatcher.getObject(..));
	declare soft: Throwable : call (void AspectProxyDispatcher.putStatic(..));
	declare soft: Throwable : call (void AspectProxyDispatcher.putField(..));

	/** POINTCUT DEFINITIONS **/

	pointcut allClasses(): !within(ConcentrateAspect);

	pointcut voidInstanceMethods(): allClasses() && call(!static void *(..));

	pointcut voidClassMethods(): allClasses() && call(static void *(..));

	pointcut nonVoidInstanceMethods(): allClasses() && call(!static !void *(..));

	pointcut nonVoidClassMethods(): allClasses() && call(static !void *(..));

	pointcut constructors(): allClasses() && call(new(..));

	pointcut staticGetfields(): allClasses() && get(static * *);

	pointcut nonStaticGetfields(): allClasses() && get(!static * *);

	pointcut staticPutfields(): allClasses() && set(static * *);

	pointcut nonStaticPutfields(): allClasses() && set(!static * *);

	/** ADVICE for Methods **/

	void around(): voidInstanceMethods() {
		if (verbose) {
			print(" D --> void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		AspectProxyDispatcher.voidInstanceMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	void around(): voidClassMethods() {
		if (verbose) {
			print(" D --> void class method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		AspectProxyDispatcher.voidClassMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	Object around(): nonVoidInstanceMethods() {
		if (verbose) {
			print(" D --> non-void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		return AspectProxyDispatcher.nonVoidInstanceMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	Object around(): nonVoidClassMethods() {
		if (verbose) {
			print(" D --> non-void class method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		return AspectProxyDispatcher.nonVoidClassMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	/** ADVICE for Constructors **/

	Object around(): constructors() {
		if (verbose) {
			print(" D --> constructor: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		return AspectProxyDispatcher.constructor(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	/** ADVICE for Fields **/

	Object around(): staticGetfields() {
		if (verbose) {
			print(" D --> get static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		return AspectProxyDispatcher.getStatic(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	Object around(): nonStaticGetfields() {
		if (verbose) {
			print(" D --> get field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		return AspectProxyDispatcher.getObject(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	void around(): staticPutfields() {
		if (verbose) {
			print(" D --> put static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		AspectProxyDispatcher.putStatic(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}

	void around(): nonStaticPutfields() {
		if (verbose) {
			print(" D --> put field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		AspectProxyDispatcher.putField(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getTarget(),
			thisJoinPoint.getArgs());
	}


	/** Utility methods **/

	static final void print(String s) {
		System.out.println(s);
	}

	static private void printStaticCtxt(JoinPoint.StaticPart jpsp) {
		print(" ... jp.id="+jpsp.getId());
		print(" ... jp.kind="+jpsp.getKind());
		print(" ... jp.signature="+jpsp.getSignature().toShortString());
		print(" ... jp.source="+jpsp.getSourceLocation());
		print(" ... jp.toLongString="+jpsp.toLongString());
	}

	static private void printNonStaticCtxt(JoinPoint jp) {
		print(" --- target object="+jp.getTarget());
		print(" --- this="+jp.getThis());
	}

	static private void printParameters(JoinPoint jp) {
		Object[] args = jp.getArgs();
		String[] names = ((CodeSignature)jp.getSignature()).getParameterNames();
		Class[] types = ((CodeSignature)jp.getSignature()).getParameterTypes();
		if (args.length>0) {
			print(" --- Arguments: " );
		}
		for (int i = 0; i < args.length; i++) {
			print(" ---   "  +i+ ". " +names[i]+ " : " +types[i].getName()+ " = " +args[i]);
		}
	}
}
