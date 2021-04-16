/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.messages.ExecMessageType;
import net.ittera.pal.messages.InternalHeaderType;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.colfer.ClInitCall;
import net.ittera.pal.messages.colfer.Class;
import net.ittera.pal.messages.colfer.ClassMethodCall;
import net.ittera.pal.messages.colfer.Constructor;
import net.ittera.pal.messages.colfer.ConstructorCall;
import net.ittera.pal.messages.colfer.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Field;
import net.ittera.pal.messages.colfer.InstanceFieldGet;
import net.ittera.pal.messages.colfer.InstanceFieldPut;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.InstanceMethodCall;
import net.ittera.pal.messages.colfer.InterceptKeyMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptReply;
import net.ittera.pal.messages.colfer.InterceptableField;
import net.ittera.pal.messages.colfer.InterceptableMethod;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.Method;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.colfer.RaisedThrowable;
import net.ittera.pal.messages.colfer.Reflectable;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldGet;
import net.ittera.pal.messages.colfer.StaticFieldPut;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.colfer.Throwable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * <pre>
 * The Colfer to JSON serializers
 * ------------------------------
 * These custom serializers are needed because:
 *  1) GSON will serialize empty arrays and empty strings, which are colfer's
 *    null-like values for these types.
 *  2) Our uint8 fields that correspond to an Enum are by default printed as byte,
 *    but we should print the name of the corresponding enum value.
 *
 *
 * The JSON serializers should be unaware of which fields are required,
 * resembling as much as possible to generic JSON serializers. So even if we know
 * that a value will be present and non-blank, we should still serialize only
 * after checking that notEmpty(value) == true.
 * This check must be skipped for boolean values, which are always serialized.
 *
 * In Colfer, empty means:
 * - null for objects
 * - "" for strings
 * - 0 for numeric types
 * - [] for arrays
 *
 * NOTE: These classes must be kept in sync with the colfer types under
 *       serdes/src/main/colfer.
 * </pre>
 */
class JSONSerializers {

  private static final Logger logger = LoggerFactory.getLogger(JSONSerializers.class);

  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  private static boolean notEmpty(int value) {
    return value != 0;
  }

  private static boolean notEmpty(Object value) {
    return value != null;
  }

  private static <E> boolean notEmpty(E[] value) {
    return value != null && value.length != 0;
  }

  static class ExecMessageSerializer implements JsonSerializer<ExecMessage> {

    @Override
    public JsonElement serialize(
        ExecMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      ExecMessageType execMessageType = ExecMessageType.values()[message.getExecMessageType()];
      final JsonObject jsonElement = new JsonObject();

      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }

      if (notEmpty(message.messageUuid)) {
        jsonElement.addProperty("message_uuid", message.messageUuid);
      }

      jsonElement.addProperty("msg_type", execMessageType.name());

      if (notEmpty(message.threadName)) {
        jsonElement.addProperty("thread_name", message.threadName);
      }

      if (notEmpty(message.currentTime)) {
        jsonElement.addProperty("current_time", message.currentTime);
      }

      if (notEmpty(message.dispatchSeq)) {
        jsonElement.addProperty("dispatch_seq", message.dispatchSeq);
      }

      if (notEmpty(message.builderSeq)) {
        jsonElement.addProperty("builder_seq", message.builderSeq);
      }

      if (notEmpty(message.followingUuid)) {
        jsonElement.addProperty("following_uuid", message.followingUuid);
      }

