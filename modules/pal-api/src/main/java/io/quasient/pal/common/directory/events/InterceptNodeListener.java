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
package io.quasient.pal.common.directory.events;

/**
 * Defines a listener for intercepting node-related events within the PAL system.
 *
 * <p>Implementers of this interface can register to receive notifications when specific {@link
 * InterceptEvent} instances occur, allowing for custom handling of node interactions.
 *
 * @see InterceptEvent
 */
public interface InterceptNodeListener {

  /**
   * Handles the specified intercept event.
   *
   * <p>This method is invoked by the PAL directory when an {@link InterceptEvent} occurs.
   *
   * @param event the {@code InterceptEvent} to be processed Must not be {@code null}.
   * @throws IllegalArgumentException if the {@code event} is {@code null}
   * @see InterceptEvent
   */
  void interceptEvent(InterceptEvent event);
}
