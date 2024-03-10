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

package net.ittera.pal.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.ittera.pal.core.messages.PublishedOffsetMsg;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Socket;

/**
 * Implements Kafka Producer Callback NOTE: All calls to this class must be made by same thread
 * (kafka's IO thread), since we use zmq to publish received offsets and zmq sockets aren't
 * thread-safe.
 */
class MessageOffsetInformer extends CompletableFuture<Void> implements Callback {

  private final UUID messageUuid;
  private final Socket offsetPubSocket;

  private static final Logger logger = LoggerFactory.getLogger(MessageOffsetInformer.class);

  MessageOffsetInformer(UUID messageUuid, Socket offsetPubSocket) {
    this.messageUuid = messageUuid;
    this.offsetPubSocket = offsetPubSocket;
  }

  /**
   * Kafka producer Callback interface
   *
   * @param recordMetadata
   * @param e
   */
  @Override
  public void onCompletion(RecordMetadata recordMetadata, Exception e) {
    // publish new record offset
    if (logger.isDebugEnabled()) {
      logger.debug("New offset {} for message w/uuid: {}", recordMetadata.offset(), messageUuid);
    }
    PublishedOffsetMsg offsetMsg = new PublishedOffsetMsg(recordMetadata.offset(), messageUuid);
    offsetMsg.send(offsetPubSocket);
    complete(null);
  }
}
