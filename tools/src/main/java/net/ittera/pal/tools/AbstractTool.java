package net.ittera.pal.tools;

import net.ittera.pal.messages.protobuf.Wrappers;

public class AbstractTool {
  protected static String getPeerUuid(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getPeerUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getPeerUuid();
    }
    return null;
  }

  protected static String getMessageUuid(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMessageUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getMessageUuid();
      //    } else if (msg.hasInterceptReply()) {
      //      return msg.getInterceptReply().getMessageUuid();
    }
    return null;
  }

  protected static String getMessageType(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMsgType().name();
    } else if (msg.hasInterceptMessage()) {
      return "InterceptMessage";
    } else if (msg.hasInterceptReply()) {
      return "InterceptReply";
    }
    return null;
  }
}
