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

public class ColferUtils {

  private static final Gson jsonPrinter;
  private static final Gson jsonPrettyPrinter;

  static {
    jsonPrinter = createJsonPrinter(false);
    jsonPrettyPrinter = createJsonPrinter(true);
  }

  private ColferUtils() {}

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

  public static String toJson(Marshallable message) {
    return toJson(message, false);
  }

  public static String toJson(Marshallable message, boolean prettyPrint) {
    if (prettyPrint) {
      return jsonPrettyPrinter.toJson(message);
    }
    return jsonPrinter.toJson(message);
  }

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
   * Returns a wrapper around a message, that is formatted lazily, for use in logging calls.
   *
   * @param message the message to format
   * @return a wrapper object that will format the message when its toString method is called
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
