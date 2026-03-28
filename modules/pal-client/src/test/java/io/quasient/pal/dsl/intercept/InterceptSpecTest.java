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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for the {@code InterceptSpec} value class and its builder.
 *
 * <p>These tests define the contract for {@code InterceptSpec}. Each test documents expected
 * behavior via Given/When/Then comments.
 */
public class InterceptSpecTest {

  @Test
  public void builder_setsAllFields() {
    // Given: An InterceptSpec builder with all fields explicitly set
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("placeOrder")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.FraudChecker")
            .callbackMethod("verify")
            .parameterTypes(Arrays.asList("java.lang.String", "int"))
            .kind(InterceptableKind.FIELD)
            .fieldOpType(FieldOpType.GET)
            .peerOverride("peer-1")
            .priorityOverride(10)
            .ttlOverride(Duration.ofMinutes(5))
            .forceImmediateOverride(true)
            .exceptionPolicyOverride(ExceptionPropagationPolicy.PROPAGATE_ALL)
            .checkedExceptionPolicyOverride(CheckedExceptionPolicy.WRAP)
            .build();

    // Then: Each getter returns the correct value that was set
    assertThat(spec.getTargetClass(), is("com.acme.OrderService"));
    assertThat(spec.getTargetName(), is("placeOrder"));
    assertThat(spec.getType(), is(InterceptType.BEFORE));
    assertThat(spec.getCallbackClass(), is("com.acme.FraudChecker"));
    assertThat(spec.getCallbackMethod(), is("verify"));
    assertThat(spec.getParameterTypes(), is(Arrays.asList("java.lang.String", "int")));
    assertThat(spec.getKind(), is(InterceptableKind.FIELD));
    assertThat(spec.getFieldOpType(), is(FieldOpType.GET));
    assertThat(spec.getPeerOverride(), is("peer-1"));
    assertThat(spec.getPriorityOverride(), is(10));
    assertThat(spec.getTtlOverride(), is(Duration.ofMinutes(5)));
    assertThat(spec.getForceImmediateOverride(), is(true));
    assertThat(spec.getExceptionPolicyOverride(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(spec.getCheckedExceptionPolicyOverride(), is(CheckedExceptionPolicy.WRAP));
  }

  @Test
  public void builder_defaultsKindToMethod() {
    // Given: An InterceptSpec builder with required fields set but kind not set
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.AFTER)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    // Then: getKind() returns InterceptableKind.METHOD
    assertThat(spec.getKind(), is(InterceptableKind.METHOD));
  }

  @Test(expected = NullPointerException.class)
  public void builder_requiresTargetClass() {
    // Given: An InterceptSpec builder with targetName and type set, but targetClass omitted
    // When: build() is called
    // Then: NullPointerException is thrown
    InterceptSpec.builder()
        .targetName("bar")
        .type(InterceptType.BEFORE)
        .callbackClass("com.acme.Cb")
        .callbackMethod("onBar")
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void builder_requiresTargetName() {
    // Given: An InterceptSpec builder with targetClass and type set, but targetName omitted
    // When: build() is called
    // Then: NullPointerException is thrown
    InterceptSpec.builder()
        .targetClass("com.acme.Foo")
        .type(InterceptType.BEFORE)
        .callbackClass("com.acme.Cb")
        .callbackMethod("onBar")
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void builder_requiresType() {
    // Given: An InterceptSpec builder with targetClass and targetName set, but type omitted
    // When: build() is called
    // Then: NullPointerException is thrown
    InterceptSpec.builder()
        .targetClass("com.acme.Foo")
        .targetName("bar")
        .callbackClass("com.acme.Cb")
        .callbackMethod("onBar")
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void builder_requiresCallbackClassAndMethod() {
    // Given: An InterceptSpec builder with target fields and type set,
    //        but callbackClass and callbackMethod omitted
    // When: build() is called
    // Then: NullPointerException is thrown
    InterceptSpec.builder()
        .targetClass("com.acme.Foo")
        .targetName("bar")
        .type(InterceptType.BEFORE)
        .build();
  }

  @Test(expected = IllegalStateException.class)
  public void builder_fieldKindRequiresFieldOpType() {
    // Given: An InterceptSpec builder with kind set to FIELD but fieldOpType not set
    // When: build() is called
    // Then: IllegalStateException is thrown
    InterceptSpec.builder()
        .targetClass("com.acme.Foo")
        .targetName("status")
        .type(InterceptType.AFTER)
        .callbackClass("com.acme.Cb")
        .callbackMethod("onField")
        .kind(InterceptableKind.FIELD)
        .build();
  }

  @Test
  public void builder_parameterTypesDefaultsToEmptyList() {
    // Given: An InterceptSpec builder with required fields set but parameterTypes not set
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    // Then: getParameterTypes() returns an empty list
    assertNotNull(spec.getParameterTypes());
    assertTrue(spec.getParameterTypes().isEmpty());
  }

  @Test
  public void toInterceptRequest_methodCall() {
    // Given: A method-based InterceptSpec (kind=METHOD)
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("placeOrder")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.FraudChecker")
            .callbackMethod("verify")
            .parameterTypes(Arrays.asList("com.acme.Order"))
            .build();

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, InterceptBundleDefaults.EMPTY);

    // Then: The returned InterceptRequest has correct values
    assertThat(request.getUuid(), is(interceptUuid));
    assertThat(request.getPeer(), is(peerUuid));
    assertThat(request.getType(), is(InterceptType.BEFORE));
    assertThat(request.getClazz(), is("com.acme.OrderService"));
    assertThat(request.getCallbackClass(), is("com.acme.FraudChecker"));
    assertThat(request.getCallbackMethod(), is("verify"));
    assertTrue(request.getInterceptable() instanceof InterceptableMethodCall);
    InterceptableMethodCall mc = (InterceptableMethodCall) request.getInterceptable();
    assertThat(mc.getName(), is("placeOrder"));
    assertThat(mc.getParameterTypes(), is(Arrays.asList("com.acme.Order")));
  }

  @Test
  public void toInterceptRequest_fieldOp() {
    // Given: A field-based InterceptSpec with kind=FIELD, fieldOpType=GET
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("status")
            .type(InterceptType.AFTER)
            .callbackClass("com.acme.FieldAuditor")
            .callbackMethod("onFieldRead")
            .kind(InterceptableKind.FIELD)
            .fieldOpType(FieldOpType.GET)
            .build();

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, InterceptBundleDefaults.EMPTY);

    // Then: The returned InterceptRequest has correct values
    assertThat(request.getUuid(), is(interceptUuid));
    assertThat(request.getPeer(), is(peerUuid));
    assertTrue(request.getInterceptable() instanceof InterceptableFieldOp);
    InterceptableFieldOp fo = (InterceptableFieldOp) request.getInterceptable();
    assertThat(fo.getName(), is("status"));
    assertThat(fo.getFieldOpType(), is(FieldOpType.GET));
  }

  @Test
  public void toInterceptRequest_usesDefaultsWhenNoOverride() {
    // Given: An InterceptSpec with no priority or TTL overrides
    //        and InterceptBundleDefaults with priority=5 and ttl=60s
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(null, 5, Duration.ofSeconds(60), null, null, null, null);

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

    // Then: The returned InterceptRequest has priority=5 and ttlSeconds=60
    assertThat(request.getPriority(), is(5));
    assertThat(request.getTtlSeconds(), is(60L));
  }

  @Test
  public void toInterceptRequest_overridesTakesPrecedenceOverDefaults() {
    // Given: An InterceptSpec with priorityOverride=10
    //        and InterceptBundleDefaults with priority=5
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .priorityOverride(10)
            .build();

    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(null, 5, null, null, null, null, null);

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

    // Then: The returned InterceptRequest has priority=10 (override wins)
    assertThat(request.getPriority(), is(10));
  }

  @Test
  public void builder_callbackTimeoutOverride_setsCorrectly() {
    // Given: An InterceptSpec builder with callbackTimeoutOverride set to 5 seconds
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .callbackTimeoutOverride(Duration.ofSeconds(5))
            .build();

    // Then: getCallbackTimeoutOverride() returns the correct Duration
    assertThat(spec.getCallbackTimeoutOverride(), is(Duration.ofSeconds(5)));
  }

  @Test
  public void toInterceptRequest_callbackTimeoutFromOverride_resolvesCorrectly() {
    // Given: An InterceptSpec with callbackTimeoutOverride = 5000ms
    //        and defaults with no callbackTimeout
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .callbackTimeoutOverride(Duration.ofSeconds(5))
            .build();

    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(null, null, null, null, null, null, null);

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

    // Then: The returned InterceptRequest has callbackTimeoutMs = 5000
    assertThat(request.getCallbackTimeoutMs(), is(5000L));
  }

  @Test
  public void toInterceptRequest_callbackTimeoutFromDefaults_resolvesCorrectly() {
    // Given: An InterceptSpec with no callbackTimeoutOverride
    //        and defaults with callbackTimeout = 3 seconds
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(null, null, null, null, null, null, Duration.ofSeconds(3));

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

    // Then: The returned InterceptRequest has callbackTimeoutMs = 3000
    assertThat(request.getCallbackTimeoutMs(), is(3000L));
  }

  @Test
  public void toInterceptRequest_callbackTimeoutAbsent_isNull() {
    // Given: An InterceptSpec with no callbackTimeoutOverride
    //        and defaults with no callbackTimeout
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(null, null, null, null, null, null, null);

    UUID interceptUuid = UUID.randomUUID();
    UUID peerUuid = UUID.randomUUID();

    // When: toInterceptRequest is called
    InterceptRequest<? extends Interceptable> request =
        spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

    // Then: The returned InterceptRequest has callbackTimeoutMs = null
    assertThat(request.getCallbackTimeoutMs(), is(nullValue()));
  }
}
