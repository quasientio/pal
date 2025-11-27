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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.quasient.pal.messages.Marshallable;
import com.quasient.pal.messages.colfer.ClInitCall;
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
import com.quasient.pal.messages.colfer.InterceptCallbackRequest;
import com.quasient.pal.messages.colfer.InterceptCallbackResponse;
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
import com.quasient.pal.serdes.colfer.JsonSerializers.ClInitCallSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ClassMethodCallSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ClassSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ConstructorCallSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ConstructorSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ContextSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ControlMessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ExecMessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.FieldSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InstanceFieldGetSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InstanceFieldPutDoneAdapter;
import com.quasient.pal.serdes.colfer.JsonSerializers.InstanceFieldPutSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InstanceMethodCallSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptCallbackRequestSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptCallbackResponseSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptKeyMessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptMessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptResponseSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptableFieldSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InterceptableMethodSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.InternalHeaderSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.MessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.MetaMessageSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.MethodSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ObjSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ParameterSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.RaisedThrowableSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ReflectableSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ReturnValueAdapter;
import com.quasient.pal.serdes.colfer.JsonSerializers.StaticFieldGetSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.StaticFieldPutDoneAdapter;
import com.quasient.pal.serdes.colfer.JsonSerializers.StaticFieldPutSerializer;
import com.quasient.pal.serdes.colfer.JsonSerializers.ThrowableSerializer;

/**
 * Utility class for serializing and deserializing {@link Marshallable} messages using Colfer and
 * Gson.
 *
 * <p>Provides methods to convert messages to byte arrays or JSON strings, and to deserialize JSON
 * strings back into message instances. Supports both compact and pretty-printed JSON formats.
 */
public class ColferUtils {

  /** Gson instance for compact JSON serialization without pretty printing. */
  private static final Gson jsonPrinter;

  /** Gson instance for JSON serialization with pretty printing enabled. */
  private static final Gson jsonPrettyPrinter;

  static {
    jsonPrinter = createJsonPrinter(false);
    jsonPrettyPrinter = createJsonPrinter(true);
  }

  /** Private constructor to prevent instantiation of this utility class. */
  private ColferUtils() {}

