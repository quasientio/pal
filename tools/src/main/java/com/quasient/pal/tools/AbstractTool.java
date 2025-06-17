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

package com.quasient.pal.tools;

import static com.quasient.pal.serdes.colfer.ExecMessageSummaryUtil.getOneLinerSummary;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageSummaryUtil.getOneLinerSummary;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.getMessageType;

import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.messages.types.MessageFamily;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import java.util.Locale;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides common utility methods for tool-related operations within the PAL runtime. */
public class AbstractTool {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(AbstractTool.class);

  /**
   * Constructs an AbstractTool instance.
   *
   * <p>This constructor is protected to allow subclassing and prevents direct instantiation of the
   * AbstractTool class.
   */
  protected AbstractTool() {}

  /**
   * Retrieves the value of a specified system property or environment variable.
   *
   * <p>The method first checks for the system property with the given name. If not found, it looks
   * for an environment variable by converting the property name to uppercase. If both are absent,
   * the provided default value is returned.
   *
   * @param propertyName the name of the property to retrieve
   * @param defaultValue the default value to return if the property is not set; may be {@code null}
   * @return the value of the property from system properties, environment variables, or the default
   *     value, or {@code null} if none are set
   */
  protected String getProperty(String propertyName, @Nullable String defaultValue) {
    if (System.getProperty(propertyName) != null) {
      logger.debug("loading value of '{}' from system properties", propertyName);
      return System.getProperty(propertyName);
    } else if (System.getenv(propertyName.toUpperCase(Locale.getDefault())) != null) {
      logger.debug("loading value of '{}' from ENV", propertyName.toUpperCase(Locale.getDefault()));
      return System.getenv(propertyName.toUpperCase(Locale.getDefault()));
    } else if (defaultValue != null) {
      logger.debug("loading value of '{}' from default", propertyName);
      return defaultValue;
    }
    return null;
  }

  /**
   * Extracts the peer UUID from a given message based on its message family.
   *
   * @param msg the message from which to extract the peer UUID
   * @return the peer UUID if available; otherwise, {@code null}
   */
  protected static String getPeerUuid(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return switch (messageType.getFamily()) {
      case EXEC -> msg.getExecMessage().getPeerUuid();
      case INTERCEPT -> msg.getInterceptMessage().getPeerUuid();
      default -> null;
    };
  }

  /**
   * Retrieves the message ID from a given message based on its message family.
   *
   * @param msg the message from which to extract the message ID
   * @return the message ID if available; otherwise, {@code null}
   */
  protected static String getMessageId(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return switch (messageType.getFamily()) {
      case EXEC -> msg.getExecMessage().getMessageId();
      case INTERCEPT -> msg.getInterceptMessage().getMessageId();
      default -> null;
    };
  }

  /**
   * Obtains the type name of a given message.
   *
   * @param msg the message whose type name is to be retrieved
   * @return the name of the message type
   */
  protected static String getMessageTypeName(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return messageType.name();
  }

  /**
   * Determines and returns the type name of the content within a log message.
   *
   * @param message the log message containing the content
   * @return the name of the message type if identifiable; otherwise, {@code null}
   */
  protected static String getMessageTypeName(LogMessage<?> message) {
    if (isBinaryRpc(message)) {
      return getMessageTypeName((Message) message.getContent());
    } else if (isJsonRpc(message)) {
      return getMessageType((JsonRpcMessage) message.getContent()).name();
    }
    return null;
  }

  /**
   * Identifies the format of the message content within a log message.
   *
   * <p>Determines whether the message content is a binary RPC or JSON-RPC and returns the
   * corresponding format string.
   *
   * @param logMessage the log message containing the content
   * @return "BINARY" if the content is a binary RPC, "JSON" if it is a JSON-RPC, or {@code null} if
   *     the format is unrecognized
   */
  protected static String getMessageFormat(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      return "BINARY";
    } else if (isJsonRpc(logMessage)) {
      return "JSON";
    }
    return null;
  }

  /**
   * Checks if the content of a log message is a binary RPC message.
   *
   * @param logMessage the log message to check
   * @return {@code true} if the message content is an instance of {@link Message}; {@code false}
   *     otherwise
   */
  protected static boolean isBinaryRpc(LogMessage<?> logMessage) {
    return logMessage.getContent() instanceof Message;
  }

  /**
   * Checks if the content of a log message is a JSON-RPC message.
   *
   * @param logMessage the log message to check
   * @return {@code true} if the message content is an instance of {@link JsonRpcMessage}; {@code
   *     false} otherwise
   */
  protected static boolean isJsonRpc(LogMessage<?> logMessage) {
    return logMessage.getContent() instanceof JsonRpcMessage;
  }

  /**
   * Retrieves the identifier from the content of a log message.
   *
   * @param logMessage the log message containing the content
   * @return the message ID if the content is a binary RPC or JSON-RPC message; otherwise, {@code
   *     null}
   */
  protected static String getId(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      return getMessageId((Message) logMessage.getContent());
    } else if (isJsonRpc(logMessage)) {
      return ((JsonRpcMessage) logMessage.getContent()).getId();
    }
    return null;
  }

  /**
   * Converts the content of a log message into a pretty-printed JSON string.
   *
   * @param logMessage the log message containing the content to be serialized
   * @return a pretty-printed JSON representation of the message content
   * @throws IllegalArgumentException if serialization fails or if the message type is unknown
   */
  protected static String getMessageContentAsPrettyJson(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      return ColferUtils.toJson((Message) logMessage.getContent(), true);
    } else if (isJsonRpc(logMessage)) {
      try {
        return JsonRpcSerializer.toPrettyJson((JsonRpcMessage) logMessage.getContent());
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to serialize JSON-RPC message", e);
      }
    } else {
      throw new IllegalArgumentException(
          "Unknown message type of class: " + logMessage.getContent().getClass());
    }
  }

  /**
   * Generates a one-line summary of the message content within a log message.
   *
   * <p>For binary RPC messages, it summarizes based on the message family. Currently, only EXEC
   * family is supported, with other types defaulting to their JSON representation.
   *
   * <p>For JSON-RPC messages, it uses appropriate utility methods to generate the summary.
   *
   * @param logMessage the log message containing the content to summarize
   * @return a one-line summary of the message content
   * @throws IllegalArgumentException if the message type is unknown
   */
  protected static String getMessageOneLiner(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      Message message = (Message) logMessage.getContent();
      MessageType messageType = MessageType.fromId(message.getMessageType());
      if (messageType.getFamily().equals(MessageFamily.EXEC)) {
        return getOneLinerSummary(message.getExecMessage());
      }
      // TODO: Add support for other message types; for now we return the 1-line Json representation
      return ColferUtils.toJson((Message) logMessage.getContent(), false);
    } else if (isJsonRpc(logMessage)) {
      return getOneLinerSummary((JsonRpcMessage) logMessage.getContent());
    } else {
      throw new IllegalArgumentException(
          "Unknown message type of class: " + logMessage.getContent().getClass());
    }
  }
}
