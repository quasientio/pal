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

package net.ittera.pal.serdes.kafka.typed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.Marshallable;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.JsonRpcType;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaLogMessageSerializer implements Serializer<LogMessage<?>> {

  private static final Logger logger = LoggerFactory.getLogger(KafkaLogMessageSerializer.class);

  private static final Gson gson =
      new GsonBuilder()
          // Register any custom serializers if needed
          .create();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    // No configuration needed for this serializer
  }

  @Override
  public byte[] serialize(String topic, LogMessage<?> logMessage) {
    // Use the serialize method with Headers when possible
    return serialize(topic, null, logMessage);
  }

  @Override
  public byte[] serialize(String topic, Headers headers, LogMessage<?> logMessage) {
    if (logMessage == null) {
      return null;
    }

    Object content = logMessage.getContent();

    // this is a hack to get the unit tests to pass since the MockProducer doesn't pass in the
    // headers
    if (headers == null) {
      logger.error(
          "Headers are null! Creating RecordHeaders to avoid NPE but they will not be persisted. If this is not a test, this is a bug.");
      headers = new RecordHeaders();
    }

    byte[] data;

    if (content instanceof Message message) {
      logger.debug("Serializing Message: {}", ColferUtils.toJson(message, true));
      MessageFormatType messageFormat = MessageFormatType.COLFER;

      // Serialize using Colfer
      data = colferMessageToBytes(message);

      // Set message-format and message-type headers
      headers.add("message-format", new byte[] {messageFormat.toByte()});
      headers.add("message-type", new byte[] {message.getMessageType()});

      // set log message headers in the kafka record headers
      for (Map.Entry<String, String> entry : logMessage.getHeaders().entrySet()) {
        if (entry.getKey().endsWith("-id")) {
          // "-id" headers are byte-serialized UUIDs
          headers.add(entry.getKey(), UuidUtils.toBytes(entry.getValue()));
        } else {
          // All other headers are UTF-8 encoded strings
          headers.add(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
      }

    } else if (content instanceof JsonRpcMessage jsonRpcMessage) {
      MessageFormatType messageFormat = MessageFormatType.JSONRPC;

      // Serialize using Gson
      String json = gson.toJson(jsonRpcMessage);
      data = json.getBytes(StandardCharsets.UTF_8);

      // Set the message-format header
      headers.add("message-format", new byte[] {messageFormat.toByte()});

      // Determine and set the message-type header
      JsonRpcType messageType;
      if (jsonRpcMessage instanceof JsonRpcRequest) {
        messageType = JsonRpcType.REQUEST;
        logger.debug("Serializing JsonRpcRequest: {}", json);
      } else if (jsonRpcMessage instanceof JsonRpcResponse) {
        messageType = JsonRpcType.RESPONSE;
        logger.debug("Serializing JsonRpcResponse: {}", json);
      } else {
        throw new IllegalArgumentException(
            "Unsupported JsonRpcMessage type: " + content.getClass());
      }
      headers.add("message-type", new byte[] {messageType.toByte()});
    } else {
      throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
    }

    return data;
  }

  private static byte[] colferMessageToBytes(Marshallable message) {
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

  @Override
  public void close() {
    // No resources to close
  }
}
