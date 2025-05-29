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
 * A Kafka producer callback that extends CompletableFuture to facilitate asynchronous notification
 * upon record acknowledgement. Once the broker acknowledges a message, this callback publishes the
 * new offset via a ZeroMQ socket. All interactions with the ZeroMQ socket must occur from the Kafka
 * I/O thread due to thread-safety constraints of ZeroMQ.
 */
class MessageOffsetInformer extends CompletableFuture<Void> implements Callback {

  /**
   * Unique identifier for the Kafka message associated with this callback. Used to correlate Log
   * messages with specific message operations.
   */
  private final String messageId;

  /**
   * ZeroMQ socket used to publish offset update messages. This socket should only be accessed from
   * the thread that instantiated this callback.
   */
  private final Socket offsetPubSocket;

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessageOffsetInformer.class);

  /**
   * Constructs a new MessageOffsetInformer with the specified message identifier and ZeroMQ socket.
   * This callback will utilize the provided socket to send offset updates upon Kafka record
   * acknowledgement.
   *
   * @param messageId the unique identifier for the message; used for logging and tracking purposes
   * @param offsetPubSocket the ZeroMQ socket designated for publishing offset messages
   */
  MessageOffsetInformer(String messageId, Socket offsetPubSocket) {
    this.messageId = messageId;
    this.offsetPubSocket = offsetPubSocket;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method is called when the Kafka producer receives a broker acknowledgement for a sent
   * record. It logs the received offset, creates an offset message, and publishes it via the ZeroMQ
   * socket. Finally, the embedded CompletableFuture is completed to signal the end of processing.
   *
   * @param recordMetadata metadata for the sent record, including partition and offset details;
   *     expected to be non-null
   * @param e the exception encountered during record processing, or null if the operation was
   *     successful
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
