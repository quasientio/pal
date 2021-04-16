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
import static net.ittera.pal.serdes.colfer.ColferUtils.toJSON;

import java.util.List;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import net.ittera.pal.messages.MessageContext;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.colfer.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageIndexer {

  private static final Logger logger = LoggerFactory.getLogger(MessageIndexer.class);

  private final String indexingServerUrl;

  MessageIndexer(String indexingServerUrl) {
    this.indexingServerUrl = indexingServerUrl;
    logger.info("initialized MessageIndexer with server URL: {}", indexingServerUrl);
  }

  void bulkIndex(String logName, List<Map> msgsWithCtx) {
    if (logger.isDebugEnabled()) {
      logger.debug("bulk-indexing {} messages", msgsWithCtx.size());
    }

    final StringBuilder queryB = new StringBuilder();
    msgsWithCtx.forEach(
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

    HttpResponse<JsonNode> response = null;
    String bulkUrl = String.format("%s/_bulk", indexingServerUrl);
    response =
        Unirest.put(bulkUrl)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-ndjson")
            .body(queryB.toString())
            .asJson();

    if (logger.isDebugEnabled()) {
      logger.debug("sent bulk query:\n{}", queryB.toString());
      logger.debug("response status: {}", response.getStatusText());
      logger.debug("errors?: {}", response.getBody().getObject().get("errors"));
    }
  }

  void index(String logName, long offset, Message message) {
    if (logger.isDebugEnabled()) {
      logger.debug("indexing message with uuid: {}", getMessageUuid(message));
    }

    final String id = String.valueOf(offset);

    String putQuery = String.format("%s/%s/_create/%s", indexingServerUrl, logName, id);
    if (logger.isDebugEnabled()) {
      logger.debug("put query: {}", putQuery);
    }

    HttpResponse<JsonNode> response;
    response =
        Unirest.put(putQuery)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(printMessage(message))
            .asJson();
    if (logger.isDebugEnabled()) {
      logger.debug("response status: {}", response.getStatusText());
      logger.debug("response body: {}", response.getBody());
    }
  }

  private static String getMessageUuid(Message msg) {
    final MessageType messageType = MessageType.values()[msg.getMessageType()];
    switch (messageType) {
      case ExecMessage:
        return msg.getExecMessage().getMessageUuid();
      case InterceptMessage:
        return msg.getInterceptMessage().getMessageUuid();
      default:
        return null;
    }
  }

  private String printMessage(Message msg) {
    final MessageType messageType = MessageType.values()[msg.getMessageType()];
    switch (messageType) {
      case ExecMessage:
        return toJSON(msg.getExecMessage(), true);
      case InterceptMessage:
        return toJSON(msg.getInterceptMessage(), true);
      case InterceptReply:
        return toJSON(msg.getInterceptReply(), true);
      default:
        throw new RuntimeException(format("unknown message type: %s", msg.toString()));
    }
  }
}
