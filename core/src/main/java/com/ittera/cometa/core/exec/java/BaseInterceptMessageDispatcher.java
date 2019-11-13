package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Intercepts;

// @Singleton
public class BaseInterceptMessageDispatcher extends AbstractDispatcher
    implements InterceptMessageDispatcher {

  @Override
  public boolean dispatchIncoming(Intercepts.InterceptMessage interceptMessage, boolean isDirect) {
    throw new UnsupportedOperationException("This dispatcher is being phased out");
  }
}
