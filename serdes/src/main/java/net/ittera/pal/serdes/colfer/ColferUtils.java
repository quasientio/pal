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
import net.ittera.pal.serdes.colfer.JSONSerializers.ClInitCallSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ClassMethodCallSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ClassSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ConstructorCallSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ConstructorSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ContextSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ExecMessageSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.FieldSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InstanceFieldGetSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InstanceFieldPutDoneSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InstanceFieldPutSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InstanceMethodCallSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InterceptKeyMessageSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InterceptMessageSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InterceptReplySerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InterceptableFieldSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InterceptableMethodSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.InternalHeaderSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.MessageSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.MethodSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ObjSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ParameterSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.RaisedThrowableSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ReflectableSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ReturnValueSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.StaticFieldGetSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.StaticFieldPutDoneSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.StaticFieldPutSerializer;
import net.ittera.pal.serdes.colfer.JSONSerializers.ThrowableSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColferUtils {

  private static final Logger logger = LoggerFactory.getLogger(ColferUtils.class);
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
            .registerTypeAdapter(StaticFieldPutDone.class, new StaticFieldPutDoneSerializer())
            .registerTypeAdapter(InstanceFieldGet.class, new InstanceFieldGetSerializer())
            .registerTypeAdapter(InstanceFieldPut.class, new InstanceFieldPutSerializer())
            .registerTypeAdapter(InstanceFieldPutDone.class, new InstanceFieldPutDoneSerializer())
            .registerTypeAdapter(InternalHeader.class, new InternalHeaderSerializer())
            .registerTypeAdapter(InterceptableMethod.class, new InterceptableMethodSerializer())
            .registerTypeAdapter(InterceptableField.class, new InterceptableFieldSerializer())
            .registerTypeAdapter(InterceptMessage.class, new InterceptMessageSerializer())
            .registerTypeAdapter(InterceptKeyMessage.class, new InterceptKeyMessageSerializer())
            .registerTypeAdapter(InterceptReply.class, new InterceptReplySerializer())
            .registerTypeAdapter(net.ittera.pal.messages.colfer.Class.class, new ClassSerializer())
            .registerTypeAdapter(Obj.class, new ObjSerializer())
            .registerTypeAdapter(Field.class, new FieldSerializer())
            .registerTypeAdapter(Method.class, new MethodSerializer())
            .registerTypeAdapter(Constructor.class, new ConstructorSerializer())
            .registerTypeAdapter(Reflectable.class, new ReflectableSerializer())
            .registerTypeAdapter(Parameter.class, new ParameterSerializer())
            .registerTypeAdapter(ReturnValue.class, new ReturnValueSerializer())
            .registerTypeAdapter(Message.class, new MessageSerializer());

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

  public static String toJSON(Marshallable message) {
    return toJSON(message, false);
  }

  public static String toJSON(Marshallable message, boolean prettyPrint) {
    if (prettyPrint) {
      return jsonPrettyPrinter.toJson(message);
    }
    return jsonPrinter.toJson(message);
  }

  public static Marshallable fromJSON(String json, Class<? extends Marshallable> messageClass)
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
   * @param message
   * @return
   */
  public static Object format(Marshallable message) {
    return new Object() {
      @Override
      public String toString() {
        return '\n' + toJSON(message, false);
      }
    };
  }
}
