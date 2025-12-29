/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
