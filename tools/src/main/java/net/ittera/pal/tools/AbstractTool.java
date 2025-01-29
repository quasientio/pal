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

package net.ittera.pal.tools;

import static net.ittera.pal.serdes.colfer.ExecMessageSummaryUtil.getOneLinerSummary;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageSummaryUtil.getOneLinerSummary;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.getMessageType;

import java.util.Locale;
import javax.annotation.Nullable;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.types.MessageFamily;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.jsonrpc.JsonRpcSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractTool {

  private final Logger logger = LoggerFactory.getLogger(AbstractTool.class);

  protected AbstractTool() {}

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

  protected static String getPeerUuid(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return switch (messageType.getFamily()) {
      case EXEC -> msg.getExecMessage().getPeerUuid();
      case INTERCEPT -> msg.getInterceptMessage().getPeerUuid();
      default -> null;
    };
  }

  protected static String getMessageId(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return switch (messageType.getFamily()) {
      case EXEC -> msg.getExecMessage().getMessageId();
      case INTERCEPT -> msg.getInterceptMessage().getMessageId();
      default -> null;
    };
  }

  protected static String getMessageTypeName(Message msg) {
    final MessageType messageType = MessageType.fromId(msg.getMessageType());
    return messageType.name();
  }

  protected static String getMessageTypeName(LogMessage<?> message) {
    if (isBinaryRpc(message)) {
      return getMessageTypeName((Message) message.getContent());
    } else if (isJsonRpc(message)) {
      return getMessageType((JsonRpcMessage) message.getContent()).name();
    }
    return null;
  }

  protected static String getMessageFormat(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      return "BINARY";
    } else if (isJsonRpc(logMessage)) {
      return "JSON";
    }
    return null;
  }

  protected static boolean isBinaryRpc(LogMessage<?> logMessage) {
    return logMessage.getContent() instanceof Message;
  }

  protected static boolean isJsonRpc(LogMessage<?> logMessage) {
    return logMessage.getContent() instanceof JsonRpcMessage;
  }

  protected static String getId(LogMessage<?> logMessage) {
    if (isBinaryRpc(logMessage)) {
      return getMessageId((Message) logMessage.getContent());
    } else if (isJsonRpc(logMessage)) {
      return ((JsonRpcMessage) logMessage.getContent()).getId();
    }
    return null;
  }

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
