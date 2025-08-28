/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.messages.colfer.ClInitCall;
import com.quasient.pal.messages.colfer.Class;
import com.quasient.pal.messages.colfer.ClassMethodCall;
import com.quasient.pal.messages.colfer.Constructor;
import com.quasient.pal.messages.colfer.ConstructorCall;
import com.quasient.pal.messages.colfer.Context;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Field;
import com.quasient.pal.messages.colfer.InstanceFieldGet;
import com.quasient.pal.messages.colfer.InstanceFieldPut;
import com.quasient.pal.messages.colfer.InstanceFieldPutDone;
import com.quasient.pal.messages.colfer.InstanceMethodCall;
import com.quasient.pal.messages.colfer.InterceptKeyMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.InterceptResponse;
import com.quasient.pal.messages.colfer.InterceptableField;
import com.quasient.pal.messages.colfer.InterceptableMethod;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.colfer.Method;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.colfer.RaisedThrowable;
import com.quasient.pal.messages.colfer.Reflectable;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.colfer.StaticFieldGet;
import com.quasient.pal.messages.colfer.StaticFieldPut;
import com.quasient.pal.messages.colfer.StaticFieldPutDone;
import com.quasient.pal.messages.colfer.Throwable;
import com.quasient.pal.messages.types.ControlCommandType;
import com.quasient.pal.messages.types.ControlStatusType;
import com.quasient.pal.messages.types.InternalHeaderType;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.messages.types.MetaServiceType;
import com.quasient.pal.messages.types.MetaStatusType;
import java.lang.reflect.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides custom JSON serializers and deserializers for Colfer message types.
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
public class JsonSerializers {

  /** Logger instance for JSON serialization operations. */
  private static final Logger logger = LoggerFactory.getLogger(JsonSerializers.class);

  /**
   * Determines if the provided string is not null and not empty.
   *
   * @param value the string to check
   * @return {@code true} if the string is not null and not empty, {@code false} otherwise
   */
  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * Determines if the provided integer is not zero.
   *
   * @param value the integer to check
   * @return {@code true} if the integer is not zero, {@code false} otherwise
   */
  private static boolean notEmpty(int value) {
    return value != 0;
  }

  /**
   * Determines if the provided object is not null.
   *
   * @param value the object to check
   * @return {@code true} if the object is not null, {@code false} otherwise
   */
  private static boolean notEmpty(Object value) {
    return value != null;
  }

  /**
   * Determines if the provided array is not null and not empty.
   *
   * @param value the array to check
   * @param <E> the type of elements in the array
   * @return {@code true} if the array is not null and not empty, {@code false} otherwise
   */
  private static <E> boolean notEmpty(E[] value) {
    return value != null && value.length != 0;
  }

  /**
   * Determines if the provided boolean is {@code true}.
   *
   * @param value the boolean to check
   * @return {@code true} if the boolean is {@code true}, {@code false} otherwise
   */
  private static boolean notEmpty(boolean value) {
    return value;
  }

  /** Serializes {@link ExecMessage} objects to JSON. */
  public static class ExecMessageSerializer implements JsonSerializer<ExecMessage> {

    /**
     * Serializes an {@link ExecMessage} into its corresponding JSON representation.
     *
     * @param message the {@link ExecMessage} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ExecMessage}
     */
    @Override
    public JsonElement serialize(
        ExecMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();

      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }

      if (notEmpty(message.messageId)) {
        jsonElement.addProperty("message_id", message.messageId);
      }

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

      if (notEmpty(message.responseToId)) {
        jsonElement.addProperty("response_to", message.responseToId);
      }

