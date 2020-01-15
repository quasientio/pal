package net.ittera.pal.core.exec.java;

import net.ittera.pal.messages.protobuf.Intercepts;

// @Singleton
public class BaseInterceptMessageDispatcher extends AbstractDispatcher
    implements InterceptMessageDispatcher {

  @Override
  public boolean dispatchIncoming(Intercepts.InterceptMessage interceptMessage, boolean isDirect) {
    throw new UnsupportedOperationException("This dispatcher is being phased out");
  }
}
