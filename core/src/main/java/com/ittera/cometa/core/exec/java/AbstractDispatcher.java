package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectService;
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
  protected ObjectService objectService;
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
  final void setObjectService(ObjectService objectService) {
    this.objectService = objectService;
  }

  @Inject
  final void setConnector(DispatcherConnector connector) {
    this.connector = connector;
  }
}
