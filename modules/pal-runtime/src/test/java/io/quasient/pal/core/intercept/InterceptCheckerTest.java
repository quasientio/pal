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
package io.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link InterceptChecker}. */
@SuppressWarnings("DoNotMock")
public class InterceptCheckerTest {

  /** Test peer UUID for local intercept filtering. */
  private static final UUID TEST_PEER_UUID = UUID.randomUUID();

  private InterceptMatcher interceptMatcher;
  private InterceptChecker interceptChecker;

  @Before
  public void setUp() {
    interceptMatcher = mock(InterceptMatcher.class);
    interceptChecker = new InterceptChecker(interceptMatcher, TEST_PEER_UUID);
  }

  @Test
  public void checkIntercepts_withMethodSignature_extractsCorrectInfo() {
    // Setup mock ProceedingJoinPoint with method signature
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(methodSig.getName()).thenReturn("myMethod");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {String.class, Integer.class});

    // Mock matcher to return empty list
    when(interceptMatcher.getMatchingIntercepts(
            eq("com.example.MyClass"),
            eq("myMethod"),
            any(String[].class),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify
    assertThat(result, is(notNullValue()));
    assertThat(result.hasRemoteIntercepts(), is(false));
    assertThat(result.hasLocalIntercepts(), is(false));

    // Verify matcher was called with correct extracted parameters
    verify(interceptMatcher)
        .getMatchingIntercepts(
            eq("com.example.MyClass"),
            eq("myMethod"),
            eq(new String[] {"java.lang.String", "java.lang.Integer"}),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE));
  }

