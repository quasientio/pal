package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.core.exec.UnsupportedMessageException;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IncomingMessageDispatcher {

  protected static final Logger logger = LoggerFactory.getLogger(IncomingMessageDispatcher.class);

  // constructor & method dispatchers
  @Inject private ConstructorDispatcher constructorDispatcher;
  @Inject private ClassMethodDispatcher classMethodDispatcher;
  @Inject private InstanceMethodDispatcher instanceMethodDispatcher;

  // fieldop dispatchers
  @Inject private GetClassVariableDispatcher getClassVariableDispatcher;
  @Inject private SetClassVariableDispatcher setClassVariableDispatcher;
  @Inject private GetInstanceVariableDispatcher getInstanceVariableDispatcher;
  @Inject private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

  // intercept dispatcher
  @Inject private InterceptMessageDispatcher interceptMessageDispatcher;

  /**
   * @param execMessage Message to invoke
   * @param isDirect true if message comes from this or another peer, false if it comes from a log
   * @return the returnValue message
   */
  public ExecMessage incomingCall(ExecMessage execMessage, boolean isDirect)
      throws UnsupportedMessageException {

    if (execMessage.hasConstructorCall()) {
      return constructorDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasClassMethodCall()) {
      return classMethodDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceMethodCall()) {
      return instanceMethodDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasStaticFieldGet()) {
      return getClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceFieldGet()) {
      return getInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasStaticFieldPut()) {
      return setClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceFieldPut()) {
      return setInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else {
      throw new UnsupportedMessageException(
          String.format("Incoming message ignored - no handler:%n%s", execMessage));
    }
  }

  public boolean incomingIntercept(InterceptMessage interceptMessage, boolean isDirect) {
    if (logger.isDebugEnabled()) {
      logger.debug(String.format("incomingCall with intercept msg:%n%s", interceptMessage));
    }
    return interceptMessageDispatcher.dispatchIncoming(interceptMessage, isDirect);
  }
}