      switch (execMessageType) {
        case CONSTRUCTOR:
          jsonElement.add(
              "constructor_call", jsonSerializationContext.serialize(message.constructorCall));
          break;
        case INSTANCE_METHOD:
          jsonElement.add(
              "instance_method_call",
              jsonSerializationContext.serialize(message.instanceMethodCall));
          break;
        case CLASS_METHOD:
          jsonElement.add(
              "class_method_call", jsonSerializationContext.serialize(message.classMethodCall));
          break;
        case GET_STATIC:
          jsonElement.add(
              "static_field_get", jsonSerializationContext.serialize(message.staticFieldGet));
          break;
        case GET_FIELD:
          jsonElement.add(
              "instance_field_get", jsonSerializationContext.serialize(message.instanceFieldGet));
          break;
        case PUT_STATIC:
          jsonElement.add(
              "static_field_put", jsonSerializationContext.serialize(message.staticFieldPut));
          break;
        case PUT_FIELD:
          jsonElement.add(
              "instance_field_put", jsonSerializationContext.serialize(message.instanceFieldPut));
          break;
        case PUT_STATIC_DONE:
          jsonElement.add(
              "static_field_put_done",
              jsonSerializationContext.serialize(message.staticFieldPutDone));
          break;
        case PUT_FIELD_DONE:
          jsonElement.add(
              "instance_field_put_done",
              jsonSerializationContext.serialize(message.instanceFieldPutDone));
          break;
        case THROWABLE:
          jsonElement.add(
              "raised_throwable", jsonSerializationContext.serialize(message.raisedThrowable));
          break;
        case RETURN_VALUE:
          jsonElement.add("return_value", jsonSerializationContext.serialize(message.returnValue));
          break;
        default:
          logger.warn("Unsupported message of type: {}", execMessageType.name());
          jsonElement.addProperty("ERROR: Unsupported message of type", execMessageType.name());
      }
      return jsonElement;
    }
  }

  static class ConstructorCallSerializer implements JsonSerializer<ConstructorCall> {
    @Override
    public JsonElement serialize(
        ConstructorCall message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.parameters)) {
        jsonElement.add("parameters", jsonSerializationContext.serialize(message.parameters));
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class InstanceMethodCallSerializer implements JsonSerializer<InstanceMethodCall> {
    @Override
    public JsonElement serialize(
        InstanceMethodCall message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.object)) {
        jsonElement.add("object", jsonSerializationContext.serialize(message.object));
      }
      if (notEmpty(message.objectRef)) {
        jsonElement.addProperty("objectref", message.objectRef);
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.parameters)) {
        jsonElement.add("parameters", jsonSerializationContext.serialize(message.parameters));
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class ClInitCallSerializer implements JsonSerializer<ClInitCall> {
    @Override
    public JsonElement serialize(
        ClInitCall message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class ClassMethodCallSerializer implements JsonSerializer<ClassMethodCall> {
    @Override
    public JsonElement serialize(
        ClassMethodCall message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.exceptionTypes)) {
        jsonElement.add(
            "exception_types", jsonSerializationContext.serialize(message.exceptionTypes));
      }
      if (notEmpty(message.parameters)) {
        jsonElement.add("parameters", jsonSerializationContext.serialize(message.parameters));
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class ContextSerializer implements JsonSerializer<Context> {
    @Override
    public JsonElement serialize(
        Context message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.senderClass)) {
        jsonElement.add("sender_class", jsonSerializationContext.serialize(message.senderClass));
      }
      if (notEmpty(message.sender)) {
        jsonElement.add("sender", jsonSerializationContext.serialize(message.sender));
      }
      if (notEmpty(message.sourceLocationFile)) {
        jsonElement.addProperty("source_location_file", message.sourceLocationFile);
      }
      if (notEmpty(message.sourceLocationLine)) {
        jsonElement.addProperty("source_location_line", message.sourceLocationLine);
      }
      if (notEmpty(message.sourceLocationType)) {
        jsonElement.addProperty("source_location_type", message.sourceLocationType);
      }
      return jsonElement;
    }
  }

  static class ThrowableSerializer implements JsonSerializer<Throwable> {
    @Override
    public JsonElement serialize(
        Throwable message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.Type)) {
        jsonElement.addProperty("type", message.Type);
      }
      if (notEmpty(message.message)) {
        jsonElement.addProperty("message", message.message);
      }
      if (notEmpty(message.stackTraceElements)) {
        jsonElement.add(
            "stacktrace_elements", jsonSerializationContext.serialize(message.stackTraceElements));
      }
      if (notEmpty(message.cause)) {
        jsonElement.add("cause", jsonSerializationContext.serialize(message.cause));
      }
      return jsonElement;
    }
  }

  static class RaisedThrowableSerializer implements JsonSerializer<RaisedThrowable> {
    @Override
    public JsonElement serialize(
        RaisedThrowable message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      jsonElement.addProperty("in_initializer", message.inInitializer);
      if (notEmpty(message.constructor)) {
        jsonElement.addProperty("constructor", message.constructor);
      }
      if (notEmpty(message.method)) {
        jsonElement.addProperty("method", message.method);
      }
      if (notEmpty(message.field)) {
        jsonElement.addProperty("field", message.field);
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.throwable)) {
        jsonElement.add("throwable", jsonSerializationContext.serialize(message.throwable));
      }
      return jsonElement;
    }
  }

  static class StaticFieldGetSerializer implements JsonSerializer<StaticFieldGet> {
    @Override
    public JsonElement serialize(
        StaticFieldGet message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class StaticFieldPutSerializer implements JsonSerializer<StaticFieldPut> {

    @Override
    public JsonElement serialize(
        StaticFieldPut message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.valueObject)) {
        jsonElement.add("value_object", jsonSerializationContext.serialize(message.valueObject));
      }
      if (notEmpty(message.valueObjectRef)) {
        jsonElement.addProperty("value_objectref", message.valueObjectRef);
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class StaticFieldPutDoneSerializer implements JsonSerializer<StaticFieldPutDone> {
    @Override
    public JsonElement serialize(
        StaticFieldPutDone message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.staticFieldPutUuid)) {
        jsonElement.addProperty("static_field_put_uuid", message.staticFieldPutUuid);
      }

      return jsonElement;
    }
  }

  static class InstanceFieldGetSerializer implements JsonSerializer<InstanceFieldGet> {

    @Override
    public JsonElement serialize(
        InstanceFieldGet message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.object)) {
        jsonElement.add("object", jsonSerializationContext.serialize(message.object));
      }
      if (notEmpty(message.objectRef)) {
        jsonElement.addProperty("objectref", message.objectRef);
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class InstanceFieldPutSerializer implements JsonSerializer<InstanceFieldPut> {

    @Override
    public JsonElement serialize(
        InstanceFieldPut message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.object)) {
        jsonElement.add("object", jsonSerializationContext.serialize(message.object));
      }
      if (notEmpty(message.objectRef)) {
        jsonElement.addProperty("objectref", message.objectRef);
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.valueObject)) {
        jsonElement.add("value_object", jsonSerializationContext.serialize(message.valueObject));
      }
      if (notEmpty(message.valueObjectRef)) {
        jsonElement.addProperty("value_objectref", message.valueObjectRef);
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  static class InstanceFieldPutDoneSerializer implements JsonSerializer<InstanceFieldPutDone> {
    @Override
    public JsonElement serialize(
        InstanceFieldPutDone message,
        Type type,
        JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.instanceFieldPutUuid)) {
        jsonElement.addProperty("instance_field_put_uuid", message.instanceFieldPutUuid);
      }

      return jsonElement;
    }
  }

  static class InternalHeaderSerializer implements JsonSerializer<InternalHeader> {
    @Override
    public JsonElement serialize(
        InternalHeader message, Type type, JsonSerializationContext jsonSerializationContext) {
      final InternalHeaderType headerType = InternalHeaderType.values()[message.headerType];
      final JsonObject jsonElement = new JsonObject();
      jsonElement.addProperty("header_type", headerType.name());
      if (notEmpty(message.value)) {
        jsonElement.addProperty("value", message.value);
      }

      return jsonElement;
    }
  }

  static class InterceptableMethodSerializer implements JsonSerializer<InterceptableMethod> {
    @Override
    public JsonElement serialize(
        InterceptableMethod message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.parameterTypes)) {
        jsonElement.add(
            "parameter_types", jsonSerializationContext.serialize(message.parameterTypes));
      }

      return jsonElement;
    }
  }

  static class InterceptableFieldSerializer implements JsonSerializer<InterceptableField> {
    @Override
    public JsonElement serialize(
        InterceptableField message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      final FieldOpType fieldOpType = FieldOpType.values()[message.fieldOpType];
      jsonElement.addProperty("fieldop_type", fieldOpType.name());
      return jsonElement;
    }
  }

  static class InterceptMessageSerializer implements JsonSerializer<InterceptMessage> {
    @Override
    public JsonElement serialize(
        InterceptMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }
      if (notEmpty(message.messageUuid)) {
        jsonElement.addProperty("message_uuid", message.messageUuid);
      }

      final InterceptType interceptType = InterceptType.values()[message.interceptType];
      jsonElement.addProperty("intercept_type", interceptType.name());
      if (notEmpty(message.clazz)) {
        jsonElement.addProperty("class", message.clazz);
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.method)) {
        jsonElement.add("method", jsonSerializationContext.serialize(message.method));
      }
      if (notEmpty(message.callbackClass)) {
        jsonElement.addProperty("callback_class", message.callbackClass);
      }
      if (notEmpty(message.callbackMethod)) {
        jsonElement.addProperty("callback_method", message.callbackMethod);
      }
      return jsonElement;
    }
  }

  static class InterceptKeyMessageSerializer implements JsonSerializer<InterceptKeyMessage> {
    @Override
    public JsonElement serialize(
        InterceptKeyMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.addProperty("class", message.clazz);
      }
      final ExecMessageType execMessageType = ExecMessageType.values()[message.execMsgType];
      jsonElement.addProperty("exec_message_type", execMessageType.name());
      if (notEmpty(message.executableName)) {
        jsonElement.addProperty("executable_name", message.executableName);
      }
      if (notEmpty(message.parameterTypes)) {
        jsonElement.add(
            "parameter_types", jsonSerializationContext.serialize(message.parameterTypes));
      }
      return jsonElement;
    }
  }

  static class InterceptReplySerializer implements JsonSerializer<InterceptReply> {
    @Override
    public JsonElement serialize(
        InterceptReply message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }
      if (notEmpty(message.followingUuid)) {
        jsonElement.addProperty("following_uuid", message.followingUuid);
      }
      jsonElement.addProperty("result", message.result);
      return jsonElement;
    }
  }

  static class ClassSerializer implements JsonSerializer<Class> {
    @Override
    public JsonElement serialize(
        Class message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      jsonElement.addProperty("unknown", message.unknown);
      return jsonElement;
    }
  }

  static class ObjSerializer implements JsonSerializer<Obj> {
    @Override
    public JsonElement serialize(
        Obj message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.value)) {
        jsonElement.addProperty("value", message.value);
      }
      if (notEmpty(message.hash)) {
        jsonElement.addProperty("hash", message.hash);
      }
      if (notEmpty(message.identityHash)) {
        jsonElement.addProperty("identity_hash", message.identityHash);
      }
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      jsonElement.addProperty("is_array", message.isArray);
      if (notEmpty(message.arrayValues)) {
        jsonElement.add("array_values", jsonSerializationContext.serialize(message.arrayValues));
      }
      if (notEmpty(message.ref)) {
        jsonElement.addProperty("ref", message.ref);
      }
      jsonElement.addProperty("is_null", message.isNull);
      jsonElement.addProperty("is_void", message.isVoid);
      return jsonElement;
    }
  }

  static class FieldSerializer implements JsonSerializer<Field> {
    @Override
    public JsonElement serialize(
        Field message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.repr)) {
        jsonElement.addProperty("repr", message.repr);
      }
      return jsonElement;
    }
  }

  static class MethodSerializer implements JsonSerializer<Method> {
    @Override
    public JsonElement serialize(
        Method message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.repr)) {
        jsonElement.addProperty("repr", message.repr);
      }
      return jsonElement;
    }
  }

  static class ConstructorSerializer implements JsonSerializer<Constructor> {
    @Override
    public JsonElement serialize(
        Constructor message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.repr)) {
        jsonElement.addProperty("repr", message.repr);
      }
      return jsonElement;
    }
  }

  static class ReflectableSerializer implements JsonSerializer<Reflectable> {
    @Override
    public JsonElement serialize(
        Reflectable message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.constructor)) {
        jsonElement.add("constructor", jsonSerializationContext.serialize(message.constructor));
      }
      if (notEmpty(message.method)) {
        jsonElement.add("method", jsonSerializationContext.serialize(message.method));
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      return jsonElement;
    }
  }

  static class ParameterSerializer implements JsonSerializer<Parameter> {
    @Override
    public JsonElement serialize(
        Parameter message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.value)) {
        jsonElement.add("value", jsonSerializationContext.serialize(message.value));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      if (notEmpty(message.Type)) {
        jsonElement.add("type", jsonSerializationContext.serialize(message.Type));
      }
      jsonElement.addProperty("is_varargs", message.isVarArgs);
      return jsonElement;
    }
  }

  static class ReturnValueSerializer implements JsonSerializer<ReturnValue> {
    @Override
    public JsonElement serialize(
        ReturnValue message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      jsonElement.addProperty("is_void", message.isVoid);
      jsonElement.addProperty("is_class", message.isClass);
      if (notEmpty(message.object)) {
        jsonElement.add("object", jsonSerializationContext.serialize(message.object));
      }
      if (notEmpty(message.from)) {
        jsonElement.add("from", jsonSerializationContext.serialize(message.from));
      }
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      return jsonElement;
    }
  }

  static class MessageSerializer implements JsonSerializer<Message> {
    @Override
    public JsonElement serialize(
        Message message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      final MessageType messageType = MessageType.values()[message.messageType];
      jsonElement.addProperty("type", messageType.name());
      switch (messageType) {
        case ExecMessage:
          jsonElement.add("exec_message", jsonSerializationContext.serialize(message.execMessage));
          break;
        case InterceptMessage:
          jsonElement.add(
              "intercept_message", jsonSerializationContext.serialize(message.interceptMessage));
          break;
        case InterceptKey:
          jsonElement.add(
              "intercept_key_message",
              jsonSerializationContext.serialize(message.interceptKeyMessage));
          break;
        case InterceptReply:
          jsonElement.add(
              "intercept_reply", jsonSerializationContext.serialize(message.interceptReply));
          break;
      }
      return jsonElement;
    }
  }
}
