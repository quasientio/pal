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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.ittera.pal.messages.Marshallable;
import net.ittera.pal.messages.colfer.ClInitCall;
import net.ittera.pal.messages.colfer.ClassMethodCall;
import net.ittera.pal.messages.colfer.Constructor;
import net.ittera.pal.messages.colfer.ConstructorCall;
import net.ittera.pal.messages.colfer.Context;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Field;
import net.ittera.pal.messages.colfer.InstanceFieldGet;
import net.ittera.pal.messages.colfer.InstanceFieldPut;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.InstanceMethodCall;
import net.ittera.pal.messages.colfer.InterceptKeyMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptResponse;
import net.ittera.pal.messages.colfer.InterceptableField;
import net.ittera.pal.messages.colfer.InterceptableMethod;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.MetaMessage;
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
import net.ittera.pal.serdes.colfer.JsonSerializers.ClInitCallSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ClassMethodCallSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ClassSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ConstructorCallSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ConstructorSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ContextSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ControlMessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ExecMessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.FieldSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InstanceFieldGetSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InstanceFieldPutDoneAdapter;
import net.ittera.pal.serdes.colfer.JsonSerializers.InstanceFieldPutSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InstanceMethodCallSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InterceptKeyMessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InterceptMessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InterceptResponseSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InterceptableFieldSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InterceptableMethodSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.InternalHeaderSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.MessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.MetaMessageSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.MethodSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ObjSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ParameterSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.RaisedThrowableSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ReflectableSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ReturnValueAdapter;
import net.ittera.pal.serdes.colfer.JsonSerializers.StaticFieldGetSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.StaticFieldPutDoneAdapter;
import net.ittera.pal.serdes.colfer.JsonSerializers.StaticFieldPutSerializer;
import net.ittera.pal.serdes.colfer.JsonSerializers.ThrowableSerializer;

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
            .registerTypeAdapter(net.ittera.pal.messages.colfer.Class.class, new ClassSerializer())
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
            .registerTypeAdapter(InterceptResponse.class, new InterceptResponseSerializer());

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
