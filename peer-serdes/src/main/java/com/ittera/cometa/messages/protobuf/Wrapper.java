package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Fields.Field;
import com.ittera.cometa.messages.protobuf.data.Ctxt;
import com.ittera.cometa.messages.protobuf.data.Primitives;

import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.Array;

import org.apache.commons.lang3.ClassUtils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * WRAPPING METHODS:
 * Two versions of these exist, as we have generally more information when messages are built from local calls (with full reflection details),
 * than when these messaages are built for remote calls, and not all context and type information is available.
 */
public final class Wrapper {

    private static final Logger logger = LogManager.getLogger(Wrapper.class);

    private Wrapper() {
        //avoid instantiation
    }

    /**
     * Wrapped is the actual value if object is a primitive, a String, or an array of these types
     * Objects created by this Concentrator, are expected to be looked up in the object map by their identity hashCode.
     *
     * @param object
     * @return
     */
    private static Primitives.Object getWrappedObjectAux(Primitives.Object.Builder builder, Object object, Class clazz, String objectKey) {
        logger.traceEntry("with object: {}, class: {}, objectKey: {}", object, clazz, objectKey);

        if (object != null && objectKey != null) {
            throw new IllegalArgumentException("Both object and objectKey can't have values");
        }
        //value is null if both object and objectKey are null
        builder.setIsNull((objectKey == null) && (object == null));

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
            if (object instanceof String) {
                builder.setValue((String) object);
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
                /** the object is not primitive, String or Array
                 *  We set the isRef flag. We assume the object will be found in the objects map keyed with its identityHash, set below
                 *  TODO: when object not created by this Concentrator, full (deep) serialization/deserialization will be required
                 *  TODO: if it's of type Class, treat differently?
                 **/
            }
        }

        final Primitives.Object builtValue = builder.build();
        logger.traceExit("with wrappedValue: {}", builtValue);
        return builtValue;
    }

    /**
     * @param object
     * @return True if object is either null, a String/primitive/wrapper, or an array of Strings/primitives/wrappers.
     */
    public static boolean isWrappable(Object object) {
        return
                object == null ||
                        object == Void.class ||
                        ClassUtils.isPrimitiveOrWrapper(object.getClass()) ||
                        String.class.equals(object.getClass()) ||
                        (object.getClass().isArray() && ClassUtils.isPrimitiveOrWrapper(object.getClass().getComponentType())) ||
                        (object.getClass().isArray() && String.class.equals(object.getClass().getComponentType()));
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
