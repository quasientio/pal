/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
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
                          + ", ctime="
                          + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                          + ", mtime="
                          + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                          + '}'));
            });
  }
}
