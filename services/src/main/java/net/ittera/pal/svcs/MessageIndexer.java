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

package net.ittera.pal.svcs;

import static java.lang.String.format;
import static net.ittera.pal.serdes.colfer.ColferUtils.toJson;

import java.util.List;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import net.ittera.pal.messages.MessageContext;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.types.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageIndexer {

  private static final Logger logger = LoggerFactory.getLogger(MessageIndexer.class);

  private final String indexingServerUrl;

  MessageIndexer(String indexingServerUrl) {
    this.indexingServerUrl = indexingServerUrl;
    logger.info("initialized MessageIndexer with server URL: {}", indexingServerUrl);
  }

  void bulkIndex(String logName, List<Map<String, Object>> messagesWithContext) {
    if (logger.isDebugEnabled()) {
      logger.debug("bulk-indexing {} messages", messagesWithContext.size());
    }

    final StringBuilder queryB = new StringBuilder();
    messagesWithContext.forEach(
        map -> {
          Message msg = (Message) map.get("message");
          MessageContext ctxt = (MessageContext) map.get("context");
          queryB
              .append(
                  String.format(
                      "{ \"create\" : { \"_index\" : \"%s\", \"_id\" : \"%d\" } }",
                      logName, ctxt.getOffset()))
              .append("\n");
          queryB.append(printMessage(msg)).append("\n");
        });

    HttpResponse<JsonNode> response;
    String bulkUrl = String.format("%s/_bulk", indexingServerUrl);
    response =
        Unirest.put(bulkUrl)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-ndjson")
            .body(queryB.toString())
            .asJson();

    debugLog(queryB.toString(), response);
  }

  void index(String logName, long offset, Message message) {
    if (logger.isDebugEnabled()) {
      logger.debug("indexing message with uuid: {}", getMessageUuid(message));
    }

    final String id = String.valueOf(offset);
    String putQuery = String.format("%s/%s/_create/%s", indexingServerUrl, logName, id);

    HttpResponse<JsonNode> response;
    response =
        Unirest.put(putQuery)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(printMessage(message))
            .asJson();
    debugLog(putQuery, response);
  }

  private static String getMessageUuid(Message msg) {
    final MessageType messageType = MessageType.fromByte(msg.getMessageType());
    return switch (messageType) {
      case EXEC_MESSAGE -> msg.getExecMessage().getMessageUuid();
      case INTERCEPT_MESSAGE -> msg.getInterceptMessage().getMessageUuid();
      default -> null;
    };
  }

  private String printMessage(Message msg) {
    final MessageType messageType = MessageType.fromByte(msg.getMessageType());
    return switch (messageType) {
      case EXEC_MESSAGE -> toJson(msg.getExecMessage(), true);
      case INTERCEPT_MESSAGE -> toJson(msg.getInterceptMessage(), true);
      case INTERCEPT_REPLY -> toJson(msg.getInterceptReply(), true);
      default -> throw new RuntimeException(format("unknown message type: %s", msg));
    };
  }

  private void debugLog(String query, HttpResponse<JsonNode> response) {
    if (logger.isDebugEnabled()) {
      logger.debug("sent query:\n{}", query);
      logger.debug("response status: {}", response.getStatusText());
      logger.debug("response body: {}", response.getBody());
      logger.debug("errors?: {}", response.getBody().getObject().get("errors"));
    }
  }
}
