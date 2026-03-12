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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import io.quasient.pal.core.rpc.policy.RpcPolicyPresets;
import io.quasient.pal.core.rpc.policy.RpcPolicyRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@link ClassMetadataSerializer} filtering based on {@link RpcPolicy}.
 *
 * <p>These tests verify that metadata returned by ClassMetadataSerializer reflects exactly what is
 * RPC-accessible according to the configured policy. Classes and methods denied by the policy must
 * not appear in the serialized metadata output.
 */
public class ClassMetadataSerializerRpcPolicyTest {

  /**
   * Verifies that classes denied by the RPC policy are excluded from the serialized metadata.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeClassesDeniedByPolicy]
   */
  @Test
  public void shouldExcludeClassesDeniedByPolicy() throws Exception {
    // Given: Policy that denies org.example.paltest.SubClass.** but allows everything else
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "org.example.paltest.SubClass", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.ALLOW);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(policy);

    // When: scan both SubClass and a JDK class
    Path outFile =
        serializer.scannedClasspathToJson(
            false, Set.of("org.example.paltest.SubClass", "java.lang.String"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    // Then: SubClass is excluded (all its members denied), String remains
    Set<String> classNames = new HashSet<>();
    for (JsonNode classNode : root) {
      classNames.add(classNode.get("className").asText());
    }
    assertThat(
        "SubClass should be excluded (denied by policy)",
        classNames.contains("org.example.paltest.SubClass"),
        is(false));
    assertThat(
        "String should be included (allowed by policy)",
        classNames.contains("java.lang.String"),
        is(true));
  }

  /**
   * Verifies that classes allowed by the RPC policy are included in the serialized metadata.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldIncludeClassesAllowedByPolicy]
   */
  @Test
  public void shouldIncludeClassesAllowedByPolicy() throws Exception {
    // Given: Policy that allows org.example.paltest.** but denies everything else
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "org.example.paltest.**", null, RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(policy);

    // When: scan SubClass and String
    Path outFile =
        serializer.scannedClasspathToJson(
            false, Set.of("org.example.paltest.SubClass", "java.lang.String"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    // Then: SubClass is included, String is excluded (denied by default action)
    Set<String> classNames = new HashSet<>();
    for (JsonNode classNode : root) {
      classNames.add(classNode.get("className").asText());
    }
    assertThat(
        "SubClass should be included (allowed by policy)",
        classNames.contains("org.example.paltest.SubClass"),
        is(true));
    assertThat(
        "String should be excluded (denied by default action)",
        classNames.contains("java.lang.String"),
        is(false));
  }

  /**
   * Verifies that methods denied by the RPC policy are excluded from a class's metadata even when
   * the class itself is allowed.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeMethodsDeniedByPolicy]
   */
  @Test
  public void shouldExcludeMethodsDeniedByPolicy() throws Exception {
    // Given: Policy that allows org.example.paltest.SubClass.** but denies getA specifically
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "org.example.paltest.SubClass", "getA", RpcPolicyAction.DENY, null, null, null),
            new RpcPolicyRule(
                "org.example.paltest.**", null, RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(policy);

    // When: scan SubClass
    Path outFile =
        serializer.scannedClasspathToJson(
            false, Set.of("org.example.paltest.SubClass"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    assertThat(root.size(), is(1));
    JsonNode classNode = root.get(0);
    JsonNode methodsArray = classNode.get("methods");

    // Then: SubClass appears but getA is NOT in the methods array
    boolean hasGetA = false;
    boolean hasIMeth = false;
    for (JsonNode methodNode : methodsArray) {
      String name = methodNode.get("name").asText();
      if ("getA".equals(name)) {
        hasGetA = true;
      }
      if ("iMeth".equals(name)) {
        hasIMeth = true;
      }
    }
    assertThat("getA should be excluded (denied by policy)", hasGetA, is(false));
    assertThat("iMeth should be included (allowed by policy)", hasIMeth, is(true));
  }

  /**
   * Verifies that classes or methods blocked by a built-in safety preset (e.g. deny-unsafe) are
   * excluded from metadata output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludePresetDeniedClasses]
   */
  @Test
  public void shouldExcludePresetDeniedClasses() throws Exception {
    // Given: Policy with deny-unsafe preset rules prepended, then allow-all default
    List<RpcPolicyRule> rules = new ArrayList<>(RpcPolicyPresets.getDenyUnsafeRules());
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.ALLOW);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(policy);

    // When: scan ProcessBuilder (which deny-unsafe blocks entirely with **)
    Path outFile =
        serializer.scannedClasspathToJson(false, Set.of("java.lang.ProcessBuilder"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    // Then: ProcessBuilder should be excluded since deny-unsafe blocks all its members (**)
    Set<String> classNames = new HashSet<>();
    for (JsonNode classNode : root) {
      classNames.add(classNode.get("className").asText());
    }
    assertThat(
        "ProcessBuilder should be excluded (denied by deny-unsafe preset)",
        classNames.contains("java.lang.ProcessBuilder"),
        is(false));
  }

  /**
   * Verifies that the static CLASS_PREFIXES_TO_EXCLUDE filter (io.quasient.pal., com.sun., sun.,
   * jdk.*) continues to operate as a baseline regardless of RPC policy configuration.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldStillExcludePalAndJdkInternalsFromPrefixFilter]
   */
  @Test
  public void shouldStillExcludePalAndJdkInternalsFromPrefixFilter() throws Exception {
    // Given: Allow-all policy with no presets
    RpcPolicy allowAll = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(allowAll);

    // When: scan all classes (no include filter)
    Path outFile = serializer.scannedClasspathToJson(false, null, null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    // Then: Even with allow-all policy, CLASS_PREFIXES_TO_EXCLUDE still filters these prefixes
    assertThat(
        "io.quasient.pal. classes should be excluded by prefix filter",
        json.contains("\"className\":\"io.quasient.pal."),
        is(false));
    // com.sun., sun., jdk. are also in the prefix filter
    // Note: these may not appear in the classpath at all, but if they do, they're excluded
    assertThat(
        "com.sun. classes should be excluded by prefix filter",
        json.contains("\"className\":\"com.sun."),
        is(false));

    // Verify that at least some classes ARE included (the filter doesn't exclude everything)
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    assertThat("Should still include many classes", root.size(), greaterThan(1000));
  }

  /**
   * Verifies that when the RPC policy allows non-public access for a specific package pattern,
   * non-public members of matching classes appear in the metadata output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldReflectVisibilityFromPolicy]
   */
  @Test
  public void shouldReflectVisibilityFromPolicy() throws Exception {
    // Given: Policy that allows org.example.paltest.** (including non-public members)
    // The allow-all policy permits all members regardless of visibility, since the serializer
    // always scans with full visibility and relies on the policy for filtering.
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "org.example.paltest.**", null, RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);
    ClassMetadataSerializer serializer = new ClassMetadataSerializer(policy);

    // When: scan BaseClass (which is package-private)
    Path outFile =
        serializer.scannedClasspathToJson(
            false, Set.of("org.example.paltest.BaseClass"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    // Then: BaseClass appears because the policy allows org.example.paltest.**
    assertThat("Should find the package-private BaseClass", root.size(), is(1));
    assertThat(root.get(0).get("className").asText(), is("org.example.paltest.BaseClass"));

    // Verify it has methods (non-public members are included because policy allows them)
    JsonNode methodsArray = root.get(0).get("methods");
    assertThat("Should have at least one method", methodsArray.size(), greaterThan(0));
  }

  /**
   * Verifies that non-public methods are excluded from metadata when the policy restricts
   * visibility to PUBLIC only.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeNonPublicMethodsWhenPolicyRestrictsVisibility]
   */
  @Test
  @Ignore("Awaiting implementation in #1102")
  public void shouldExcludeNonPublicMethodsWhenPolicyRestrictsVisibility() {
    // Given: Policy with ALLOW rule restricted to visibilities = EnumSet.of(PUBLIC), default DENY
    // And: ClassMetadataSerializer scanning a class with public and non-public methods
    // When: scannedClasspathToJson() called
    // Then: JSON output contains only public methods; protected/package-private/private methods
    //       excluded

    // TODO(#1102): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that non-public constructors are excluded from metadata when the policy restricts
   * visibility to PUBLIC only.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeNonPublicConstructorsWhenPolicyRestrictsVisibility]
   */
  @Test
  @Ignore("Awaiting implementation in #1102")
  public void shouldExcludeNonPublicConstructorsWhenPolicyRestrictsVisibility() {
    // Given: Policy with ALLOW rule restricted to visibilities = EnumSet.of(PUBLIC), default DENY
    // And: Class with both public and package-private constructors
    // When: Metadata generated
    // Then: Only public constructor appears in output

    // TODO(#1102): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that non-public fields are excluded from metadata when the policy restricts visibility
   * to PUBLIC only.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldExcludeNonPublicFieldsWhenPolicyRestrictsVisibility]
   */
  @Test
  @Ignore("Awaiting implementation in #1102")
  public void shouldExcludeNonPublicFieldsWhenPolicyRestrictsVisibility() {
    // Given: Policy with ALLOW rule restricted to visibilities = EnumSet.of(PUBLIC), default DENY
    // And: Class with public and private fields
    // When: Metadata generated
    // Then: Only public fields appear in output

    // TODO(#1102): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that all members appear in metadata when the policy has no visibility restriction
   * (visibilities = null).
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerRpcPolicyTest.shouldIncludeAllVisibilitiesWhenNoVisibilityRestriction]
   */
  @Test
  @Ignore("Awaiting implementation in #1102")
  public void shouldIncludeAllVisibilitiesWhenNoVisibilityRestriction() {
    // Given: Policy with ALLOW rule and no visibility restriction (visibilities = null)
    // When: Metadata generated
    // Then: All methods/constructors/fields appear regardless of visibility

    // TODO(#1102): Implement test logic
    fail("Not yet implemented");
  }
}