  @Test
  public void checkIntercepts_withConstructorSignature_usesNewAsName() {
    // Setup mock ProceedingJoinPoint with constructor signature
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    ConstructorSignature ctorSig = mock(ConstructorSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(ctorSig);
    when(ctorSig.getDeclaringTypeName()).thenReturn("java.util.ArrayList");
    when(ctorSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    when(interceptMatcher.getMatchingIntercepts(
            eq("java.util.ArrayList"),
            eq("new"),
            any(String[].class),
            eq(MessageType.EXEC_CONSTRUCTOR),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute
    interceptChecker.checkIntercepts(pjp, MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE);

    // Verify constructor uses "new" as executable name
    verify(interceptMatcher)
        .getMatchingIntercepts(
            eq("java.util.ArrayList"),
            eq("new"),
            eq(new String[] {}),
            eq(MessageType.EXEC_CONSTRUCTOR),
            eq(ExecPhase.BEFORE));
  }

  @Test
  public void checkIntercepts_withFieldSignature_extractsFieldName() {
    // Setup mock ProceedingJoinPoint with field signature
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    FieldSignature fieldSig = mock(FieldSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(fieldSig);
    when(fieldSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(fieldSig.getName()).thenReturn("myField");

    when(interceptMatcher.getMatchingIntercepts(
            eq("com.example.MyClass"),
            eq("myField"),
            eq(null), // String[]
            eq(MessageType.EXEC_GET_FIELD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute
    interceptChecker.checkIntercepts(pjp, MessageType.EXEC_GET_FIELD, ExecPhase.BEFORE);

    // Verify field has null parameter types
    verify(interceptMatcher)
        .getMatchingIntercepts(
            eq("com.example.MyClass"),
            eq("myField"),
            eq(null), // String[]
            eq(MessageType.EXEC_GET_FIELD),
            eq(ExecPhase.BEFORE));
  }

  @Test
  public void checkIntercepts_withMatchingIntercepts_returnsRemoteIntercepts() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.Calculator");
    when(methodSig.getName()).thenReturn("add");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {int.class, int.class});

    // Create matching intercepts
    InterceptMessage intercept1 = new InterceptMessage();
    intercept1.peerUuid =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-1".getBytes(StandardCharsets.UTF_8)));
    InterceptMessage intercept2 = new InterceptMessage();
    intercept2.peerUuid =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-2".getBytes(StandardCharsets.UTF_8)));
    List<InterceptMessage> matchingIntercepts = List.of(intercept1, intercept2);

    when(interceptMatcher.getMatchingIntercepts(
            eq("com.example.Calculator"),
            eq("add"),
            eq(new String[] {"int", "int"}),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(matchingIntercepts);

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify
    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.getRemoteIntercepts().size(), is(2));
    assertThat(result.needsExecMessage(), is(true));
  }

  @Test
  public void checkIntercepts_afterPhase_passesCorrectPhase() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(methodSig.getName()).thenReturn("method");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    when(interceptMatcher.getMatchingIntercepts(
            any(), any(), any(), eq(MessageType.EXEC_INSTANCE_METHOD), eq(ExecPhase.AFTER)))
        .thenReturn(Collections.emptyList());

    // Execute with AFTER phase
    interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.AFTER);

    // Verify AFTER phase was passed
    verify(interceptMatcher).getMatchingIntercepts(any(), any(), any(), any(), eq(ExecPhase.AFTER));
  }

  /**
   * Tests that intercepts with remote peer UUIDs are classified as remote intercepts.
   *
   * <p>When an intercept's callback peer UUID differs from the local peer UUID, it should be
   * filtered into the remote intercepts list.
   */
  @Test
  public void checkIntercepts_withRemotePeerUuid_isRemoteIntercept() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(methodSig.getName()).thenReturn("method");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    // Create intercept with a different peer UUID
    InterceptMessage intercept = new InterceptMessage();
    intercept.setPeerUuid(UuidUtils.toBytes(UUID.randomUUID())); // Different from TEST_PEER_UUID
    when(interceptMatcher.getMatchingIntercepts(any(), any(), any(), any(), any()))
        .thenReturn(List.of(intercept));

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify intercept is classified as remote
    assertThat(result.getRemoteIntercepts().size(), is(1));
    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.getLocalIntercepts(), is(empty()));
    assertThat(result.hasLocalIntercepts(), is(false));
  }

  /**
   * Tests that intercepts with the local peer UUID are classified as local intercepts.
   *
   * <p>When an intercept's callback peer UUID matches the local peer UUID, it should be filtered
   * into the local intercepts list.
   */
  @Test
  public void checkIntercepts_withLocalPeerUuid_isLocalIntercept() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(methodSig.getName()).thenReturn("method");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    // Create intercept with same peer UUID as test instance
    InterceptMessage intercept = new InterceptMessage();
    intercept.setPeerUuid(UuidUtils.toBytes(TEST_PEER_UUID)); // Same as local peer
    when(interceptMatcher.getMatchingIntercepts(any(), any(), any(), any(), any()))
        .thenReturn(List.of(intercept));

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify intercept is classified as local
    assertThat(result.getLocalIntercepts().size(), is(1));
    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.getRemoteIntercepts(), is(empty()));
    assertThat(result.hasRemoteIntercepts(), is(false));
  }

  /**
   * Tests that a mix of local and remote intercepts are correctly separated.
   *
   * <p>When there are both local and remote intercepts, they should be filtered into their
   * respective lists correctly.
   */
  @Test
  public void checkIntercepts_withMixedIntercepts_separatesCorrectly() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.MyClass");
    when(methodSig.getName()).thenReturn("method");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    // Create one local and one remote intercept
    InterceptMessage localIntercept = new InterceptMessage();
    localIntercept.setPeerUuid(UuidUtils.toBytes(TEST_PEER_UUID));

    InterceptMessage remoteIntercept = new InterceptMessage();
    remoteIntercept.setPeerUuid(UuidUtils.toBytes(UUID.randomUUID()));

    when(interceptMatcher.getMatchingIntercepts(any(), any(), any(), any(), any()))
        .thenReturn(List.of(localIntercept, remoteIntercept));

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify intercepts are correctly separated
    assertThat(result.getLocalIntercepts().size(), is(1));
    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.getRemoteIntercepts().size(), is(1));
    assertThat(result.hasRemoteIntercepts(), is(true));
  }

  @Test
  public void checkIntercepts_withEmptyParameterArray_handlesCorrectly() {
    // Setup mock ProceedingJoinPoint
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart staticPart = mock(JoinPoint.StaticPart.class);
    MethodSignature methodSig = mock(MethodSignature.class);

    when(pjp.getStaticPart()).thenReturn(staticPart);
    when(staticPart.getSignature()).thenReturn(methodSig);
    when(methodSig.getDeclaringTypeName()).thenReturn("com.example.NoArgsMethod");
    when(methodSig.getName()).thenReturn("noArgs");
    when(methodSig.getParameterTypes()).thenReturn(new Class<?>[] {});

    when(interceptMatcher.getMatchingIntercepts(
            eq("com.example.NoArgsMethod"),
            eq("noArgs"),
            eq(new String[] {}),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute
    interceptChecker.checkIntercepts(pjp, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify empty parameter array is handled
    verify(interceptMatcher)
        .getMatchingIntercepts(
            any(), any(), eq(new String[] {}), eq(MessageType.EXEC_INSTANCE_METHOD), any());
  }

  // ========== isInterceptableType Tests ==========

  /** Tests that EXEC_CONSTRUCTOR is an interceptable type. */
  @Test
  public void isInterceptableType_constructor_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_CONSTRUCTOR), is(true));
  }

