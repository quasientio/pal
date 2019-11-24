package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectStore;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import java.util.UUID;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractDispatcher {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  protected UUID peerUuid;
  protected MessageBuilder messageBuilder;
  protected ObjectStore objectStore;
  protected DispatcherConnector connector;

  @Inject
  final void setPeerUuid(UUID peerUuid) {
    this.peerUuid = peerUuid;
  }

  @Inject
  final void setMessageBuilder(MessageBuilder messageBuilder) {
    this.messageBuilder = messageBuilder;
  }

  @Inject
  final void setObjectStore(ObjectStore objectStore) {
    this.objectStore = objectStore;
  }

  @Inject
  final void setConnector(DispatcherConnector connector) {
    this.connector = connector;
  }
}
