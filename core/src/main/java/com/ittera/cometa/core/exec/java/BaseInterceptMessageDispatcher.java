package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Intercepts;
import javax.inject.Singleton;

@Singleton
public class BaseInterceptMessageDispatcher extends AbstractDispatcher
    implements InterceptMessageDispatcher {

  @Override
  public boolean dispatchIncoming(Intercepts.InterceptMessage interceptMessage, boolean isDirect) {
    final int reply = connector.registerIntercept(interceptMessage);
    return reply == 0;
  }
}