  /**
   * Creates a {@link Gson} instance configured for JSON serialization of {@link Marshallable}
   * messages.
   *
   * @param prettyPrint if {@code true}, the Gson instance will format JSON output with indentation
   *     for readability; otherwise, it will produce compact JSON.
   * @return a configured {@link Gson} instance for serializing messages.
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static Gson createJsonPrinter(boolean prettyPrint) {
    final GsonBuilder printerBuilder =
        new GsonBuilder()
            // Message (wrapper)
            .registerTypeAdapter(Message.class, new MessageSerializer())
            // Exec Message
            .registerTypeAdapter(ExecMessage.class, new ExecMessageSerializer())
            .registerTypeAdapter(ConstructorCall.class, new ConstructorCallSerializer())
            .registerTypeAdapter(InstanceMethodCall.class, new InstanceMethodCallSerializer())
            .registerTypeAdapter(ClInitCall.class, new ClInitCallSerializer())
            .registerTypeAdapter(ClassMethodCall.class, new ClassMethodCallSerializer())
            .registerTypeAdapter(Context.class, new ContextSerializer())
            .registerTypeAdapter(Throwable.class, new ThrowableSerializer())
            .registerTypeAdapter(RaisedThrowable.class, new RaisedThrowableSerializer())
            .registerTypeAdapter(StaticFieldGet.class, new StaticFieldGetSerializer())
            .registerTypeAdapter(StaticFieldPut.class, new StaticFieldPutSerializer())
            .registerTypeAdapter(StaticFieldPutDone.class, new StaticFieldPutDoneAdapter())
            .registerTypeAdapter(InstanceFieldGet.class, new InstanceFieldGetSerializer())
            .registerTypeAdapter(InstanceFieldPut.class, new InstanceFieldPutSerializer())
            .registerTypeAdapter(InstanceFieldPutDone.class, new InstanceFieldPutDoneAdapter())
            .registerTypeAdapter(InternalHeader.class, new InternalHeaderSerializer())
            .registerTypeAdapter(InterceptableMethod.class, new InterceptableMethodSerializer())
            .registerTypeAdapter(InterceptableField.class, new InterceptableFieldSerializer())
            .registerTypeAdapter(
                com.quasient.pal.messages.colfer.Class.class, new ClassSerializer())
            .registerTypeAdapter(Obj.class, new ObjSerializer())
            .registerTypeAdapter(Field.class, new FieldSerializer())
            .registerTypeAdapter(Method.class, new MethodSerializer())
            .registerTypeAdapter(Constructor.class, new ConstructorSerializer())
            .registerTypeAdapter(Reflectable.class, new ReflectableSerializer())
            .registerTypeAdapter(Parameter.class, new ParameterSerializer())
            .registerTypeAdapter(ReturnValue.class, new ReturnValueAdapter())
            // Control Message
            .registerTypeAdapter(ControlMessage.class, new ControlMessageSerializer())
            // Control Message
            .registerTypeAdapter(MetaMessage.class, new MetaMessageSerializer())
            // Intercept
            .registerTypeAdapter(InterceptMessage.class, new InterceptMessageSerializer())
            .registerTypeAdapter(InterceptKeyMessage.class, new InterceptKeyMessageSerializer())
            .registerTypeAdapter(InterceptResponse.class, new InterceptResponseSerializer())
            .registerTypeAdapter(
                InterceptCallbackRequest.class, new InterceptCallbackRequestSerializer())
            .registerTypeAdapter(
                InterceptCallbackResponse.class, new InterceptCallbackResponseSerializer());

    if (prettyPrint) {
      printerBuilder.setPrettyPrinting();
    }

    return printerBuilder.create();
  }

  /**
   * Serializes the given {@link Marshallable} message into a byte array.
   *
   * <p>The method calculates the required buffer size, marshals the message into the buffer, and
   * returns a byte array containing the serialized data. If the serialized data is smaller than the
   * calculated buffer size, a trimmed array is returned.
   *
   * @param message the {@link Marshallable} message to serialize
   * @return a byte array containing the serialized message, or {@code null} if the message is null
   */
  public static byte[] toBytes(Marshallable message) {
    if (message == null) {
      return null;
    }

    final int maxSize = message.marshalFit();
    final byte[] buf = new byte[maxSize];
    final int finalIdx = message.marshal(buf, 0);
    if (finalIdx < maxSize) {
      byte[] trimmed = new byte[finalIdx];
      System.arraycopy(buf, 0, trimmed, 0, finalIdx);
      return trimmed;
    }
    return buf;
  }

  /**
   * Serializes the given {@link Marshallable} message into a JSON string.
   *
   * <p>This method produces compact JSON output without indentation.
   *
   * @param message the {@link Marshallable} message to serialize
   * @return a JSON string representing the serialized message
   */
  public static String toJson(Marshallable message) {
    return toJson(message, false);
  }

  /**
   * Serializes the given {@link Marshallable} message into a JSON string.
   *
   * @param message the {@link Marshallable} message to serialize
   * @param prettyPrint if {@code true}, the resulting JSON string will be formatted with
   *     indentation for readability; otherwise, it will be compact
   * @return a JSON string representing the serialized message
   */
  public static String toJson(Marshallable message, boolean prettyPrint) {
    if (prettyPrint) {
      return jsonPrettyPrinter.toJson(message);
    }
    return jsonPrinter.toJson(message);
  }

  /**
   * Deserializes a JSON string into an instance of the specified {@link Marshallable} class.
   *
   * @param json the JSON string to deserialize
   * @param messageClass the {@link Class} object of the target {@link Marshallable} type
   * @return an instance of {@code messageClass} populated with data from the JSON string
   * @throws JsonParseException if the JSON is invalid or deserialization fails
   */
  public static Marshallable fromJson(String json, Class<? extends Marshallable> messageClass)
      throws JsonParseException {
    Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
    try {
      Marshallable messageInstance = messageClass.getDeclaredConstructor().newInstance();
      return messageInstance.fromJson(jsonObject);
    } catch (Exception e) {
      throw new JsonParseException(
          "Error instantiating or deserializing class: " + e.getMessage(), e);
    }
  }

  /**
   * Wraps a {@link Marshallable} message to defer JSON formatting until {@code toString()} is
   * called.
   *
   * <p>This is useful for optimizing logging by avoiding unnecessary serialization.
   *
   * @param message the message to format
   * @return a lazily-formatted wrapper that serializes the message to JSON upon calling {@code
   *     toString()}
   */
  public static Object format(Marshallable message) {
    return new Object() {
      @Override
      public String toString() {
        return toJson(message, false);
      }
    };
  }
}
