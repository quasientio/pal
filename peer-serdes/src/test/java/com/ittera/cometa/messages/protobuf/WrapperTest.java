package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Fields.Field;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import static java.util.stream.Collectors.toList;

import static org.junit.Assert.*;

import java.lang.reflect.Array;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class WrapperTest {

	private static List<Class> javaLangClasses;
	private static List<Class> allPrimitiveAndLangClasses;

	private static List<Class> primitiveClasses = Arrays.asList(
		boolean.class,
		byte.class,
		char.class,
		double.class,
		float.class,
		int.class,
		long.class,
		short.class
	);

	private static List<Class> primitiveWrapperClasses = Arrays.asList(
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
	private static List<Class> nonWrapperJavaLangClasses = Arrays.asList(
		Character.Subset.class, Character.UnicodeBlock.class, Class.class, ClassLoader.class, ClassValue.class,
		Compiler.class, Enum.class, InheritableThreadLocal.class, Math.class, Number.class, Object.class, Package.class,
		Process.class, ProcessBuilder.class, ProcessBuilder.Redirect.class, Runtime.class, RuntimePermission.class,
		SecurityManager.class, StackTraceElement.class, StrictMath.class, StringBuffer.class, StringBuilder.class,
		System.class, Thread.class, ThreadGroup.class, ThreadLocal.class, Throwable.class, Void.class);

	/**
	 * List of objects that should be wrappable
	 */
	private static List<Object> wrappableObjects = Arrays.asList(
		/** null and void **/
		null, Void.class, void.class,
		/** primitives **/
		false, Byte.parseByte("0"), 'c', 0.43d, 512.5f, Integer.parseInt("4"), 34L, Short.parseShort("10"),
		/** strings **/
		String.valueOf("hello"),
		/** primitive wrappers **/
		Boolean.TRUE, Byte.valueOf("1"), Character.valueOf('a'), Double.valueOf("382.03"), Float.valueOf("393.4"),
		Integer.valueOf("458"), Long.valueOf("348333"), Short.valueOf("25"));

	@BeforeClass
	public static void setupLists() {

		javaLangClasses = new ArrayList();
		javaLangClasses.addAll(primitiveWrapperClasses);
		javaLangClasses.addAll(nonWrapperJavaLangClasses);

		allPrimitiveAndLangClasses = new ArrayList();
		allPrimitiveAndLangClasses.addAll(primitiveClasses);
		allPrimitiveAndLangClasses.addAll(javaLangClasses);

	}

	private static <T> T[] getArrayOf(Class<T> clazz, int size) {
		return (T[]) Array.newInstance(clazz, size);
	}

	@Test
	public void isWrappable_wrappableObject_true() {

		for (Object obj : wrappableObjects) {
			assertTrue(String.format("%s is not wrappable!", obj), Wrapper.isWrappable(obj));
		}
	}

	@Test
	public void isWrappable_oneDimArrayOfPrimitive_true() {

		int arraySize = 10;

		for (Class clazz : primitiveClasses) {
			Object primitiveArray = Array.newInstance(clazz, arraySize);
			assertTrue(String.format("%s is not wrappable!", primitiveArray), Wrapper.isWrappable(primitiveArray));
		}

	}

	@Test
	public void isWrappable_oneDimArrayOfWrapper_true() {

		// create list of 1-dimensional arrays, one for each of primitiveWrapperClasses, with length=10
		List primitiveArrays = (List<Object>) primitiveWrapperClasses.stream().map(c -> getArrayOf(c, 10))
			.collect(toList());

		for (Object array : primitiveArrays) {
			assertTrue(String.format("Array of type %s is not wrappable", array.getClass().getComponentType()),
				Wrapper.isWrappable(array));
		}
	}

	/**
	 * 1-dimensional CharSequence arrays (String, StringBuffer, StringBuilder)
	 */
	@Test
	public void isWrappable_oneDimCharSequenceType_true() {

		List<CharSequence[]> charSeqArrays = new ArrayList<>();
		charSeqArrays.add(new String[10]);
		charSeqArrays.add(new StringBuffer[10]);
		charSeqArrays.add(new StringBuilder[10]);

		for (CharSequence[] array : charSeqArrays) {
			assertTrue(String.format("Array of type %s is not wrappable", array.getClass().getComponentType()),
				Wrapper.isWrappable(array));
		}
	}

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

		List<String> classNames = allPrimitiveAndLangClasses.stream().map(c -> c.getName()).collect(toList());

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

		List<Class> classes = wrappableObjects.stream().filter(o -> o != null).map(o -> o.getClass())
			.collect(toList());

		for (Class clazz : classes) {
			Primitives.Class wrappedClass = Wrapper.getWrappedClass(clazz);

			// neither null nor unknown
			assertNotNull(wrappedClass);
			assertFalse(wrappedClass.getUnknown());

			//name is set and correctly
			assertEquals(clazz.getName(), wrappedClass.getName());
		}
	}

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

	@Test
	public void getWrappedObject_wrappableObjAndClassname_wrappedOk() {

		for (Object obj : wrappableObjects) {
			Primitives.Object wrappedObj = Wrapper.getWrappedObject(obj, obj == null ? null
				: obj.getClass().getName(), null);

			assertNotNull(wrappedObj);
			assertNotNull(wrappedObj.getClass_());
			assertNotNull(wrappedObj.getClass_().getName());
			assertFalse(wrappedObj.hasRef());

			if (obj == null) {
				assertTrue(wrappedObj.getIsNull());
				assertFalse(wrappedObj.hasValue());
			} else {
				assertFalse(wrappedObj.getIsNull());
				assertTrue(wrappedObj.hasValue());
			}
		}
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
	}

}
