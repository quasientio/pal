package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;
import javax.inject.Singleton;

@Singleton
public class BaseInterceptMessageDispatcher extends AbstractDispatcher
    implements InterceptMessageDispatcher {

  @Override
  public boolean dispatchIncoming(InterceptRequest interceptRequest, boolean isDirect) {
    final int reply = connector.registerIntercept(interceptRequest);
    return reply == 0;
  }
}
