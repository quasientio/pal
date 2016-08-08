package com.ittera.cometa;

/**
 * Created by libre on 7/31/16.
 */

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

import com.ittera.cometa.distributor.ExecutableMessageCreationException;
import com.ittera.cometa.distributor.Distributor;

aspect DistributeAspect {

	private static final boolean verbose=false;

	pointcut allClasses(): !within(DistributeAspect);

	pointcut voidInstanceMethods(): allClasses() && call(!static void *(..));

	pointcut voidClassMethods(): allClasses() && call(static void *(..));

	pointcut nonVoidInstanceMethods(): allClasses() && call(!static !void *(..));

	pointcut nonVoidClassMethods(): allClasses() && call(static !void *(..));

	pointcut allConstructors(): allClasses() && call(new(..));

	pointcut staticGetfields(): allClasses() && get(static * *);

	pointcut nonStaticGetfields(): allClasses() && get(!static * *);

	pointcut staticPutfields(): allClasses() && set(static * *);

	pointcut nonStaticPutfields(): allClasses() && set(!static * *);

	static final void print(String s) {
		System.out.println(s);
	}

	void around(): voidInstanceMethods() {	
		if (verbose) {
			print(" D --> void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Distributor.voidInstanceMethod((CodeSignature)thisJoinPointStaticPart.getSignature(), //signature
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

		Distributor.voidClassMethod((CodeSignature)thisJoinPointStaticPart.getSignature(), //signature
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

		Object returnedValue = Distributor.nonVoidInstanceMethod(
			(CodeSignature)thisJoinPointStaticPart.getSignature(), //signature
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

		Object returnedValue = Distributor.nonVoidClassMethod(
			(CodeSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters

		return returnedValue;
	}

	Object around(): allConstructors() {	
		if (verbose) {
			print(" D --> constructor: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}

		Object returnedValue = Distributor.constructor(
			(CodeSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters

		return returnedValue;
	}


	Object around(): staticGetfields() {	
		if (verbose) {
			print(" D --> get static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			//printParameters(thisJoinPoint);
		}
		Object returnedValue = Distributor.getObjectStatic(
			(FieldSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis()); //Object sender

		return returnedValue;
	}


	Object around(): nonStaticGetfields() {	
		if (verbose) {
			print(" D --> get field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			//printParameters(thisJoinPoint);
		}
		Object returnedValue = Distributor.getObject(
			(FieldSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget()); //Object receiver

		return returnedValue;
	}

	void around(): staticPutfields() {	
		if (verbose) {
			print(" D --> put static: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			//printParameters(thisJoinPoint);
		}
		Distributor.putStatic(
			(FieldSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getArgs()); //parameters
	}

	void around(): nonStaticPutfields() {	
		if (verbose) {
			print(" D --> put field: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			//printParameters(thisJoinPoint);
		}

		Distributor.putField(
			(FieldSignature)thisJoinPointStaticPart.getSignature(), //signature
			thisJoinPoint.getThis(), //Object sender
			thisJoinPoint.getTarget(), //Object receiver
			thisJoinPoint.getArgs()); //parameters
	}



	static private void printStaticCtxt(JoinPoint.StaticPart jpsp) {
		print(" ... jp.id="+jpsp.getId());
		print(" ... jp.kind="+jpsp.getKind());
		//print(" ... jp.signature="+jpsp.getSignature());
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
