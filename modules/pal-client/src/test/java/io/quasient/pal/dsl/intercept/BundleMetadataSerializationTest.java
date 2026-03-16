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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link BundleMetadata} JSON serialization round-trips.
 *
 * <p>Verifies that {@link BundleMetadata#toJson()} and {@link BundleMetadata#fromJson(String)}
 * correctly serialize and deserialize all fields, including custom Gson type adapters for {@link
 * java.time.Instant} and {@link java.util.UUID}.
 */
public class BundleMetadataSerializationTest {

  /**
   * Verifies that a fully-populated BundleMetadata round-trips through JSON without data loss.
   *
   * <p>All fields (bundleName, peerUuid, interceptUuids, appliedAt, version) must survive the
   * serialize/deserialize cycle.
   */
  @Test
  public void roundTrip_serializeDeserialize() {
    // Given
    String bundleName = "fraud-check-v1";
    UUID peerUuid = UUID.randomUUID();
    List<UUID> interceptUuids = List.of(UUID.randomUUID(), UUID.randomUUID());
    Instant appliedAt = Instant.parse("2026-03-15T10:30:00Z");
    int version = 1;

    BundleMetadata original =
        new BundleMetadata(bundleName, peerUuid, interceptUuids, appliedAt, version);

    // When
    String json = original.toJson();
    BundleMetadata deserialized = BundleMetadata.fromJson(json);

    // Then
    assertThat(deserialized, is(notNullValue()));
    assertThat(deserialized.getBundleName(), is(bundleName));
    assertThat(deserialized.getPeerUuid(), is(peerUuid));
    assertThat(deserialized.getInterceptUuids(), is(interceptUuids));
    assertThat(deserialized.getAppliedAt(), is(appliedAt));
    assertThat(deserialized.getVersion(), is(version));
  }

  /**
   * Verifies that a BundleMetadata with an empty interceptUuids list serializes and deserializes
   * correctly.
   */
  @Test
  public void serialize_handlesEmptyInterceptUuids() {
    // Given
    BundleMetadata original =
        new BundleMetadata(
            "empty-bundle", UUID.randomUUID(), Collections.emptyList(), Instant.now(), 0);

    // When
    String json = original.toJson();
    BundleMetadata deserialized = BundleMetadata.fromJson(json);

    // Then
    assertThat(deserialized, is(notNullValue()));
    assertThat(deserialized.getInterceptUuids(), is(notNullValue()));
    assertThat(deserialized.getInterceptUuids().isEmpty(), is(true));
  }

  /** Verifies that a BundleMetadata with multiple intercept UUIDs round-trips correctly. */
  @Test
  public void serialize_handlesMultipleUuids() {
    // Given
    List<UUID> uuids = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      uuids.add(UUID.randomUUID());
    }
    BundleMetadata original =
        new BundleMetadata("multi-bundle", UUID.randomUUID(), uuids, Instant.now(), 2);

    // When
    String json = original.toJson();
    BundleMetadata deserialized = BundleMetadata.fromJson(json);

    // Then
    assertThat(deserialized.getInterceptUuids().size(), is(5));
    assertThat(deserialized.getInterceptUuids(), is(uuids));
  }
}
