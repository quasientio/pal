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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.colfer.Reflectable;
import net.ittera.pal.messages.types.MessageType;

/**
 * Provides utility methods for extracting information from {@link ExecMessage} instances.
 *
 * <p>This class offers methods to retrieve class names, executable names, parameter types, and
 * message identifiers based on the type of execution message. It facilitates the handling and
 * interpretation of different execution scenarios encapsulated within {@link ExecMessage} objects.
 */
public class ExecMessageUtils {

  /**
   * Retrieves the fully qualified class name associated with the given {@link ExecMessage}.
   *
   * @param execMessage the execution message from which to extract the class name
   * @return the name of the class involved in the execution, or the type of the raised throwable
   * @throws IllegalArgumentException if the message type is unsupported
   */
  public static String getClassname(ExecMessage execMessage) {
    final MessageType msgType = getMessageTypeOf(execMessage);
    return switch (msgType) {
      case EXEC_CONSTRUCTOR -> execMessage.getConstructorCall().getClazz().getName();
      case EXEC_INSTANCE_METHOD -> execMessage.getInstanceMethodCall().getClazz().getName();
      case EXEC_CLASS_METHOD -> execMessage.getClassMethodCall().getClazz().getName();
      case EXEC_GET_STATIC -> execMessage.getStaticFieldGet().getClazz().getName();
      case EXEC_GET_FIELD -> execMessage.getInstanceFieldGet().getClazz().getName();
      case EXEC_PUT_STATIC -> execMessage.getStaticFieldPut().getClazz().getName();
      case EXEC_PUT_FIELD -> execMessage.getInstanceFieldPut().getClazz().getName();
      case EXEC_PUT_FIELD_DONE -> execMessage.getInstanceFieldPutDone().getClazz().getName();
      case EXEC_PUT_STATIC_DONE -> execMessage.getStaticFieldPutDone().getClazz().getName();
      case EXEC_THROWABLE -> execMessage.getRaisedThrowable().getThrowable().getType();
      case EXEC_RETURN_VALUE ->
          execMessage.getReturnValue().getIsVoid()
              ? "void"
              : execMessage.getReturnValue().getObject().getClazz().getName();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", msgType));
    };
  }

  /**
   * Retrieves the name of the executable (constructor, method, or field) from the given {@link
   * ExecMessage}.
   *
   * @param execMessage the execution message from which to extract the executable name
   * @return the name of the executable, such as a constructor name, method name, or field name
   * @throws IllegalArgumentException if the message type is unsupported
   */
  public static String getExecutableName(ExecMessage execMessage) {
    final MessageType execMessageType = getMessageTypeOf(execMessage);
    return switch (execMessageType) {
      case EXEC_CONSTRUCTOR -> "new";
      case EXEC_INSTANCE_METHOD -> execMessage.getInstanceMethodCall().getName();
      case EXEC_CLASS_METHOD -> execMessage.getClassMethodCall().getName();
      case EXEC_GET_STATIC -> execMessage.getStaticFieldGet().getField().getName();
      case EXEC_GET_FIELD -> execMessage.getInstanceFieldGet().getField().getName();
      case EXEC_PUT_STATIC -> execMessage.getStaticFieldPut().getField().getName();
      case EXEC_PUT_FIELD -> execMessage.getInstanceFieldPut().getField().getName();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", execMessageType));
    };
  }

  /**
   * Retrieves the name of the executable from which the current executable is invoked.
   *
   * @param execMessage the execution message from which to extract the source executable name
   * @return the name of the source executable, or {@code null} if not applicable
   * @throws IllegalArgumentException if the message type is unsupported
   */
  public static String getFromExecutableName(ExecMessage execMessage) {
    final MessageType execMessageType = getMessageTypeOf(execMessage);
    return switch (execMessageType) {
      case EXEC_PUT_FIELD_DONE -> execMessage.getInstanceFieldPutDone().getField().getName();
      case EXEC_PUT_STATIC_DONE -> execMessage.getStaticFieldPutDone().getField().getName();
      case EXEC_RETURN_VALUE -> {
        if (execMessage.getReturnValue().getFrom() == null) {
          yield null;
        }
        yield getFromReflectableName(execMessage.getReturnValue().getFrom());
      }
      case EXEC_THROWABLE -> {
        if (execMessage.getRaisedThrowable().getFrom() == null) {
          yield null;
        }
        yield getFromReflectableName(execMessage.getRaisedThrowable().getFrom());
      }
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", execMessageType));
    };
  }

  /**
   * Retrieves the fully qualified class name of the executable from which the current executable is
   * invoked.
   *
   * @param execMessage the execution message from which to extract the source executable class name
   * @return the class name of the source executable, or {@code null} if not applicable
   * @throws IllegalArgumentException if the message type is unsupported
   */
  public static String getFromExecutableClassName(ExecMessage execMessage) {
    final MessageType execMessageType = getMessageTypeOf(execMessage);
    return switch (execMessageType) {
      case EXEC_PUT_FIELD_DONE -> execMessage.getInstanceFieldPutDone().getClazz().getName();
      case EXEC_PUT_STATIC_DONE -> execMessage.getStaticFieldPutDone().getClass().getName();
      case EXEC_RETURN_VALUE -> {
        if (execMessage.getReturnValue().getFrom() == null) {
          yield null;
        }
        yield getFromReflectableClassName(execMessage.getReturnValue().getFrom());
      }
      case EXEC_THROWABLE -> {
        if (execMessage.getRaisedThrowable().getFrom() == null) {
          yield null;
        }
        yield getFromReflectableClassName(execMessage.getRaisedThrowable().getFrom());
      }
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", execMessageType));
    };
  }

