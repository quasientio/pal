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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code InterceptBundleParser}.
 *
 * <p>These tests define the YAML schema contract for intercept bundle files. Each test documents a
 * specific aspect of the schema: valid bundles, target parsing, duration strings, field intercepts,
 * validation errors, and security (SafeConstructor).
 *
 * @see InterceptBundleSpec
 * @see InterceptSpec
 * @see InterceptBundleDefaults
 */
public class InterceptBundleParserTest {

  private final InterceptBundleParser parser = new InterceptBundleParser();

  @Test
  public void parse_validFullBundle() {
    // Given
    String yaml =
        """
        bundle: fraud-check-v1
        defaults:
          peer: fraud-checker
          priority: 5
          ttl: 30s
          forceImmediate: true
          exceptionPolicy: PROPAGATE_ALL
          checkedExceptionPolicy: WRAP
        intercepts:
          - target: com.acme.OrderService.placeOrder
            type: BEFORE
            callback:
              class: com.acme.FraudChecker
              method: verify
          - target: com.acme.OrderService.refund
            type: AROUND
            callback:
              class: com.acme.FraudChecker
              method: wrapRefund
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getBundleName(), is("fraud-check-v1"));

    InterceptBundleDefaults defaults = bundle.getDefaults();
    assertThat(defaults.getPeer(), is("fraud-checker"));
    assertThat(defaults.getPriority(), is(5));
    assertThat(defaults.getTtl(), is(Duration.ofSeconds(30)));
    assertThat(defaults.getForceImmediate(), is(true));
    assertThat(defaults.getExceptionPolicy(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(defaults.getCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));

    assertThat(bundle.getIntercepts().size(), is(2));

    InterceptSpec first = bundle.getIntercepts().get(0);
    assertThat(first.getTargetClass(), is("com.acme.OrderService"));
    assertThat(first.getTargetName(), is("placeOrder"));
    assertThat(first.getType(), is(InterceptType.BEFORE));
    assertThat(first.getCallbackClass(), is("com.acme.FraudChecker"));
    assertThat(first.getCallbackMethod(), is("verify"));

    InterceptSpec second = bundle.getIntercepts().get(1);
    assertThat(second.getTargetClass(), is("com.acme.OrderService"));
    assertThat(second.getTargetName(), is("refund"));
    assertThat(second.getType(), is(InterceptType.AROUND));
    assertThat(second.getCallbackClass(), is("com.acme.FraudChecker"));
    assertThat(second.getCallbackMethod(), is("wrapRefund"));
  }

  @Test
  public void parse_minimalBundle() {
    // Given
    String yaml =
        """
        bundle: minimal
        intercepts:
          - target: com.acme.Service.doWork
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getBundleName(), is("minimal"));
    assertThat(bundle.getDefaults().getPeer(), is(nullValue()));
    assertThat(bundle.getDefaults().getPriority(), is(nullValue()));
    assertThat(bundle.getDefaults().getTtl(), is(nullValue()));
    assertThat(bundle.getDefaults().getForceImmediate(), is(nullValue()));
    assertThat(bundle.getDefaults().getExceptionPolicy(), is(nullValue()));
    assertThat(bundle.getDefaults().getCheckedExceptionPolicy(), is(nullValue()));

