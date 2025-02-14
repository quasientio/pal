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

package net.ittera.pal.common.directory.events;

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