      MessageType execMessageType = getMessageTypeOf(message);
      switch (execMessageType) {
        case EXEC_CONSTRUCTOR ->
            jsonElement.add(
                "constructor_call", jsonSerializationContext.serialize(message.constructorCall));
        case EXEC_INSTANCE_METHOD ->
            jsonElement.add(
                "instance_method_call",
                jsonSerializationContext.serialize(message.instanceMethodCall));
        case EXEC_CLASS_METHOD ->
            jsonElement.add(
                "class_method_call", jsonSerializationContext.serialize(message.classMethodCall));
        case EXEC_GET_STATIC ->
            jsonElement.add(
                "static_field_get", jsonSerializationContext.serialize(message.staticFieldGet));
        case EXEC_GET_FIELD ->
            jsonElement.add(
                "instance_field_get", jsonSerializationContext.serialize(message.instanceFieldGet));
        case EXEC_PUT_STATIC ->
            jsonElement.add(
                "static_field_put", jsonSerializationContext.serialize(message.staticFieldPut));
        case EXEC_PUT_FIELD ->
            jsonElement.add(
                "instance_field_put", jsonSerializationContext.serialize(message.instanceFieldPut));
        case EXEC_PUT_STATIC_DONE ->
            jsonElement.add(
                "static_field_put_done",
                jsonSerializationContext.serialize(message.staticFieldPutDone));
        case EXEC_PUT_FIELD_DONE ->
            jsonElement.add(
                "instance_field_put_done",
                jsonSerializationContext.serialize(message.instanceFieldPutDone));
        case EXEC_THROWABLE ->
            jsonElement.add(
                "raised_throwable", jsonSerializationContext.serialize(message.raisedThrowable));
        case EXEC_RETURN_VALUE ->
            jsonElement.add(
                "return_value", jsonSerializationContext.serialize(message.returnValue));
        default -> {
          logger.warn("Unsupported message of type: {}", execMessageType.name());
          jsonElement.addProperty("ERROR: Unsupported message of type", execMessageType.name());
        }
      }
      return jsonElement;
    }
  }

  /** Serializes {@link ConstructorCall} objects to JSON. */
  public static class ConstructorCallSerializer implements JsonSerializer<ConstructorCall> {
    /**
     * Serializes a {@link ConstructorCall} into its corresponding JSON representation.
     *
     * @param message the {@link ConstructorCall} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ConstructorCall}
     */
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

  /** Serializes {@link InstanceMethodCall} objects to JSON. */
  public static class InstanceMethodCallSerializer implements JsonSerializer<InstanceMethodCall> {
    /**
     * Serializes an {@link InstanceMethodCall} into its corresponding JSON representation.
     *
     * @param message the {@link InstanceMethodCall} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InstanceMethodCall}
     */
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

  /** Serializes {@link ClInitCall} objects to JSON. */
  public static class ClInitCallSerializer implements JsonSerializer<ClInitCall> {
    /**
     * Serializes a {@link ClInitCall} into its corresponding JSON representation.
     *
     * @param message the {@link ClInitCall} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ClInitCall}
     */
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

  /** Serializes {@link ClassMethodCall} objects to JSON. */
  public static class ClassMethodCallSerializer implements JsonSerializer<ClassMethodCall> {
    /**
     * Serializes a {@link ClassMethodCall} into its corresponding JSON representation.
     *
     * @param message the {@link ClassMethodCall} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ClassMethodCall}
     */
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
      if (notEmpty(message.parameters)) {
        jsonElement.add("parameters", jsonSerializationContext.serialize(message.parameters));
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  /** Serializes {@link Context} objects to JSON. */
  public static class ContextSerializer implements JsonSerializer<Context> {
    /**
     * Serializes a {@link Context} into its corresponding JSON representation.
     *
     * @param message the {@link Context} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Context}
     */
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

  /** Serializes {@link Throwable} objects to JSON. */
  public static class ThrowableSerializer implements JsonSerializer<Throwable> {
    /**
     * Serializes a {@link Throwable} into its corresponding JSON representation.
     *
     * @param message the {@link Throwable} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Throwable}
     */
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

  /** Serializes {@link RaisedThrowable} objects to JSON. */
  public static class RaisedThrowableSerializer implements JsonSerializer<RaisedThrowable> {
    /**
     * Serializes a {@link RaisedThrowable} into its corresponding JSON representation.
     *
     * @param message the {@link RaisedThrowable} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link RaisedThrowable}
     */
    @Override
    public JsonElement serialize(
        RaisedThrowable message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.inInitializer)) {
        jsonElement.addProperty("in_initializer", true);
      }
      if (notEmpty(message.from)) {
        jsonElement.add("from", jsonSerializationContext.serialize(message.from));
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

  /** Serializes {@link StaticFieldGet} objects to JSON. */
  public static class StaticFieldGetSerializer implements JsonSerializer<StaticFieldGet> {
    /**
     * Serializes a {@link StaticFieldGet} into its corresponding JSON representation.
     *
     * @param message the {@link StaticFieldGet} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link StaticFieldGet}
     */
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
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  /** Serializes {@link StaticFieldPut} objects to JSON. */
  public static class StaticFieldPutSerializer implements JsonSerializer<StaticFieldPut> {

    /**
     * Serializes a {@link StaticFieldPut} into its corresponding JSON representation.
     *
     * @param message the {@link StaticFieldPut} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link StaticFieldPut}
     */
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
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  /** Serializes and deserializes {@link StaticFieldPutDone} objects to and from JSON. */
  public static class StaticFieldPutDoneAdapter
      implements JsonSerializer<StaticFieldPutDone>, JsonDeserializer<StaticFieldPutDone> {
    /**
     * Serializes a {@link StaticFieldPutDone} into its corresponding JSON representation.
     *
     * @param message the {@link StaticFieldPutDone} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link StaticFieldPutDone}
     */
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
      if (notEmpty(message.staticFieldPutId)) {
        jsonElement.addProperty("static_field_put_id", message.staticFieldPutId);
      }

      return jsonElement;
    }

    /**
     * Deserializes a JSON element into a {@link StaticFieldPutDone} object.
     *
     * @param json the JSON element to deserialize
     * @param typeOfT the type of the Object to deserialize to
     * @param context the context of the deserialization process
     * @return the deserialized {@link StaticFieldPutDone} object
     * @throws JsonParseException if the JSON is not in the expected format
     */
    @Override
    public StaticFieldPutDone deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      final JsonObject jsonObject = json.getAsJsonObject();
      final StaticFieldPutDone staticFieldPutDone = new StaticFieldPutDone();
      if (jsonObject.has("class")) {
        staticFieldPutDone.clazz = context.deserialize(jsonObject.get("class"), Class.class);
      }
      if (jsonObject.has("field")) {
        staticFieldPutDone.field = context.deserialize(jsonObject.get("field"), Field.class);
      }
      if (jsonObject.has("static_field_put_id")) {
        staticFieldPutDone.staticFieldPutId = jsonObject.get("static_field_put_id").getAsString();
      }
      return staticFieldPutDone;
    }
  }

  /** Serializes {@link InstanceFieldGet} objects to JSON. */
  public static class InstanceFieldGetSerializer implements JsonSerializer<InstanceFieldGet> {

    /**
     * Serializes an {@link InstanceFieldGet} into its corresponding JSON representation.
     *
     * @param message the {@link InstanceFieldGet} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InstanceFieldGet}
     */
    @Override
    public JsonElement serialize(
        InstanceFieldGet message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.objectRef)) {
        jsonElement.addProperty("objectref", message.objectRef);
      }
      if (notEmpty(message.field)) {
        jsonElement.add("field", jsonSerializationContext.serialize(message.field));
      }
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  /** Serializes {@link InstanceFieldPut} objects to JSON. */
  public static class InstanceFieldPutSerializer implements JsonSerializer<InstanceFieldPut> {

    /**
     * Serializes an {@link InstanceFieldPut} into its corresponding JSON representation.
     *
     * @param message the {@link InstanceFieldPut} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InstanceFieldPut}
     */
    @Override
    public JsonElement serialize(
        InstanceFieldPut message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
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
      if (notEmpty(message.context)) {
        jsonElement.add("context", jsonSerializationContext.serialize(message.context));
      }

      return jsonElement;
    }
  }

  /** Serializes and deserializes {@link InstanceFieldPutDone} objects to and from JSON. */
  public static class InstanceFieldPutDoneAdapter
      implements JsonSerializer<InstanceFieldPutDone>, JsonDeserializer<InstanceFieldPutDone> {
    /**
     * Serializes an {@link InstanceFieldPutDone} into its corresponding JSON representation.
     *
     * @param message the {@link InstanceFieldPutDone} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InstanceFieldPutDone}
     */
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
      if (notEmpty(message.instanceFieldPutId)) {
        jsonElement.addProperty("instance_field_put_id", message.instanceFieldPutId);
      }

      return jsonElement;
    }

    /**
     * Deserializes a JSON element into an {@link InstanceFieldPutDone} object.
     *
     * @param json the JSON element to deserialize
     * @param typeOfT the type of the Object to deserialize to
     * @param context the context of the deserialization process
     * @return the deserialized {@link InstanceFieldPutDone} object
     * @throws JsonParseException if the JSON is not in the expected format
     */
    @Override
    public InstanceFieldPutDone deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      final JsonObject jsonObject = json.getAsJsonObject();
      final InstanceFieldPutDone instanceFieldPutDone = new InstanceFieldPutDone();
      if (jsonObject.has("class")) {
        instanceFieldPutDone.clazz = context.deserialize(jsonObject.get("class"), Class.class);
      }
      if (jsonObject.has("field")) {
        instanceFieldPutDone.field = context.deserialize(jsonObject.get("field"), Field.class);
      }
      if (jsonObject.has("instance_field_put_id")) {
        instanceFieldPutDone.instanceFieldPutId =
            jsonObject.get("instance_field_put_id").getAsString();
      }
      return instanceFieldPutDone;
    }
  }

  /** Serializes {@link InternalHeader} objects to JSON. */
  public static class InternalHeaderSerializer implements JsonSerializer<InternalHeader> {
    /**
     * Serializes an {@link InternalHeader} into its corresponding JSON representation.
     *
     * @param message the {@link InternalHeader} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InternalHeader}
     */
    @Override
    public JsonElement serialize(
        InternalHeader message, Type type, JsonSerializationContext jsonSerializationContext) {
      final InternalHeaderType headerType = InternalHeaderType.fromByte(message.headerType);
      final JsonObject jsonElement = new JsonObject();
      jsonElement.addProperty("header_type", headerType.name());
      if (notEmpty(message.value)) {
        jsonElement.addProperty("value", message.value);
      }

      return jsonElement;
    }
  }

  /** Serializes {@link InterceptableMethod} objects to JSON. */
  public static class InterceptableMethodSerializer implements JsonSerializer<InterceptableMethod> {
    /**
     * Serializes an {@link InterceptableMethod} into its corresponding JSON representation.
     *
     * @param message the {@link InterceptableMethod} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InterceptableMethod}
     */
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

  /** Serializes {@link InterceptableField} objects to JSON. */
  public static class InterceptableFieldSerializer implements JsonSerializer<InterceptableField> {
    /**
     * Serializes an {@link InterceptableField} into its corresponding JSON representation.
     *
     * @param message the {@link InterceptableField} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InterceptableField}
     */
    @Override
    public JsonElement serialize(
        InterceptableField message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      final FieldOpType fieldOpType = FieldOpType.fromByte(message.fieldOpType);
      jsonElement.addProperty("fieldop_type", fieldOpType.name());
      return jsonElement;
    }
  }

  /** Serializes {@link InterceptMessage} objects to JSON. */
  public static class InterceptMessageSerializer implements JsonSerializer<InterceptMessage> {
    /**
     * Serializes an {@link InterceptMessage} into its corresponding JSON representation.
     *
     * @param message the {@link InterceptMessage} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InterceptMessage}
     */
    @Override
    public JsonElement serialize(
        InterceptMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }
      if (notEmpty(message.messageId)) {
        jsonElement.addProperty("message_id", message.messageId);
      }

      final InterceptType interceptType = InterceptType.fromByte(message.interceptType);
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

  /** Serializes {@link InterceptKeyMessage} objects to JSON. */
  public static class InterceptKeyMessageSerializer implements JsonSerializer<InterceptKeyMessage> {
    /**
     * Serializes an {@link InterceptKeyMessage} into its corresponding JSON representation.
     *
     * @param message the {@link InterceptKeyMessage} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InterceptKeyMessage}
     */
    @Override
    public JsonElement serialize(
        InterceptKeyMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.addProperty("class", message.clazz);
      }
      MessageType execMessageType = MessageType.fromId(message.execMsgType);
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

  /** Serializes {@link InterceptResponse} objects to JSON. */
  public static class InterceptResponseSerializer implements JsonSerializer<InterceptResponse> {
    /**
     * Serializes an {@link InterceptResponse} into its corresponding JSON representation.
     *
     * @param message the {@link InterceptResponse} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link InterceptResponse}
     */
    @Override
    public JsonElement serialize(
        InterceptResponse message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.peerUuid)) {
        jsonElement.addProperty("peer_uuid", message.peerUuid);
      }
      if (notEmpty(message.responseToId)) {
        jsonElement.addProperty("response_to", message.responseToId);
      }
      if (notEmpty(message.result)) {
        jsonElement.addProperty("result", true);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Class} objects to JSON. */
  public static class ClassSerializer implements JsonSerializer<Class> {
    /**
     * Serializes a {@link Class} into its corresponding JSON representation.
     *
     * @param message the {@link Class} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Class}
     */
    @Override
    public JsonElement serialize(
        Class message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Obj} objects to JSON. */
  public static class ObjSerializer implements JsonSerializer<Obj> {
    /**
     * Serializes an {@link Obj} into its corresponding JSON representation.
     *
     * @param message the {@link Obj} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Obj}
     */
    @Override
    public JsonElement serialize(
        Obj message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.value)) {
        jsonElement.addProperty("value", message.value);
      }
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.ref)) {
        jsonElement.addProperty("ref", message.ref);
      }
      if (notEmpty(message.isNull)) {
        jsonElement.addProperty("null", true);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Field} objects to JSON. */
  public static class FieldSerializer implements JsonSerializer<Field> {
    /**
     * Serializes a {@link Field} into its corresponding JSON representation.
     *
     * @param message the {@link Field} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Field}
     */
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
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Method} objects to JSON. */
  public static class MethodSerializer implements JsonSerializer<Method> {
    /**
     * Serializes a {@link Method} into its corresponding JSON representation.
     *
     * @param message the {@link Method} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Method}
     */
    @Override
    public JsonElement serialize(
        Method message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.name)) {
        jsonElement.addProperty("name", message.name);
      }
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      if (notEmpty(message.modifiers)) {
        jsonElement.addProperty("modifiers", message.modifiers);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Constructor} objects to JSON. */
  public static class ConstructorSerializer implements JsonSerializer<Constructor> {
    /**
     * Serializes a {@link Constructor} into its corresponding JSON representation.
     *
     * @param message the {@link Constructor} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Constructor}
     */
    @Override
    public JsonElement serialize(
        Constructor message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.clazz)) {
        jsonElement.add("class", jsonSerializationContext.serialize(message.clazz));
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Reflectable} objects to JSON. */
  public static class ReflectableSerializer implements JsonSerializer<Reflectable> {
    /**
     * Serializes a {@link Reflectable} into its corresponding JSON representation.
     *
     * @param message the {@link Reflectable} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Reflectable}
     */
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

  /** Serializes {@link Parameter} objects to JSON. */
  public static class ParameterSerializer implements JsonSerializer<Parameter> {
    /**
     * Serializes a {@link Parameter} into its corresponding JSON representation.
     *
     * @param message the {@link Parameter} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Parameter}
     */
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
      return jsonElement;
    }
  }

  /** Serializes and deserializes {@link ReturnValue} objects to and from JSON. */
  public static class ReturnValueAdapter
      implements JsonSerializer<ReturnValue>, JsonDeserializer<ReturnValue> {
    /**
     * Serializes a {@link ReturnValue} into its corresponding JSON representation.
     *
     * @param message the {@link ReturnValue} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ReturnValue}
     */
    @Override
    public JsonElement serialize(
        ReturnValue message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.isVoid)) {
        jsonElement.addProperty("void", true);
      }
      if (notEmpty(message.object)) {
        jsonElement.add("object", jsonSerializationContext.serialize(message.object));
      }
      if (notEmpty(message.from)) {
        jsonElement.add("from", jsonSerializationContext.serialize(message.from));
      }
      return jsonElement;
    }

    /**
     * Deserializes a JSON element into a {@link ReturnValue} object.
     *
     * @param json the JSON element to deserialize
     * @param typeOfT the type of the Object to deserialize to
     * @param context the context of the deserialization process
     * @return the deserialized {@link ReturnValue} object
     * @throws JsonParseException if the JSON is not in the expected format
     */
    @Override
    public ReturnValue deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      final JsonObject jsonObject = json.getAsJsonObject();
      final ReturnValue returnValue = new ReturnValue();
      if (jsonObject.has("void")) {
        returnValue.isVoid = jsonObject.get("void").getAsBoolean();
      }
      if (jsonObject.has("object")) {
        returnValue.object = context.deserialize(jsonObject.get("object"), Obj.class);
      }
      if (jsonObject.has("from")) {
        returnValue.from = context.deserialize(jsonObject.get("from"), Reflectable.class);
      }
      return returnValue;
    }
  }

  /** Serializes {@link ControlMessage} objects to JSON. */
  public static class ControlMessageSerializer implements JsonSerializer<ControlMessage> {
    /**
     * Serializes a {@link ControlMessage} into its corresponding JSON representation.
     *
     * @param message the {@link ControlMessage} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link ControlMessage}
     */
    @Override
    public JsonElement serialize(
        ControlMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.fromPeer)) {
        jsonElement.addProperty("from_peer", message.fromPeer);
      }
      if (notEmpty(message.messageId)) {
        jsonElement.addProperty("message_id", message.messageId);
      }
      if (notEmpty(message.responseToId)) {
        jsonElement.addProperty("response_to", message.responseToId);
      }
      if (notEmpty(message.command)) {
        ControlCommandType commandType = ControlCommandType.fromId(message.getCommand());
        jsonElement.addProperty("command", commandType.name());
      }
      if (notEmpty(message.params)) {
        jsonElement.add("params", jsonSerializationContext.serialize(message.params));
      }
      if (notEmpty(message.status)) {
        ControlStatusType statusType = ControlStatusType.fromId(message.getStatus());
        jsonElement.addProperty("status", statusType.name());
      }
      if (notEmpty(message.body)) {
        jsonElement.addProperty("body", message.body);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link MetaMessage} objects to JSON. */
  public static class MetaMessageSerializer implements JsonSerializer<MetaMessage> {
    /**
     * Serializes a {@link MetaMessage} into its corresponding JSON representation.
     *
     * @param message the {@link MetaMessage} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link MetaMessage}
     */
    @Override
    public JsonElement serialize(
        MetaMessage message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      if (notEmpty(message.fromPeer)) {
        jsonElement.addProperty("from_peer", message.fromPeer);
      }
      if (notEmpty(message.messageId)) {
        jsonElement.addProperty("message_id", message.messageId);
      }
      if (notEmpty(message.responseToId)) {
        jsonElement.addProperty("response_to", message.responseToId);
      }
      if (notEmpty(message.service)) {
        MetaServiceType metaServiceType = MetaServiceType.fromId(message.getService());
        jsonElement.addProperty("service", metaServiceType.getJsonName());
      }
      if (notEmpty(message.params)) {
        jsonElement.add("params", jsonSerializationContext.serialize(message.params));
      }
      if (notEmpty(message.status)) {
        MetaStatusType statusType = MetaStatusType.fromId(message.getStatus());
        jsonElement.addProperty("status", statusType.name());
      }
      if (notEmpty(message.body)) {
        jsonElement.addProperty("body", message.body);
      }
      return jsonElement;
    }
  }

  /** Serializes {@link Message} objects to JSON. */
  public static class MessageSerializer implements JsonSerializer<Message> {
    /**
     * Serializes a {@link Message} into its corresponding JSON representation.
     *
     * @param message the {@link Message} to serialize
     * @param type the type of the source object
     * @param jsonSerializationContext the context of the serialization process
     * @return the JSON representation of the {@link Message}
     */
    @Override
    public JsonElement serialize(
        Message message, Type type, JsonSerializationContext jsonSerializationContext) {
      final JsonObject jsonElement = new JsonObject();
      final MessageType messageType = MessageType.fromId(message.messageType);
      jsonElement.addProperty("type", messageType.name());
      switch (messageType.getFamily()) {
        case CONTROL ->
            jsonElement.add(
                "control_message", jsonSerializationContext.serialize(message.controlMessage));
        case EXEC ->
            jsonElement.add(
                "exec_message", jsonSerializationContext.serialize(message.execMessage));
        case INTERCEPT -> {
          switch (messageType) {
            case INTERCEPT_MESSAGE:
              jsonElement.add(
                  "intercept_message",
                  jsonSerializationContext.serialize(message.interceptMessage));
              break;
            case INTERCEPT_KEY:
              jsonElement.add(
                  "intercept_key_message",
                  jsonSerializationContext.serialize(message.interceptKeyMessage));
              break;
            case INTERCEPT_RESPONSE:
              jsonElement.add(
                  "intercept_response",
                  jsonSerializationContext.serialize(message.interceptResponse));
              break;
            default:
              logger.error("Unable to serialize message of type: {}", messageType);
          }
        }
        default -> logger.error("Unable to serialize message of type: {}", messageType);
      }
      return jsonElement;
    }
  }
}