    assertThat(bundle.getIntercepts().size(), is(1));
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getTargetClass(), is("com.acme.Service"));
    assertThat(spec.getTargetName(), is("doWork"));
    assertThat(spec.getType(), is(InterceptType.BEFORE));
    assertThat(spec.getCallbackClass(), is("com.acme.Handler"));
    assertThat(spec.getCallbackMethod(), is("handle"));
    assertThat(spec.getPeerOverride(), is(nullValue()));
    assertThat(spec.getPriorityOverride(), is(nullValue()));
    assertThat(spec.getTtlOverride(), is(nullValue()));
    assertThat(spec.getForceImmediateOverride(), is(nullValue()));
    assertThat(spec.getExceptionPolicyOverride(), is(nullValue()));
    assertThat(spec.getCheckedExceptionPolicyOverride(), is(nullValue()));
  }

  @Test
  public void parse_targetParsing_classAndMethodSplit() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.payment.OrderService.placeOrder
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getTargetClass(), is("com.acme.payment.OrderService"));
    assertThat(spec.getTargetName(), is("placeOrder"));
  }

  @Test
  public void parse_targetParsing_innerClass() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Outer$Inner.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getTargetClass(), is("com.acme.Outer$Inner"));
    assertThat(spec.getTargetName(), is("method"));
  }

  @Test
  public void parse_fieldIntercept() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.status
            kind: field
            fieldOp: GET
            type: AFTER
            callback:
              class: com.acme.Auditor
              method: onFieldRead
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getKind(), is(InterceptableKind.FIELD));
    assertThat(spec.getFieldOpType(), is(FieldOpType.GET));
    assertThat(spec.getTargetClass(), is("com.acme.Service"));
    assertThat(spec.getTargetName(), is("status"));
  }

  @Test
  public void parse_fieldIntercept_putOp() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.config
            kind: field
            fieldOp: PUT
            type: BEFORE
            callback:
              class: com.acme.Auditor
              method: onFieldWrite
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getKind(), is(InterceptableKind.FIELD));
    assertThat(spec.getFieldOpType(), is(FieldOpType.PUT));
  }

  @Test
  public void parse_parameterTypes() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.process
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
            params:
              - java.lang.String
              - int
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    InterceptSpec spec = bundle.getIntercepts().get(0);
    assertThat(spec.getParameterTypes(), is(Arrays.asList("java.lang.String", "int")));
  }

  @Test
  public void parse_durationParsing_seconds() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          ttl: 30s
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getTtl(), is(Duration.ofSeconds(30)));
  }

  @Test
  public void parse_durationParsing_minutes() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          ttl: 5m
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getTtl(), is(Duration.ofMinutes(5)));
  }

  @Test
  public void parse_durationParsing_hours() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          ttl: 1h
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getTtl(), is(Duration.ofHours(1)));
  }

  @Test
  public void parse_durationParsing_days() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          ttl: 1d
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getTtl(), is(Duration.ofDays(1)));
  }

  @Test
  public void parse_durationParsing_zeroMeansNoTtl() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          ttl: 0s
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getTtl(), is(Duration.ZERO));
  }

  @Test
  public void parse_interceptOverridesDefaults() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          priority: 0
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
            priority: 10
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getDefaults().getPriority(), is(0));
    assertThat(bundle.getIntercepts().get(0).getPriorityOverride(), is(10));
  }

  @Test
  public void parse_interceptTypeCaseInsensitive() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.method
            type: before
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(bundle.getIntercepts().get(0).getType(), is(InterceptType.BEFORE));
  }

  @Test
  public void parse_allInterceptTypes() {
    // Given
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.m1
            type: BEFORE
            callback:
              class: com.acme.H
              method: h
          - target: com.acme.Service.m2
            type: AFTER
            callback:
              class: com.acme.H
              method: h
          - target: com.acme.Service.m3
            type: AROUND
            callback:
              class: com.acme.H
              method: h
          - target: com.acme.Service.m4
            type: BEFORE_ASYNC
            callback:
              class: com.acme.H
              method: h
          - target: com.acme.Service.m5
            type: AFTER_ASYNC
            callback:
              class: com.acme.H
              method: h
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    List<InterceptSpec> specs = bundle.getIntercepts();
    assertThat(specs.size(), is(5));
    assertThat(specs.get(0).getType(), is(InterceptType.BEFORE));
    assertThat(specs.get(1).getType(), is(InterceptType.AFTER));
    assertThat(specs.get(2).getType(), is(InterceptType.AROUND));
    assertThat(specs.get(3).getType(), is(InterceptType.BEFORE_ASYNC));
    assertThat(specs.get(4).getType(), is(InterceptType.AFTER_ASYNC));
  }

  @Test
  public void parse_exceptionPolicies() {
    // Given
    String yaml =
        """
        bundle: test
        defaults:
          exceptionPolicy: PROPAGATE_ALL
          checkedExceptionPolicy: WRAP
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;

    // When
    InterceptBundleSpec bundle = parser.parse(yaml);

    // Then
    assertThat(
        bundle.getDefaults().getExceptionPolicy(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(bundle.getDefaults().getCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_emptyYaml_throws() {
    parser.parse("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_missingBundleName_throws() {
    String yaml =
        """
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;
    parser.parse(yaml);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_missingIntercepts_throws() {
    parser.parse("bundle: test\n");
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_emptyInterceptsList_throws() {
    parser.parse("bundle: test\nintercepts: []\n");
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_missingTarget_throws() {
    String yaml =
        """
        bundle: test
        intercepts:
          - type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;
    parser.parse(yaml);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_missingType_throws() {
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.method
            callback:
              class: com.acme.Handler
              method: handle
        """;
    parser.parse(yaml);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_missingCallback_throws() {
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
        """;
    parser.parse(yaml);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_fieldKindWithoutFieldOp_throws() {
    String yaml =
        """
        bundle: test
        intercepts:
          - target: com.acme.Service.status
            kind: field
            type: AFTER
            callback:
              class: com.acme.Auditor
              method: onFieldRead
        """;
    parser.parse(yaml);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_invalidDurationFormat_throws() {
    String yaml =
        """
        bundle: test
        defaults:
          ttl: abc
        intercepts:
          - target: com.acme.Service.method
            type: BEFORE
            callback:
              class: com.acme.Handler
              method: handle
        """;
    parser.parse(yaml);
  }

  @Test(expected = Exception.class)
  public void parse_useSafeConstructor() {
    // A malicious YAML containing a Java type tag that SafeConstructor rejects
    String maliciousYaml = "bundle: !!java.lang.Runtime test\nintercepts: []\n";
    parser.parse(maliciousYaml);
  }
}
