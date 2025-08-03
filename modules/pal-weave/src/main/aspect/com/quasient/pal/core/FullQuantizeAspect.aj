/**
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.DispatchForwarder;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

aspect FullQuantizeAspect {
	//if false, no output at all
	private static final boolean verbose=false;

	//Exception softening of calls to DispatchForwarder
	declare soft: Throwable : call (Object DispatchForwarder.constructor(..));
	declare soft: Throwable : call (void DispatchForwarder.voidInstanceMethod(..));
	declare soft: Throwable : call (Object DispatchForwarder.nonVoidInstanceMethod(..));
	declare soft: Throwable : call (void DispatchForwarder.voidClassMethod(..));
	declare soft: Throwable : call (Object DispatchForwarder.nonVoidClassMethod(..));
	declare soft: Throwable : call (Object DispatchForwarder.getStatic(..));
	declare soft: Throwable : call (Object DispatchForwarder.getObject(..));
	declare soft: Throwable : call (void DispatchForwarder.putStatic(..));
	declare soft: Throwable : call (void DispatchForwarder.putField(..));

	/** POINTCUT DEFINITIONS **/

	pointcut allClasses(): !within(FullQuantizeAspect) && !within(is(EnumType));

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

		DispatchForwarder.voidInstanceMethod(
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

		DispatchForwarder.voidClassMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getArgs());
	}

	Object around(): nonVoidInstanceMethods() {
		if (verbose) {
			print(" D --> non-void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		return DispatchForwarder.nonVoidInstanceMethod(
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

		return DispatchForwarder.nonVoidClassMethod(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
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

		return DispatchForwarder.constructor(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getArgs());
	}

	/** ADVICE for Fields **/

	Object around(): staticGetfields() {
		if (verbose) {
			print(" D --> get static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		return DispatchForwarder.getStatic(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getArgs());
	}

	Object around(): nonStaticGetfields() {
		if (verbose) {
			print(" D --> get field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		return DispatchForwarder.getObject(
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

		DispatchForwarder.putStatic(
			Context.parseFrom(thisJoinPointStaticPart),
			thisJoinPoint.getThis(),
			thisJoinPoint.getArgs());
	}

	void around(): nonStaticPutfields() {
		if (verbose) {
			print(" D --> put field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		DispatchForwarder.putField(
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
