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

import com.google.common.base.Splitter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents an event triggered when an intercept is added or removed within the system.
 *
 * <p>This event encapsulates information about the type of change (addition or removal), the path
 * of the intercept, the peer involved, the intercept identifier, and the intercept request if
 * applicable.
 *
 * @param type the type of the intercept event indicating addition or removal. Must not be {@code
 *     null}.
 * @param interceptPath the hierarchical path of the intercept, structured with four non-empty parts
 *     separated by '/' and not {@code null}.
 * @param peerUuid the universally unique identifier (UUID) of the peer that owns the new or removed
 *     intercept, must not be {@code null}.
 * @param interceptId The unique identifier of the intercept that was added or removed. Must not be
 *     {@code null} or empty.
 * @param interceptRequest The {@link InterceptRequest} that was added. This is {@code null} if the
 *     intercept was removed. It cannot be {@code null} if the event type is {@link
 *     Type#INTERCEPT_ADDED}.
 * @see InterceptRequest
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Internal event class - callers are trusted")
public record InterceptEvent(
    @Nonnull InterceptEvent.Type type,
    @Nonnull String interceptPath,
    @Nonnull UUID peerUuid,
    @Nonnull String interceptId,
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
