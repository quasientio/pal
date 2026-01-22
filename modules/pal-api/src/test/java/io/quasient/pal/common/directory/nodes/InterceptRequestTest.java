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

import io.quasient.pal.common.lang.FieldOpType;
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
import org.junit.Ignore;
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
  @Ignore("Awaiting implementation in #278")
  public void shouldStoreExceptionPropagationPolicy() {
    // Given: InterceptRequest with PROPAGATE_ALL exception propagation policy
    // When: Getting the exception propagation policy
    // Then: Returns PROPAGATE_ALL

    // TODO: Implement after #278 provides the implementation
    // Expected usage:
    // InterceptRequest<?> request = new InterceptRequest<>(
    //     uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod,
    //     false, ExceptionPropagationPolicy.PROPAGATE_ALL, null);
    // assertEquals(ExceptionPropagationPolicy.PROPAGATE_ALL,
    // request.getExceptionPropagationPolicy());
  }

  /**
   * Test specification for storing checked exception policy.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldStoreCheckedExceptionPolicy] Checked
   * exception policy stored
   */
  @Test
  @Ignore("Awaiting implementation in #278")
  public void shouldStoreCheckedExceptionPolicy() {
    // Given: InterceptRequest with WRAP checked exception policy
    // When: Getting the checked exception policy
    // Then: Returns WRAP

    // TODO: Implement after #278 provides the implementation
    // Expected usage:
    // InterceptRequest<?> request = new InterceptRequest<>(
    //     uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod,
    //     false, null, CheckedExceptionPolicy.WRAP);
    // assertEquals(CheckedExceptionPolicy.WRAP, request.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for default null policies.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldDefaultToNullPolicies] Null defaults
   * for deferred resolution
   */
  @Test
  @Ignore("Awaiting implementation in #278")
  public void shouldDefaultToNullPolicies() {
    // Given: InterceptRequest constructed without explicit policies
    // When: Getting both exception propagation and checked exception policies
    // Then: Both policies return null (indicating they defer to global defaults)

    // TODO: Implement after #278 provides the implementation
    // Expected usage:
    // InterceptRequest<?> request = new InterceptRequest<>(
    //     uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod);
    // assertNull(request.getExceptionPropagationPolicy());
    // assertNull(request.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for JSON serialization of policies.
   *
   * <p>Acceptance Criterion: [TEST:InterceptRequestTest.shouldSerializeAndDeserializePolicies] JSON
   * serialization works
   */
  @Test
  @Ignore("Awaiting implementation in #278")
  public void shouldSerializeAndDeserializePolicies() {
    // Given: InterceptRequest with both exception propagation and checked exception policies set
    // When: Serializing to bytes and deserializing back
    // Then: Both policies are preserved after round-trip serialization

    // TODO: Implement after #278 provides the implementation
    // Expected usage:
    // InterceptRequest<?> original = new InterceptRequest<>(
    //     uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableMethod,
    //     false, ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY,
    //     CheckedExceptionPolicy.REJECT);
    // byte[] bytes = original.toBytes(StandardCharsets.UTF_8);
    // InterceptRequest<?> deserialized = InterceptRequest.fromBytes(bytes, StandardCharsets.UTF_8);
    // assertEquals(ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY,
    //     deserialized.getExceptionPropagationPolicy());
    // assertEquals(CheckedExceptionPolicy.REJECT, deserialized.getCheckedExceptionPolicy());
  }
}
