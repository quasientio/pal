/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.zmq.publish;

/**
 * Enumerates the available policies/strategies to drop messages enqueued to be published when the
 * PUB queue or {@link MessagePublisher}'s internal SPSC queue is saturated (i.e. producers outpace
 * the publisher's network thread).
 */
public enum PublishingDropPolicy {
  /** Producer drops the message that cannot be enqueued. */
  DROP_NEW,

  /** Consumer trims oldest messages when backlog crosses a high-water mark. */
  DROP_OLD,

  /** Block/slow producers until space is available. */
  NONE
}
