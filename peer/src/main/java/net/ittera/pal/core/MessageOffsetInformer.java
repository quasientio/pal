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

  private final String messageId;
  private final Socket offsetPubSocket;

  private static final Logger logger = LoggerFactory.getLogger(MessageOffsetInformer.class);

  MessageOffsetInformer(String messageId, Socket offsetPubSocket) {
    this.messageId = messageId;
    this.offsetPubSocket = offsetPubSocket;
  }

  /**
   * Kafka producer Callback interface. Called when the record has been acknowledged by the server.
   *
   * @param recordMetadata metadata for the record that was sent (i.e. the partition and offset)
   * @param e The exception thrown during processing of this record. Null if no error occurred.
   */
  @Override
  public void onCompletion(RecordMetadata recordMetadata, Exception e) {
    // publish new record offset
    if (logger.isDebugEnabled()) {
      logger.debug("New offset {} for message w/id: {}", recordMetadata.offset(), messageId);
    }
    PublishedOffsetMsg offsetMsg = new PublishedOffsetMsg(recordMetadata.offset(), messageId);
    offsetMsg.send(offsetPubSocket);
    complete(null);
  }
}
