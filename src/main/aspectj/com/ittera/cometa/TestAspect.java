package com.ittera.cometa;

/**
 * Created by libre on 7/31/16.
 */
aspect TestAspect {

	pointcut allClasses(): !within(TestAspect);

	pointcut voidMethods(): allClasses() && call(void *(..));

	pointcut nonVoidMethods(): allClasses() && call(!void *(..));

	pointcut allConstructors(): allClasses() && call(new(..));

	pointcut allSets(): allClasses() && set(* *);

	pointcut allGets(): allClasses() && get(* *);

	void around(): voidMethods() {	
		String s=" --- void method called: "+thisJoinPoint;
		if (thisJoinPoint.getArgs().length>0) {
			s=s+" arg 0="+thisJoinPoint.getArgs()[0];
		}
		System.out.println(s);
		proceed();
	}

	Object around(): nonVoidMethods() {	
		String s=" --- non-void method called: "+thisJoinPoint;
		if (thisJoinPoint.getArgs().length>0) {
			s=s+" arg 0="+thisJoinPoint.getArgs()[0];
		}
		System.out.println(s);
		Object a=proceed();
		return a;
	}


	Object around(): allConstructors() {	
		String s=" --- constructor called: "+thisJoinPoint;
		if (thisJoinPoint.getArgs().length>0) {
			s=s+" arg 0="+thisJoinPoint.getArgs()[0];
		}
		System.out.println(s);
		Object a=proceed();
		return a;
	}

	void around(): allSets() {	
		String s=" --- set field called: "+thisJoinPoint;
		if (thisJoinPoint.getArgs().length>0) {
			s=s+" arg 0="+thisJoinPoint.getArgs()[0];
		}
		System.out.println(s);
	}

	Object around(): allGets() {	
		String s=" --- get field called: "+thisJoinPoint;
		if (thisJoinPoint.getArgs().length>0) {
			s=s+" arg 0="+thisJoinPoint.getArgs()[0];
		}
		System.out.println(s);
		Object a=proceed();
		return a;
	}
}
