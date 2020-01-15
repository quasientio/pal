package net.ittera.pal.svcs;

import static java.lang.String.format;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.List;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import net.ittera.pal.messages.MessageContext;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageIndexer {

  private static final Logger logger = LoggerFactory.getLogger(MessageIndexer.class);

  private final String indexingServerUrl;
  private final JsonFormat.Printer protobufJsonPrinter =
      JsonFormat.printer().omittingInsignificantWhitespace();

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
          try {
            queryB.append(printMessage(msg)).append("\n");
          } catch (InvalidProtocolBufferException e) {
            logger.error("protobuf parse error", e);
          }
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
    try {
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
    } catch (InvalidProtocolBufferException e) {
      logger.error("protobuf parse error", e);
    }
  }

  private static String getMessageUuid(Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMessageUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getMessageUuid();
      //    } else if (msg.hasInterceptReply()) {
      //      return msg.getInterceptReply().getMessageUuid();
    }
    return null;
  }

  private String printMessage(Message msg) throws InvalidProtocolBufferException {
    if (msg.hasExecMessage()) {
      return protobufJsonPrinter.print(msg.getExecMessage());
    } else if (msg.hasInterceptMessage()) {
      return protobufJsonPrinter.print(msg.getInterceptMessage());
    } else if (msg.hasInterceptReply()) {
      return protobufJsonPrinter.print(msg.getInterceptReply());
    } else {
      throw new RuntimeException(format("unknown message type: %s", msg.toString()));
    }
  }
}