  /** Tests that EXEC_INSTANCE_METHOD is an interceptable type. */
  @Test
  public void isInterceptableType_instanceMethod_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /** Tests that EXEC_CLASS_METHOD is an interceptable type. */
  @Test
  public void isInterceptableType_classMethod_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_CLASS_METHOD), is(true));
  }

  /** Tests that EXEC_GET_STATIC is an interceptable type. */
  @Test
  public void isInterceptableType_getStatic_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_GET_STATIC), is(true));
  }

  /** Tests that EXEC_GET_FIELD is an interceptable type. */
  @Test
  public void isInterceptableType_getField_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_GET_FIELD), is(true));
  }

  /** Tests that EXEC_PUT_STATIC is an interceptable type. */
  @Test
  public void isInterceptableType_putStatic_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_PUT_STATIC), is(true));
  }

  /** Tests that EXEC_PUT_FIELD is an interceptable type. */
  @Test
  public void isInterceptableType_putField_returnsTrue() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_PUT_FIELD), is(true));
  }

  /** Tests that CONTROL_MESSAGE_REQUEST is not an interceptable type. */
  @Test
  public void isInterceptableType_controlMessageRequest_returnsFalse() {
    assertThat(
        InterceptChecker.isInterceptableType(MessageType.CONTROL_MESSAGE_REQUEST), is(false));
  }

  /** Tests that META_MESSAGE_REQUEST is not an interceptable type. */
  @Test
  public void isInterceptableType_metaMessageRequest_returnsFalse() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.META_MESSAGE_REQUEST), is(false));
  }

  /** Tests that EXEC_RETURN_VALUE is not an interceptable type. */
  @Test
  public void isInterceptableType_execReturnValue_returnsFalse() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_RETURN_VALUE), is(false));
  }

  /** Tests that EXEC_THROWABLE is not an interceptable type. */
  @Test
  public void isInterceptableType_execThrowable_returnsFalse() {
    assertThat(InterceptChecker.isInterceptableType(MessageType.EXEC_THROWABLE), is(false));
  }

  // ========== checkIntercepts with Explicit Parameters Tests ==========

  /** Tests the overload that accepts explicit string parameters. */
  @Test
  public void checkIntercepts_withExplicitParams_passesToMatcher() {
    String className = "com.example.Calculator";
    String methodName = "calculate";
    String[] paramTypes = new String[] {"int", "int"};

    when(interceptMatcher.getMatchingIntercepts(
            eq(className),
            eq(methodName),
            eq(paramTypes),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute with explicit parameters
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(
            className, methodName, paramTypes, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify
    assertThat(result, is(notNullValue()));
    verify(interceptMatcher)
        .getMatchingIntercepts(
            eq(className),
            eq(methodName),
            eq(paramTypes),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(ExecPhase.BEFORE));
  }

  /** Tests that explicit parameters with null paramTypes works for fields. */
  @Test
  public void checkIntercepts_withNullParamTypes_worksForFields() {
    String className = "com.example.MyClass";
    String fieldName = "myField";

    when(interceptMatcher.getMatchingIntercepts(
            eq(className),
            eq(fieldName),
            eq(null),
            eq(MessageType.EXEC_GET_FIELD),
            eq(ExecPhase.BEFORE)))
        .thenReturn(Collections.emptyList());

    // Execute with null param types (for field access)
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(
            className, fieldName, null, MessageType.EXEC_GET_FIELD, ExecPhase.BEFORE);

    // Verify
    assertThat(result, is(notNullValue()));
    verify(interceptMatcher)
        .getMatchingIntercepts(
            eq(className), eq(fieldName), eq(null), eq(MessageType.EXEC_GET_FIELD), any());
  }

  /** Tests explicit params with matching intercepts. */
  @Test
  public void checkIntercepts_explicitParams_withMatches_returnsCorrectResult() {
    String className = "com.example.Service";
    String methodName = "process";
    String[] paramTypes = new String[] {"java.lang.String"};

    // Create a remote intercept
    InterceptMessage intercept = new InterceptMessage();
    intercept.setPeerUuid(UuidUtils.toBytes(UUID.randomUUID()));

    when(interceptMatcher.getMatchingIntercepts(any(), any(), any(), any(), any()))
        .thenReturn(List.of(intercept));

    // Execute
    InterceptCheckResult result =
        interceptChecker.checkIntercepts(
            className, methodName, paramTypes, MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE);

    // Verify
    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.getRemoteIntercepts().size(), is(1));
  }
}
