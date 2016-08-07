package com.ittera.cometa;

/**
 * Created by libre on 7/31/16.
 */

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;

import com.ittera.cometa.distributor.ExecutableMessageCreationException;
import com.ittera.cometa.distributor.Distributor;

aspect TestAspect {

	private boolean verbose=false;

	pointcut allClasses(): !within(TestAspect);

	pointcut voidMethods(): allClasses() && call(void *(..));

	pointcut nonVoidMethods(): allClasses() && call(!void *(..));

	pointcut allConstructors(): allClasses() && call(new(..));

	pointcut allSets(): allClasses() && set(* *);

	pointcut allGets(): allClasses() && get(* *);

	static final void print(String s) {
		System.out.println(s);
	}

	void around(): voidMethods() {	
		if (verbose) {
			print(" --> void method called: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}
		if (thisJoinPoint.getTarget()!=null) { //if non-static, call through Distr.
			try {
				if (verbose) {
					print("Calling through Distributor");
				}
				Distributor.instanceMethod(thisJoinPoint.getThis(), //Object sender
					thisJoinPoint.getTarget(), //Object receiver
					thisJoinPointStaticPart.getSignature().getName(), //String methodName
					thisJoinPointStaticPart.getSignature().toString(), //String methodSignature
					((CodeSignature)thisJoinPointStaticPart.getSignature()).getParameterTypes(), //parameter types
					thisJoinPoint.getArgs()); //parameters
			} catch (ExecutableMessageCreationException ex) {
				//deal with it
				print("Error calling Distributor instance method message");
				ex.printStackTrace();
			}
		} else { //if it's static, proceed directly to method
			proceed();
		}
	}

	Object around(): nonVoidMethods() {	
		if (verbose) {
			print(" --> non-void method called: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}
		Object a=proceed();
		return a;
	}


	Object around(): allConstructors() {	
		if (verbose) {
			print(" --> constructor called: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			printParameters(thisJoinPoint);
		}
		Object a=proceed();
		return a;
	}

	void around(): allSets() {	
		if (verbose) {
			print(" --> set field called: "+thisJoinPoint);
			//printParameters(thisJoinPoint);
		}
	}

	Object around(): allGets() {	
		if (verbose) {
			print(" --> get field called: "+thisJoinPoint);
			printStaticCtxt(thisJoinPointStaticPart);
			printNonStaticCtxt(thisJoinPoint);
			//printParameters(thisJoinPoint);
		}
		Object a=proceed();
		return a;
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
