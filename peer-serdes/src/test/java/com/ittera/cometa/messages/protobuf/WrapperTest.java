package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Primitives.Field;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import static java.util.stream.Collectors.toList;

import static org.junit.Assert.*;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class WrapperTest {

	private static List<Class> allPrimitiveAndLangClasses;

	private static final List<Class> primitiveClasses = Arrays.asList(
		boolean.class,
		byte.class,
		char.class,
		double.class,
		float.class,
		int.class,
		long.class,
		short.class
	);

	private static final List<Class> primitiveWrapperClasses = Arrays.asList(
		Boolean.class,
		Byte.class,
		Character.class,
		Double.class,
		Float.class,
		Integer.class,
		Long.class,
		Short.class
	);

	/**
	 * Comprehensive list of all java.lang(8) classes
	 */
	private static final List<Class> nonWrapperJavaLangClasses = Arrays.asList(
		Character.Subset.class, Character.UnicodeBlock.class, Class.class, ClassLoader.class, ClassValue.class,
		Compiler.class, Enum.class, InheritableThreadLocal.class, Math.class, Number.class, Object.class, Package.class,
		Process.class, ProcessBuilder.class, ProcessBuilder.Redirect.class, Runtime.class, RuntimePermission.class,
		SecurityManager.class, StackTraceElement.class, StrictMath.class, StringBuffer.class, StringBuilder.class,
		System.class, Thread.class, ThreadGroup.class, ThreadLocal.class, Throwable.class, Void.class);

	/**
	 * List of objects that should be wrappable
	 */
	static final List<Object> wrappableObjects = Arrays.asList(
		/** null and void **/
		null, Void.class, void.class,
		/** primitives **/
		false, Byte.parseByte("0"), 'c', 0.43d, 512.5f, Integer.parseInt("4"), 34L, Short.parseShort("10"),
		/** char sequences **/
		String.valueOf("hello"), new StringBuilder("world"), new StringBuffer("!!"),
		/** primitive wrappers **/
		Boolean.TRUE, Byte.valueOf("1"), Character.valueOf('a'), Double.valueOf("382.03"), Float.valueOf("393.4"),
		Integer.valueOf("458"), Long.valueOf("348333"), Short.valueOf("25"));

	/**
	 * List of some objects that should NOT be wrappable
	 */
	static final List<Object> someNonWrappableObjects = Arrays.asList(
		Object.class, new Object(),
		new java.util.Date(), new ArrayList(), new java.util.HashSet(), new java.util.HashMap(), new java.util.Stack(),
		new java.util.Random(),
		new Object[1], new Class[1]);

	@BeforeClass
	public static void setupLists() {

		List<Class> javaLangClasses = new ArrayList<>();
		javaLangClasses.addAll(primitiveWrapperClasses);
		javaLangClasses.addAll(nonWrapperJavaLangClasses);

		allPrimitiveAndLangClasses = new ArrayList<>();
		allPrimitiveAndLangClasses.addAll(primitiveClasses);
		allPrimitiveAndLangClasses.addAll(javaLangClasses);

	}

	private static <T> T[] getArrayOf(Class<T> clazz, int size) {
		return (T[]) Array.newInstance(clazz, size);
	}

	// <editor-fold defaultstate="collapsed" desc="isWrappable tests">
	@Test
	public void isWrappable_nonWrappableObject_false() {

		for (Object obj : someNonWrappableObjects) {
			assertFalse(String.format("%s should not be wrappable!", obj), Wrapper.isWrappable(obj));
		}
	}

	@Test
	public void isWrappable_wrappableObject_true() {

		for (Object obj : wrappableObjects) {
			assertTrue(String.format("%s is not wrappable!", obj), Wrapper.isWrappable(obj));
		}
	}

	@Test
	public void isWrappable_oneDimArrayOfNonWrappableObject_false() {

		List<Object[]> nonWrappableArrays = someNonWrappableObjects.stream().
			map(o -> getArrayOf(o.getClass(), 1)).collect(toList());

		for (Object array : nonWrappableArrays) {
			assertFalse(String.format("%s should not be wrappable!", array), Wrapper.isWrappable(array));
		}
	}

	@Test
	public void isWrappable_oneDimArrayOfPrimitive_true() {

		int arraySize = 1;

		for (Class clazz : primitiveClasses) {
			Object primitiveArray = Array.newInstance(clazz, arraySize);
			assertTrue(String.format("%s is not wrappable!", primitiveArray), Wrapper.isWrappable(primitiveArray));
		}

	}

	@Test
	public void isWrappable_oneDimArrayOfWrapper_true() {

		// create list of 1-dimensional arrays, one for each of primitiveWrapperClasses, with length=1
		List wrapperArrays = (List<Object>) primitiveWrapperClasses.stream().map(c -> getArrayOf(c, 1))
			.collect(toList());

		for (Object wrapperArray : wrapperArrays) {
			assertTrue(String.format("Array of type %s is not wrappable", wrapperArray.getClass().getComponentType()),
				Wrapper.isWrappable(wrapperArray));
		}
	}

	/**
	 * 1-dimensional CharSequence arrays (String, StringBuffer, StringBuilder)
	 */
	@Test
	public void isWrappable_oneDimCharSequenceTypeArray_true() {

		List<CharSequence[]> charSeqArrays = new ArrayList<>();
		charSeqArrays.add(new StringBuffer[10]);
		charSeqArrays.add(new StringBuilder[10]);

		for (CharSequence[] array : charSeqArrays) {
			assertTrue(String.format("Array of type %s is not wrappable", array.getClass().getComponentType()),
				Wrapper.isWrappable(array));
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="isWrappableCharSeqClass tests">
	@Test
	public void isWrappableCharSeqClass_charSeqClasses_true() {

		assertTrue(Wrapper.isWrappableCharSeqClass(StringBuffer.class));
		assertTrue(Wrapper.isWrappableCharSeqClass(StringBuilder.class));
	}

	@Test
	public void isWrappableCharSeqClass_nonCharSeqClasses_false() {

		assertFalse(Wrapper.isWrappableCharSeqClass(Integer.class));
		assertFalse(Wrapper.isWrappableCharSeqClass(String.class));
		assertFalse(Wrapper.isWrappableCharSeqClass(Character.class));
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="getWrappedClass tests">

	@Test
	public void getWrappedClass_nullClass_unknownClassNoName() {

		Primitives.Class wrappedClass = Wrapper.getWrappedClass((Class) null);

		assertNotNull(wrappedClass);
		assertTrue(wrappedClass.getUnknown());
		assertFalse(wrappedClass.hasName());
		assertTrue(wrappedClass.getName().isEmpty());
	}

	@Test
	public void getWrappedClass_javaLangOrPrimitiveClass_wrappedOk() {

		for (Class clazz : allPrimitiveAndLangClasses) {
			Primitives.Class wrappedClass = Wrapper.getWrappedClass(clazz);

			// neither null nor unknown
			assertNotNull(wrappedClass);
			assertFalse(wrappedClass.getUnknown());

			//name is set and correctly
			assertEquals(clazz.getName(), wrappedClass.getName());
		}
	}

	@Test
	public void getWrappedClass_javaLangOrPrimitiveClassName_wrappedOk() {

		List<String> classNames = allPrimitiveAndLangClasses.stream().map(Class::getName).collect(toList());

		for (String classname : classNames) {
			Primitives.Class wrappedClass = Wrapper.getWrappedClass(classname);

			// neither null nor unknown
			assertNotNull(wrappedClass);
			assertFalse(wrappedClass.getUnknown());

			//name is set and correctly
			assertEquals(classname, wrappedClass.getName());
		}
	}

	/**
	 * Class of all wrappableObjects must be wrappable as well
	 */
	@Test
	public void getWrappedClass_wrappableClass_wrappedOk() {

		List<Class> classes = wrappableObjects.stream().filter(Objects::nonNull).map(Object::getClass).collect(toList());

		for (Class clazz : classes) {
			Primitives.Class wrappedClass = Wrapper.getWrappedClass(clazz);

			// neither null nor unknown
			assertNotNull(wrappedClass);
			assertFalse(wrappedClass.getUnknown());

			//name is set and correctly
			assertEquals(clazz.getName(), wrappedClass.getName());
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="getWrappedField tests">
	@Test
	public void getWrappedField_fieldAndClass_wrappedOk() {

		Class clazz = Integer.class;
		String fieldName = "height";

		Field field = Wrapper.getWrappedField(clazz, fieldName);

		assertNotNull(field);
		assertEquals(fieldName, field.getName());
		assertEquals(clazz.getName(), field.getClass_().getName());
	}

	@Test
	public void getWrappedField_fieldAndClassName_wrappedOk() {
		String className = Integer.class.getName();
		String fieldName = "height";

		Field field = Wrapper.getWrappedField(className, fieldName);

		assertNotNull(field);
		assertEquals(fieldName, field.getName());
		assertEquals(className, field.getClass_().getName());
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="getWrappedObject tests">

	@Test
	public void getWrappedObject_nonWrappableObj_nonWrappableExceptionThrown() {

		for (Object obj : someNonWrappableObjects) {
			try {
				Primitives.Object wrappedObj = Wrapper.getWrappedObject(obj, obj.getClass(), null);
				fail("Should have thrown an exception");
			} catch (NonWrappableObjectException ex) {
				// all good
			}
		}
	}

	@Test
	public void getWrappedObject_wrappableValuedObj_wrappedWithValue() {

		// test all wrappable objects except null & void
		List<Object> valuedWrappableObjs =
			wrappableObjects.stream()
				.filter(o -> o != null && o != void.class && o != Void.class)
				.collect(toList());

		for (Object obj : valuedWrappableObjs) {
			Primitives.Object wrappedObj = Wrapper.getWrappedObject(obj, obj.getClass(), null);

			assertNotNull(wrappedObj);
			assertNotNull(wrappedObj.getClass_());
			assertNotNull(wrappedObj.getClass_().getName());

			assertFalse(wrappedObj.hasRef());
			assertFalse(wrappedObj.getIsNull());
			assertTrue(wrappedObj.hasValue());
			assertFalse(wrappedObj.getIsVoid());
		}
	}

	@Test
	public void getWrappedObject_voidObject_wrappedOk() {

		Primitives.Object wrappedObj = Wrapper.getWrappedObject(void.class, void.class.getClass(), null);

		assertNotNull(wrappedObj);
		assertNotNull(wrappedObj.getClass_());
		assertNotNull(wrappedObj.getClass_().getName());

		assertFalse(wrappedObj.hasRef());
		assertFalse(wrappedObj.getIsNull());
		assertFalse(wrappedObj.hasValue());
		assertTrue(wrappedObj.getIsVoid());
	}

	@Test
	public void getWrappedObject_voidClassObject_wrappedOk() {

		Primitives.Object wrappedObj = Wrapper.getWrappedObject(Void.class, Void.class.getClass(), null);

		assertNotNull(wrappedObj);
		assertNotNull(wrappedObj.getClass_());
		assertNotNull(wrappedObj.getClass_().getName());

		assertFalse(wrappedObj.hasRef());
		assertFalse(wrappedObj.getIsNull());
		assertFalse(wrappedObj.hasValue());
		assertTrue(wrappedObj.getIsVoid());
	}

	@Test
	public void getWrappedObject_nullObjAndGivenClassname_wrappedOk() {

		Primitives.Object wrappedObj = Wrapper.getWrappedObject(null, "java.lang.String", null);

		assertNotNull(wrappedObj);
		assertNotNull(wrappedObj.getClass_());
		assertNotNull(wrappedObj.getClass_().getName());

		assertFalse(wrappedObj.hasRef());
		assertTrue(wrappedObj.getIsNull());
		assertFalse(wrappedObj.hasValue());
		assertFalse(wrappedObj.getIsVoid());
	}
	// </editor-fold>
}
