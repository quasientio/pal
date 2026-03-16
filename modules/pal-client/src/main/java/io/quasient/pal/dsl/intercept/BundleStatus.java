/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Status of a bundle's intercepts in the directory.
 *
 * <p>Contains the bundle name, peer information, and a list of {@link InterceptStatusEntry} records
 * indicating whether each intercept is active and its remaining TTL.
 */
public final class BundleStatus {

  /** Status of an individual intercept within the bundle. */
  public static final class InterceptStatusEntry {

    /** The intercept specification. */
    private final InterceptSpec spec;

    /** The intercept UUID. */
    private final UUID uuid;

    /** Whether the intercept is currently active. */
    private final boolean active;

    /** The remaining TTL, or {@code null} if no TTL is set. */
    @Nullable private final Duration ttlRemaining;

    /**
     * Constructs a new intercept status entry.
     *
     * @param spec the intercept specification
     * @param uuid the intercept UUID
     * @param active whether the intercept is currently active
     * @param ttlRemaining the remaining TTL, or {@code null} if no TTL is set
     * @throws NullPointerException if {@code spec} or {@code uuid} is {@code null}
     */
    public InterceptStatusEntry(
        InterceptSpec spec, UUID uuid, boolean active, @Nullable Duration ttlRemaining) {
      this.spec = Objects.requireNonNull(spec, "spec must not be null");
      this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
      this.active = active;
      this.ttlRemaining = ttlRemaining;
    }

    /**
     * Returns the intercept specification.
     *
     * @return the intercept spec
     */
    public InterceptSpec getSpec() {
      return spec;
    }

    /**
     * Returns the intercept UUID.
     *
     * @return the UUID
     */
    public UUID getUuid() {
      return uuid;
    }

    /**
     * Returns whether the intercept is currently active in the directory.
     *
     * @return {@code true} if active
     */
    public boolean isActive() {
      return active;
    }

    /**
     * Returns the remaining TTL, or {@code null} if no TTL is set.
     *
     * @return the remaining TTL, or {@code null}
     */
    @Nullable
    public Duration getTtlRemaining() {
      return ttlRemaining;
    }
  }

  /** The bundle name. */
  private final String bundleName;

  /** The peer name, or {@code null} if not available. */
  @Nullable private final String peerName;

  /** The peer UUID, or {@code null} if not available. */
  @Nullable private final UUID peerUuid;

  /** The list of intercept status entries. */
  private final List<InterceptStatusEntry> entries;

  /**
   * Constructs a new bundle status.
   *
   * @param bundleName the bundle name
   * @param peerName the peer name, or {@code null}
   * @param peerUuid the peer UUID, or {@code null}
   * @param entries the list of intercept status entries
   * @throws NullPointerException if {@code bundleName} or {@code entries} is {@code null}
   */
  public BundleStatus(
      String bundleName,
      @Nullable String peerName,
      @Nullable UUID peerUuid,
      List<InterceptStatusEntry> entries) {
    this.bundleName = Objects.requireNonNull(bundleName, "bundleName must not be null");
    this.peerName = peerName;
    this.peerUuid = peerUuid;
    Objects.requireNonNull(entries, "entries must not be null");
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
  }

  /**
   * Returns the bundle name.
   *
   * @return the bundle name
   */
  public String getBundleName() {
    return bundleName;
  }

  /**
   * Returns the peer name, or {@code null} if not available.
   *
   * @return the peer name, or {@code null}
   */
  @Nullable
  public String getPeerName() {
    return peerName;
  }

  /**
   * Returns the peer UUID, or {@code null} if not available.
   *
   * @return the peer UUID, or {@code null}
   */
  @Nullable
  public UUID getPeerUuid() {
    return peerUuid;
  }

  /**
   * Returns the list of intercept status entries.
   *
   * @return an unmodifiable list of entries
   */
  public List<InterceptStatusEntry> getEntries() {
    return entries;
  }

  /**
   * Returns the count of active intercepts.
   *
   * @return the number of active intercepts
   */
  public long getActiveCount() {
    return entries.stream().filter(InterceptStatusEntry::isActive).count();
  }

  /**
   * Returns the total number of intercepts in this bundle status.
   *
   * @return the total count
   */
  public int getTotalCount() {
    return entries.size();
  }
}