  /**
   * Extracts the name from a {@link Reflectable} instance.
   *
   * @param from the reflectable instance from which to extract the name
   * @return the name of the constructor, method, or field, or {@code null} if none is present
   */
  private static String getFromReflectableName(Reflectable from) {
    if (from.getConstructor() != null) {
      return "new";
    } else if (from.getMethod() != null) {
      return from.getMethod().getName();
    } else if (from.getField() != null) {
      return from.getField().getName();
    }
    return null;
  }

  /**
   * Extracts the fully qualified class name from a {@link Reflectable} instance.
   *
   * @param from the reflectable instance from which to extract the class name
   * @return the class name of the constructor, method, or field, or {@code null} if none is present
   */
  private static String getFromReflectableClassName(Reflectable from) {
    if (from.getConstructor() != null) {
      return from.getConstructor().getClazz().getName();
    } else if (from.getMethod() != null) {
      return from.getMethod().getClazz().getName();
    } else if (from.getField() != null) {
      return from.getField().getClazz().getName();
    }
    return null;
  }

  /**
   * Retrieves the parameter types from the given {@link ExecMessage}.
   *
   * @param execMessage the execution message from which to extract parameter types
   * @return {@code null} if the message is not a constructor or method call; otherwise, a list of
   *     parameter class names, which may be empty if there are no parameters
   */
  public static List<String> getParameterTypes(ExecMessage execMessage) {
    final MessageType execMessageType = getMessageTypeOf(execMessage);
    Parameter[] params;
    switch (execMessageType) {
      case EXEC_CONSTRUCTOR:
        params = execMessage.getConstructorCall().getParameters();
        break;
      case EXEC_INSTANCE_METHOD:
        params = execMessage.getInstanceMethodCall().getParameters();
        break;
      case EXEC_CLASS_METHOD:
        params = execMessage.getClassMethodCall().getParameters();
        break;
      default:
        return null;
    }

    if (params != null && params.length > 0) {
      return Arrays.stream(params)
          .map(
              param -> {
                if (param.getValue().getClazz() == null
                    || param.getValue().getClazz().getName().isEmpty()) {
                  return null;
                } else {
                  return param.getValue().getClazz().getName();
                }
              })
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  /**
   * Determines the {@link MessageType} of the given {@link ExecMessage}.
   *
   * @param execMessage the execution message to evaluate
   * @return the corresponding {@link MessageType} of the execution message
   * @throws IllegalArgumentException if the message type is unknown or unsupported
   */
  public static MessageType getMessageTypeOf(ExecMessage execMessage) {
    if (execMessage.getConstructorCall() != null) {
      return MessageType.EXEC_CONSTRUCTOR;
    } else if (execMessage.getInstanceMethodCall() != null) {
      return MessageType.EXEC_INSTANCE_METHOD;
    } else if (execMessage.getClassMethodCall() != null) {
      return MessageType.EXEC_CLASS_METHOD;
    } else if (execMessage.getStaticFieldGet() != null) {
      return MessageType.EXEC_GET_STATIC;
    } else if (execMessage.getStaticFieldPut() != null) {
      return MessageType.EXEC_PUT_STATIC;
    } else if (execMessage.getInstanceFieldGet() != null) {
      return MessageType.EXEC_GET_FIELD;
    } else if (execMessage.getInstanceFieldPut() != null) {
      return MessageType.EXEC_PUT_FIELD;
    } else if (execMessage.getInstanceFieldPutDone() != null) {
      return MessageType.EXEC_PUT_FIELD_DONE;
    } else if (execMessage.getStaticFieldPutDone() != null) {
      return MessageType.EXEC_PUT_STATIC_DONE;
    } else if (execMessage.getReturnValue() != null) {
      return MessageType.EXEC_RETURN_VALUE;
    } else if (execMessage.getRaisedThrowable() != null) {
      return MessageType.EXEC_THROWABLE;
    } else {
      throw new IllegalArgumentException("Unknown message type");
    }
  }

  /**
   * Retrieves the message identifier from the given {@link Message}.
   *
   * @param msg the message from which to extract the identifier
   * @return the message ID associated with the message's type
   */
  public static String getMessageId(Message msg) {
    MessageType messageType = MessageType.fromId(msg.getMessageType());
    return switch (messageType.getFamily()) {
      case CONTROL -> msg.getControlMessage().getMessageId();
      case EXEC -> msg.getExecMessage().getMessageId();
      case INTERCEPT -> msg.getInterceptMessage().getMessageId();
      case META -> msg.getMetaMessage().getMessageId();
    };
  }
}
