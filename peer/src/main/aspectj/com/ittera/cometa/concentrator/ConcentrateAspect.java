package com.ittera.cometa.concentrator;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

aspect ConcentrateAspect {
	//if false, no output at all
	private static final boolean verbose=false;

	//Exception softening of calls to Concentrator
	declare soft: ClassNotFoundException : call (boolean Concentrator.classConstructor(..));
	declare soft: Throwable : call (Object Concentrator.constructor(..));
	declare soft: Throwable : call (void Concentrator.voidInstanceMethod(..));
	declare soft: Throwable : call (Object Concentrator.nonVoidInstanceMethod(..));
	declare soft: Throwable : call (void Concentrator.voidClassMethod(..));
	declare soft: Throwable : call (Object Concentrator.nonVoidClassMethod(..));
	declare soft: IllegalAccessException : call (Object Concentrator.getStatic(..));
	declare soft: IllegalAccessException : call (Object Concentrator.getObject(..));
	declare soft: IllegalAccessException : call (void Concentrator.putStatic(..));
	declare soft: IllegalAccessException : call (void Concentrator.putField(..));

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

	pointcut staticConstructors(): allClasses() && staticinitialization(*);


	/** ADVICE for Methods **/

	void around(): voidInstanceMethods() {	
		if (verbose) {
			print(" D --> void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Concentrator.voidInstanceMethod(thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget(), //Object receiver
			thisJoinPoint.getArgs()); //parameters
	}

	void around(): voidClassMethods() {	
		if (verbose) {
			print(" D --> void class method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Concentrator.voidClassMethod(thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters
	}

	Object around(): nonVoidInstanceMethods() {	
		if (verbose) {
			print(" D --> non-void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Object returnedValue = Concentrator.nonVoidInstanceMethod(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget(), //Object receiver
			thisJoinPoint.getArgs()); //parameters

		return returnedValue;
	}

	Object around(): nonVoidClassMethods() {	
		if (verbose) {
			print(" D --> non-void class method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Object returnedValue = Concentrator.nonVoidClassMethod(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters

		return returnedValue;
	}

	/** ADVICE for Constructors **/

	Object around(): constructors() {	
		if (verbose) {
			print(" D --> constructor: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Object returnedValue = Concentrator.constructor(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters

		return returnedValue;
	}

	/** ADVICE for Initializers (ie. class constructors) **/

	/**
	void around(): staticConstructors() {
		if (verbose) {
			print(" D --> static constructor: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		boolean classLoaded = Concentrator.classConstructor(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis()); //Object sender

		if (!classLoaded) {
			proceed();
		}
	}*


	/** ADVICE for Fields **/

	Object around(): staticGetfields() {	
		if (verbose) {
			print(" D --> get static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}
		Object returnedValue = Concentrator.getStatic(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis()); //Object sender

		return returnedValue;
	}

	Object around(): nonStaticGetfields() {	
		if (verbose) {
			print(" D --> get field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}
		Object returnedValue = Concentrator.getObject(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget()); //Object receiver

		return returnedValue;
	}

	void around(): staticPutfields() {	
		if (verbose) {
			print(" D --> put static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}
		Concentrator.putStatic(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters
	}

	void around(): nonStaticPutfields() {	
		if (verbose) {
			print(" D --> put field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
		}

		Concentrator.putField(
			thisJoinPointStaticPart,
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget(), //Object receiver
			thisJoinPoint.getArgs()); //parameters
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
