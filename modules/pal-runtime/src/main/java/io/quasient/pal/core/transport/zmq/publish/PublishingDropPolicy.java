/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
