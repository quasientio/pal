package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Fields.Field;
import com.ittera.cometa.messages.protobuf.data.Ctxt;
import com.ittera.cometa.messages.protobuf.data.Primitives;

import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.Array;

import org.apache.commons.lang3.ClassUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WRAPPING METHODS:
 * Two versions of these exist, as we have generally more information when messages are built from local calls
 * (with full reflection details), than when these messages are built for remote calls, and not all context and
 * type information is available.
 */
public final class Wrapper {

	private static final Logger logger = LoggerFactory.getLogger(Wrapper.class);

	private Wrapper() {
		//avoid instantiation
	}

	private static boolean implementsCharSequence(Class clazz) {
		Class[] interfaces = clazz.getInterfaces();
		for (Class iface : interfaces) {
			if (iface.equals(CharSequence.class)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Wrappable objects:
	 * - null, void.class, Void.class
	 * - all primitive types
	 * - all wrapper types
	 * - char sequence types (String, StringBuilder, etc.)
	 *
	 * @param builder
	 * @param object
	 * @param clazz
	 * @param objectKey
	 * @return
	 */
	private static Primitives.Object getWrappedObjectAux(Primitives.Object.Builder builder, Object object, Class clazz,
																											 String objectKey) {
		logger.trace("in with object: {}, class: {}, objectKey: {}", object, clazz, objectKey);

		if (object != null && objectKey != null) {
			throw new IllegalArgumentException("Both object and objectKey can't have values");
		}
		//value is null if both object and objectKey are null
		builder.setIsNull(objectKey == null && object == null);

		//value may also be void
		builder.setIsVoid(object == void.class || object == Void.class);

		//set required fields (class already set at this point)
		builder.setIdentityHash(System.identityHashCode(object));

		if (clazz != null && clazz.isArray()) {
			builder.setIsArray(true);
		}

		if (objectKey != null) {
			builder.setRef(objectKey);
		}

		if (object != null) {
			builder.setHash(object.hashCode());
			if (object instanceof CharSequence) {
				builder.setValue(((CharSequence) object).toString());
			} else if (object.getClass().isArray()) {
				builder.setIsArray(true);
				//TODO only handles 1-dimensional arrays ?? Check out Arrays.deepToString

				final int length = Array.getLength(object);
				//NOTE: we iterate using reflection (Array) because the array type is unknown
				for (int i = 0; i < length; i++) {
					final Object arrayElem = Array.get(object, i);
					//wrap and all array elements -- recursive
					builder.addArrayValue(getWrappedObject(arrayElem, arrayElem.getClass(), objectKey));
				}
			} else if (ClassUtils.isPrimitiveOrWrapper(object.getClass())) {
				builder.setValue(String.valueOf(object));
			} else {
				logger.warn("Don't know what to do to wrap object: {} of class: {}", object, clazz);
			}
		}

		final Primitives.Object builtValue = builder.build();
		logger.trace("out with wrappedValue: {}", builtValue);
		return builtValue;
	}

	/**
	 * @param object
	 * @return True if object is either null, a CharSequence/primitive/wrapper, or an array of CharSequence/primitives/wrappers.
	 */
	public static boolean isWrappable(Object object) {
		return
			object == null || object == Void.class || object == void.class ||
				ClassUtils.isPrimitiveOrWrapper(object.getClass()) ||
				(object instanceof CharSequence) ||
				(object.getClass().isArray() && ClassUtils.isPrimitiveOrWrapper(object.getClass().getComponentType())) ||
				/** String[] will pass the last check so this check is redundant, but they're so common we can optimize a bit
				 * by checking first for String[] and avoid going through its interfaces as the next check does **/
				(object.getClass().isArray() && String.class.equals(object.getClass().getComponentType())) ||
				(object.getClass().isArray() && (implementsCharSequence(object.getClass().getComponentType())));
	}

	/**
	 * Called when we have only a class name
	 *
	 * @param object
	 * @param className
	 * @param objectKey
	 * @return
	 */
	static Primitives.Object getWrappedObject(Object object, String className, String objectKey) {
		final Primitives.Object.Builder builder = Primitives.Object.newBuilder();
		logger.debug("entering (w/ className) for: {}", object);

		//set required fields
		builder.setClass_(getWrappedClass(className));

		return getWrappedObjectAux(builder, object, null, objectKey);
	}

	/**
	 * Called when we have a full class object
	 *
	 * @param object
	 * @param clazz
	 * @param objectKey
	 * @return
	 */
	static Primitives.Object getWrappedObject(Object object, Class clazz, String objectKey) {
		final Primitives.Object.Builder builder = Primitives.Object.newBuilder();
		logger.debug("entering (w/ class {}) for: {}", clazz.getName(), object);

		//set required fields
		builder.setClass_(getWrappedClass(clazz));

		return getWrappedObjectAux(builder, object, clazz, objectKey);
	}

	static Primitives.Class getWrappedClass(String className) {
		final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
		if (className == null) {
			clazzBuilder.setUnknown(true);
		} else {
			clazzBuilder.setName(className);
		}
		return clazzBuilder.build();
	}

	static Primitives.Class getWrappedClass(Class clazz) {
		final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
		if (clazz == null) {
			clazzBuilder.setUnknown(true);
		} else {
			clazzBuilder.setName(clazz.getName());
		}
		//TODO: fill all other available Class info
		return clazzBuilder.build();
	}

	static Field getWrappedField(Class clazz, String fieldName) {
		final Field.Builder fieldBuilder = Field.newBuilder();

		fieldBuilder.setName(fieldName);
		fieldBuilder.setClass_(getWrappedClass(clazz));
		return fieldBuilder.build();
	}

	static Field getWrappedField(String className, String fieldName) {
		final Field.Builder fieldBuilder = Field.newBuilder();

		fieldBuilder.setName(fieldName);
		fieldBuilder.setClass_(getWrappedClass(className));
		return fieldBuilder.build();
	}

	static Ctxt.Context getWrappedContext(StaticPart staticPart, Object sender) {
		final Ctxt.Context.Builder ctxtBuilder = Ctxt.Context.newBuilder();

		ctxtBuilder.setSenderClass(getWrappedClass(staticPart.getSourceLocation().getWithinType()));
		if (sender != null) {
			ctxtBuilder.setSender(getWrappedObject(sender, staticPart.getSourceLocation().getWithinType(), null));
		}
		ctxtBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName());
		ctxtBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine());
		ctxtBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getName());

		return ctxtBuilder.build();
	}

}
