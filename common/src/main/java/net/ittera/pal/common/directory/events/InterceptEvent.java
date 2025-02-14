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

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.nodes.InterceptRequest;

/**
 * Represents an event triggered when an intercept is added or removed within the system.
 *
 * <p>This event encapsulates information about the type of change (addition or removal), the path
 * of the intercept, the peer involved, the intercept identifier, and the intercept request if
 * applicable.
 *
 * @see InterceptRequest
 */
public record InterceptEvent(
    /**
     * The type of the intercept event indicating addition or removal.
     *
     * @see Type
     */
    @Nonnull InterceptEvent.Type type,

    /**
     * The hierarchical path of the intercept, structured with four non-empty parts separated by
     * '/'.
     */
    @Nonnull String interceptPath,

    /**
     * The universally unique identifier (UUID) of the peer that owns the new or removed intercept.
     */
    @Nonnull UUID peerUuid,

    /** The unique identifier of the intercept that was added or removed. */
    @Nonnull String interceptId,

    /**
     * The {@link InterceptRequest} that was added. This is {@code null} if the intercept was
     * removed.
     */
    @Nullable InterceptRequest<?> interceptRequest) {

  /**
   * Constructs an {@code InterceptEvent} with the specified parameters, ensuring all invariants are
   * met.
   *
   * @param type the type of the event, must not be {@code null}.
   * @param interceptPath the path of the intercept, must consist of four non-empty parts separated
   *     by '/' and not {@code null}.
   * @param peerUuid the UUID of the peer, must not be {@code null}.
   * @param interceptId the identifier of the intercept, must not be {@code null} or empty.
   * @param interceptRequest the intercept request, must not be {@code null} if the event type is
   *     {@link Type#INTERCEPT_ADDED}.
   * @throws NullPointerException if any of {@code type}, {@code interceptPath}, {@code peerUuid},
   *     or {@code interceptId} is {@code null}.
   * @throws IllegalArgumentException if {@code interceptPath} is empty or does not consist of four
   *     parts, or if {@code interceptId} is empty.
   */
  public InterceptEvent {
    Objects.requireNonNull(type, "type cannot be null");
    validatePath(interceptPath);
    Objects.requireNonNull(peerUuid, "peerUuid cannot be null");
    Objects.requireNonNull(interceptId, "interceptId cannot be null");
    if (interceptId.isEmpty()) {
      throw new IllegalArgumentException("interceptId cannot be empty");
    }
    if (type == Type.INTERCEPT_ADDED) {
      Objects.requireNonNull(interceptRequest, "interceptRequest cannot be null");
    }
  }

  /** Enumeration of possible types of {@link InterceptEvent}. */
  public enum Type {
    /** Indicates that an intercept has been successfully added. */
    INTERCEPT_ADDED,

    /** Indicates that an intercept has been successfully removed. */
    INTERCEPT_REMOVED,
  }

  /**
   * Validates the intercept path to ensure it meets the required format.
   *
   * @param path the intercept path to validate, must not be {@code null} or empty.
   * @throws NullPointerException if {@code path} is {@code null}.
   * @throws IllegalArgumentException if {@code path} is empty or does not consist of four non-empty
   *     parts separated by '/'.
   */
  private static void validatePath(String path) {
    Objects.requireNonNull(path, "interceptPath cannot be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("interceptPath cannot be empty");
    }
    // path should consist of 4 parts separated by '/'
    Iterable<String> parts = Splitter.on('/').split(path);
    List<String> nonEmptyPathElements = new ArrayList<>();
    for (String part : parts) {
      if (!part.isEmpty()) {
        nonEmptyPathElements.add(part);
      }
    }
    if (nonEmptyPathElements.size() != 4) {
      throw new IllegalArgumentException(
          "interceptPath should consist of 4 non-empty parts separated by '/'");
    }
  }
}
