/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java.reflect;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Test specifications for {@link ClassMetadataSerializer} filtering based on {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicy}.
 *
 * <p>These tests verify that metadata returned by ClassMetadataSerializer reflects exactly what is
 * RPC-accessible according to the configured policy. Classes and methods denied by the policy must
 * not appear in the serialized metadata output.
 *
 * <p>All tests are stubs awaiting implementation in #1005.
 */
public class ClassMetadataSerializerRpcPolicyTest {

  /**
   * Verifies that classes denied by the RPC policy are excluded from the serialized metadata.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeClassesDeniedByPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldExcludeClassesDeniedByPolicy() {
    // Given: ClassMetadataSerializer with RpcPolicy that denies com.example.internal.**
    // When: scannedClasspathToJson()
    // Then: No com.example.internal classes in output JSON

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that classes allowed by the RPC policy are included in the serialized metadata.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldIncludeClassesAllowedByPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldIncludeClassesAllowedByPolicy() {
    // Given: Policy that allows com.example.api.**
    // When: scannedClasspathToJson()
    // Then: com.example.api classes appear in output

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that methods denied by the RPC policy are excluded from a class's metadata even when
   * the class itself is allowed.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeMethodsDeniedByPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldExcludeMethodsDeniedByPolicy() {
    // Given: Policy that allows com.example.Foo.* but denies com.example.Foo.secretMethod
    // When: scannedClasspathToJson() for class com.example.Foo
    // Then: Foo appears but secretMethod is not in the methods array

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that classes or methods blocked by a built-in safety preset (e.g. deny-unsafe) are
   * excluded from metadata output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludePresetDeniedClasses]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldExcludePresetDeniedClasses() {
    // Given: Policy with deny-unsafe preset
    // When: scannedClasspathToJson()
    // Then: java.lang.ProcessBuilder not in output (or its dangerous methods excluded)

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the static CLASS_PREFIXES_TO_EXCLUDE filter (io.quasient.pal., com.sun., sun.,
   * jdk.*) continues to operate as a baseline regardless of RPC policy configuration.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldStillExcludePalAndJdkInternalsFromPrefixFilter]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldStillExcludePalAndJdkInternalsFromPrefixFilter() {
    // Given: Policy with no presets (even without deny-jdk-internals preset)
    // When: scannedClasspathToJson()
    // Then: CLASS_PREFIXES_TO_EXCLUDE still filters io.quasient.pal., com.sun., sun., jdk.*
    // Note: The static prefix filter remains as a baseline; policy adds dynamic filtering on top

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that when the RPC policy allows non-public access for a specific package pattern,
   * non-public members of matching classes appear in the metadata output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldReflectVisibilityFromPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #1005")
  public void shouldReflectVisibilityFromPolicy() {
    // Given: Policy that allows non-public access for com.example.internal.**
    // When: scannedClasspathToJson() with that policy
    // Then: Non-public members of com.example.internal classes included in metadata

    // TODO(#1005): Implement test logic
    fail("Not yet implemented");
  }
}
