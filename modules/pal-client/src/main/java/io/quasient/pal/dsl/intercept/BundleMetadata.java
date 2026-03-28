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
package io.quasient.pal.dsl.intercept;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight metadata stored in etcd per intercept bundle.
 *
 * <p>This class is serializable to and from JSON via Gson, allowing bundle metadata to be persisted
 * in etcd for operations like {@code pal intercept rm --bundle} and {@code pal intercept status
 * --bundle} without requiring the original YAML file.
 */
public final class BundleMetadata {

  /** Shared Gson instance configured with custom type adapters. */
  private static final Gson GSON = createGson();

  /** The bundle name. */
  private final String bundleName;

  /** The UUID of the peer that applied this bundle. */
  private final UUID peerUuid;

  /** The list of intercept UUIDs in this bundle. */
  private final List<UUID> interceptUuids;

  /** The instant when the bundle was applied. */
  private final Instant appliedAt;

  /** The version number of this metadata. */
  private final int version;

  /**
   * Constructs a new bundle metadata instance.
   *
   * @param bundleName the bundle name
   * @param peerUuid the UUID of the peer that applied this bundle
   * @param interceptUuids the list of intercept UUIDs in this bundle
   * @param appliedAt the instant when the bundle was applied
   * @param version the version number of this metadata
   * @throws NullPointerException if any required parameter is {@code null}
   */
  public BundleMetadata(
      String bundleName, UUID peerUuid, List<UUID> interceptUuids, Instant appliedAt, int version) {
    this.bundleName = Objects.requireNonNull(bundleName, "bundleName must not be null");
    this.peerUuid = Objects.requireNonNull(peerUuid, "peerUuid must not be null");
    Objects.requireNonNull(interceptUuids, "interceptUuids must not be null");
    this.interceptUuids = Collections.unmodifiableList(new ArrayList<>(interceptUuids));
    this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt must not be null");
    this.version = version;
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
   * Returns the UUID of the peer that applied this bundle.
   *
   * @return the peer UUID
   */
  public UUID getPeerUuid() {
    return peerUuid;
  }

  /**
   * Returns the list of intercept UUIDs in this bundle.
   *
   * @return an unmodifiable list of intercept UUIDs
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "interceptUuids is already an unmodifiable defensive copy")
  public List<UUID> getInterceptUuids() {
    return interceptUuids;
  }

  /**
   * Returns the instant when the bundle was applied.
   *
   * @return the applied-at instant
   */
  public Instant getAppliedAt() {
    return appliedAt;
  }

  /**
   * Returns the version number of this metadata.
   *
   * @return the version
   */
  public int getVersion() {
    return version;
  }

  /**
   * Serializes this metadata to a JSON string.
   *
   * @return the JSON representation
   */
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * Deserializes a JSON string into a {@code BundleMetadata} instance.
   *
   * @param json the JSON string
   * @return the deserialized bundle metadata
   * @throws com.google.gson.JsonSyntaxException if the JSON is malformed
   * @throws NullPointerException if {@code json} is {@code null}
   */
  public static BundleMetadata fromJson(String json) {
    Objects.requireNonNull(json, "json must not be null");
    return GSON.fromJson(json, BundleMetadata.class);
  }

  /**
   * Creates a Gson instance configured with custom type adapters for {@link Instant} and {@link
   * UUID}.
   *
   * @return the configured Gson instance
   */
  private static Gson createGson() {
    return new GsonBuilder()
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(UUID.class, new UuidTypeAdapter())
        .create();
  }

  /** Gson type adapter for {@link Instant}, serialized as ISO-8601 string. */
  private static final class InstantTypeAdapter extends TypeAdapter<Instant> {

    /**
     * Writes an Instant value as an ISO-8601 string.
     *
     * @param out the JSON writer
     * @param value the Instant value to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    /**
     * Reads an Instant value from an ISO-8601 string.
     *
     * @param in the JSON reader
     * @return the parsed Instant
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Instant read(JsonReader in) throws IOException {
      String value = in.nextString();
      return Instant.parse(value);
    }
  }

  /** Gson type adapter for {@link UUID}, serialized as string. */
  private static final class UuidTypeAdapter extends TypeAdapter<UUID> {

    /**
     * Writes a UUID value as a string.
     *
     * @param out the JSON writer
     * @param value the UUID value to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(JsonWriter out, UUID value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    /**
     * Reads a UUID value from a string.
     *
     * @param in the JSON reader
     * @return the parsed UUID
     * @throws IOException if an I/O error occurs
     */
    @Override
    public UUID read(JsonReader in) throws IOException {
      String value = in.nextString();
      return UUID.fromString(value);
    }
  }
}
