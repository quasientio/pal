/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.directory.nodes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class InterceptRequestTest {

  private UUID uuid;
  private UUID peer;
  private InterceptType type;
  private String clazz;
  private String callbackClass;
  private String callbackMethod;
  private InterceptableMethodCall interceptableMethod;
  private InterceptableFieldOp interceptableFieldOp;
  private InterceptRequest<InterceptableMethodCall> methodInterceptRequest;
  private InterceptRequest<InterceptableFieldOp> fieldOpInterceptRequest;

  @Before
  public void setUp() {
    uuid = UUID.randomUUID();
    peer = UUID.randomUUID();
    type = InterceptType.BEFORE;
    clazz = "com.dummy.Class";
    callbackClass = "com.dummy.CallbackClass";
    callbackMethod = "MyCallback";
    interceptableMethod =
        new InterceptableMethodCall(
            "println", Arrays.asList("java.lang.String", "java.lang.Integer"));
    interceptableFieldOp = new InterceptableFieldOp("myField", FieldOpType.GET);

    methodInterceptRequest =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);
    fieldOpInterceptRequest =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableFieldOp);
  }

  @Test
  public void equalsContract() {
    InterceptRequest<InterceptableMethodCall> a =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);
    InterceptRequest<InterceptableMethodCall> b =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);
    InterceptRequest<InterceptableMethodCall> c =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);
    InterceptRequest<InterceptableMethodCall> different =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, "OtherCallback", interceptableMethod);

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }

  @Test
  public void getUuid() {
    assertEquals(uuid, methodInterceptRequest.getUuid());
  }

  @Test
  public void getPeer() {
    assertEquals(peer, methodInterceptRequest.getPeer());
  }

  @Test
  public void getType() {
    assertEquals(type, methodInterceptRequest.getType());
  }

  @Test
  public void getClazz() {
    assertEquals(clazz, methodInterceptRequest.getClazz());
  }

  @Test
  public void getCallbackClass() {
    assertEquals(callbackClass, methodInterceptRequest.getCallbackClass());
  }

  @Test
  public void getCallbackMethod() {
    assertEquals(callbackMethod, methodInterceptRequest.getCallbackMethod());
  }

  @Test
  public void getMethodInterceptable() {
    assertEquals(interceptableMethod, methodInterceptRequest.getInterceptable());
  }

  @Test
  public void getFieldopInterceptable() {
    assertEquals(interceptableFieldOp, fieldOpInterceptRequest.getInterceptable());
  }

  @Test
  public void toAndFromBytes_methodIntercept() {
    // method
    byte[] bytes = methodInterceptRequest.toBytes(StandardCharsets.UTF_8);
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);
    assertEquals(methodInterceptRequest, deserialized);
  }

  @Test
  public void toAndFromBytes_fieldopIntercept() {
    // field op
    byte[] bytes = fieldOpInterceptRequest.toBytes(StandardCharsets.UTF_8);
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);
    assertEquals(fieldOpInterceptRequest, deserialized);
  }

  @Test
  public void testToString_interceptableMethod() {
    Stream.of(methodInterceptRequest, fieldOpInterceptRequest)
        .forEach(
            interceptRequest -> {

              // set time fields
              long ctime = 22892339L;
              long mtime = 23982349L;
              interceptRequest.setCtime(ctime);
              interceptRequest.setMtime(mtime);

              assertThat(
                  interceptRequest.toString(),
                  is(
                      "InterceptRequest {"
                          + "uuid="
                          + uuid
                          + ", peer="
                          + peer
                          + ", type="
                          + type
                          + ", clazz='"
                          + clazz
                          + '\''
                          + ", interceptable="
                          + interceptRequest.getInterceptable()
                          + ", callbackClass='"
                          + callbackClass
                          + '\''
                          + ", callbackMethod='"
                          + callbackMethod
                          + '\''
                          + ", forceImmediate="
                          + interceptRequest.isForceImmediate()
                          + ", priority="
                          + interceptRequest.getPriority()
                          + ", ctime="
                          + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                          + ", mtime="
                          + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                          + '}'));
            });
  }

  /**
   * Test specification for storing exception propagation policy.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldStoreExceptionPropagationPolicy]
   * Exception propagation policy stored
   */
  @Test
  public void shouldStoreExceptionPropagationPolicy() {
    // Given: InterceptRequest with PROPAGATE_ALL exception propagation policy
    InterceptRequest<?> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            ExceptionPropagationPolicy.PROPAGATE_ALL,
            null);

    // When: Getting the exception propagation policy
    // Then: Returns PROPAGATE_ALL
    assertEquals(ExceptionPropagationPolicy.PROPAGATE_ALL, request.getExceptionPropagationPolicy());
  }

  /**
   * Test specification for storing checked exception policy.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldStoreCheckedExceptionPolicy] Checked
   * exception policy stored
   */
  @Test
  public void shouldStoreCheckedExceptionPolicy() {
    // Given: InterceptRequest with WRAP checked exception policy
    InterceptRequest<?> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            CheckedExceptionPolicy.WRAP);

    // When: Getting the checked exception policy
    // Then: Returns WRAP
    assertEquals(CheckedExceptionPolicy.WRAP, request.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for default null policies.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldDefaultToNullPolicies] Null defaults
   * for deferred resolution
   */
  @Test
  public void shouldDefaultToNullPolicies() {
    // Given: InterceptRequest constructed without explicit policies
    InterceptRequest<?> request =
        new InterceptRequest<>(
            uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);

    // When: Getting both exception propagation and checked exception policies
    // Then: Both policies return null (indicating they defer to global defaults)
    assertNull(request.getExceptionPropagationPolicy());
    assertNull(request.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for JSON serialization of policies.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldSerializeAndDeserializePolicies] JSON
   * serialization works
   */
  @Test
  public void shouldSerializeAndDeserializePolicies() {
    // Given: InterceptRequest with both exception propagation and checked exception policies set
    InterceptRequest<?> original =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY,
            CheckedExceptionPolicy.REJECT);

    // When: Serializing to bytes and deserializing back
    byte[] bytes = original.toBytes(StandardCharsets.UTF_8);
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);

    // Then: Both policies are preserved after round-trip serialization
    assertEquals(
        ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY,
        deserialized.getExceptionPropagationPolicy());
    assertEquals(CheckedExceptionPolicy.REJECT, deserialized.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for storing priority value.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldStorePriority] Priority value stored
   * and returned by getter
   */
  @Test
  public void shouldStorePriority() {
    // Given: InterceptRequest constructed with priority=42 via the new full constructor
    InterceptRequest<InterceptableMethodCall> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            42);

    // When/Then: Calling getPriority() returns 42
    assertThat(request.getPriority(), is(42));
  }

  /**
   * Test specification for default priority value.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldDefaultPriorityToZero] Default
   * priority is 0 when using existing constructors
   */
  @Test
  public void shouldDefaultPriorityToZero() {
    // Given: InterceptRequest constructed with the existing 7-arg convenience constructor
    // When/Then: Calling getPriority() returns 0
    assertThat(methodInterceptRequest.getPriority(), is(0));
  }

  /**
   * Test specification for serialization round-trip with positive priority.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldSerializeAndDeserializePriority]
   * Serialization round-trip preserves positive priority
   */
  @Test
  public void shouldSerializeAndDeserializePriority() {
    // Given: InterceptRequest with priority=42
    InterceptRequest<InterceptableMethodCall> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            42);

    // When: toBytes() then fromBytes()
    byte[] bytes = request.toBytes(StandardCharsets.UTF_8);
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);

    // Then: Deserialized request has priority=42
    assertThat(deserialized.getPriority(), is(42));
    assertEquals(request, deserialized);
  }

  /**
   * Test specification for serialization round-trip with negative priority.
   *
   * <p>Acceptance Criterion:
   * [TEST:InterceptRequestTest.shouldSerializeAndDeserializeNegativePriority] Serialization
   * round-trip preserves negative priority
   */
  @Test
  public void shouldSerializeAndDeserializeNegativePriority() {
    // Given: InterceptRequest with priority=-10
    InterceptRequest<InterceptableMethodCall> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            -10);

    // When: toBytes() then fromBytes()
    byte[] bytes = request.toBytes(StandardCharsets.UTF_8);
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);

    // Then: Deserialized request has priority=-10
    assertThat(deserialized.getPriority(), is(-10));
    assertEquals(request, deserialized);
  }

  /**
   * Test specification for backward-compatible deserialization of old format.
   *
   * <p>Acceptance Criterion:
   * [TEST:InterceptRequestTest.shouldDeserializeOldFormatWithoutPriorityAsZero] Old format
   * deserializes with priority=0
   */
  @Test
  public void shouldDeserializeOldFormatWithoutPriorityAsZero() {
    // Given: Byte array serialized in old 13-field format (no priority field at index 13)
    // Construct old format manually: 13 fields (indices 0-12), no priority at index 13
    String oldFormat =
        uuid
            + "##"
            + peer
            + "##"
            + type.toByte()
            + "##"
            + clazz
            + "##"
            + callbackClass
            + "##"
            + callbackMethod
            + "##"
            + interceptableMethod.getType().toByte()
            + "##"
            + interceptableMethod.toSerializedString()
            + "##"
            + false
            + "##"
            + "null"
            + "##"
            + "null"
            + "##"
            + "null"
            + "##"
            + "null";
    byte[] bytes = oldFormat.getBytes(StandardCharsets.UTF_8);

    // When: fromBytes()
    InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);

    // Then: Deserialized request has priority=0
    assertThat(deserialized.getPriority(), is(0));
  }

  /**
   * Test specification for priority inclusion in equals.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldIncludePriorityInEquals] Equality
   * considers priority
   */
  @Test
  public void shouldIncludePriorityInEquals() {
    // Given: Two InterceptRequests identical except for priority (one p=0, one p=5)
    InterceptRequest<InterceptableMethodCall> a =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            0);
    InterceptRequest<InterceptableMethodCall> b =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            5);

    // When/Then: They are NOT equal
    assertNotEquals(a, b);
  }

  /**
   * Test specification for priority inclusion in hashCode.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldIncludePriorityInHashCode]
   * Same-priority requests have same hashCode
   */
  @Test
  public void shouldIncludePriorityInHashCode() {
    // Given: Two InterceptRequests identical including same priority
    InterceptRequest<InterceptableMethodCall> a =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            7);
    InterceptRequest<InterceptableMethodCall> b =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            7);

    // When/Then: Hash codes are equal
    assertThat(a.hashCode(), is(b.hashCode()));
  }

  /**
   * Test specification for priority inclusion in toString.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldIncludePriorityInToString] toString
   * includes priority
   */
  @Test
  public void shouldIncludePriorityInToString() {
    // Given: InterceptRequest with priority=7
    InterceptRequest<InterceptableMethodCall> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptableMethod,
            false,
            null,
            null,
            7);

    // When/Then: Output contains "priority=7"
    assertThat(request.toString(), org.hamcrest.Matchers.containsString("priority=7"));
  }
}
