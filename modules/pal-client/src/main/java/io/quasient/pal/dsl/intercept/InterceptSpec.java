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

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Immutable value class for a single intercept definition in the DSL.
 *
 * <p>An {@code InterceptSpec} describes a single intercept: what to intercept (target class and
 * method/field), when to intercept (before, after, around), and what callback to invoke. It also
 * carries optional overrides for priority, TTL, and exception policies that take precedence over
 * bundle-level defaults.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * InterceptSpec spec = InterceptSpec.builder()
 *     .targetClass("com.acme.OrderService")
 *     .targetName("placeOrder")
 *     .type(InterceptType.BEFORE)
 *     .callbackClass("com.acme.FraudChecker")
 *     .callbackMethod("verify")
 *     .build();
 * }</pre>
 *
 * @see InterceptBundleSpec
 * @see InterceptBundleDefaults
 */
public final class InterceptSpec {

  /** The fully qualified target class name. */
  private final String targetClass;

  /** The target method or field name. */
  private final String targetName;

  /** The intercept type (BEFORE, AFTER, AROUND, etc.). */
  private final InterceptType type;

  /** The fully qualified callback class name. */
  private final String callbackClass;

  /** The callback method name. */
  private final String callbackMethod;

  /** Parameter types for overloaded method matching. */
  private final List<String> parameterTypes;

  /** The kind of interceptable (METHOD or FIELD). */
  private final InterceptableKind kind;

  /** The field operation type; {@code null} for method intercepts. */
  @Nullable private final FieldOpType fieldOpType;

  /** Peer override; {@code null} to use bundle defaults. */
  @Nullable private final String peerOverride;

  /** Priority override; {@code null} to use bundle defaults. */
  @Nullable private final Integer priorityOverride;

  /** TTL override; {@code null} to use bundle defaults. */
  @Nullable private final Duration ttlOverride;

  /** Force-immediate override; {@code null} to use bundle defaults. */
  @Nullable private final Boolean forceImmediateOverride;

  /** Exception policy override; {@code null} to use bundle defaults. */
  @Nullable private final ExceptionPropagationPolicy exceptionPolicyOverride;

  /** Checked exception policy override; {@code null} to use bundle defaults. */
  @Nullable private final CheckedExceptionPolicy checkedExceptionPolicyOverride;

  /**
   * Constructs an InterceptSpec from a builder.
   *
   * @param builder the builder containing validated field values
   */
  private InterceptSpec(Builder builder) {
    this.targetClass = builder.targetClass;
    this.targetName = builder.targetName;
    this.type = builder.type;
    this.callbackClass = builder.callbackClass;
    this.callbackMethod = builder.callbackMethod;
    this.parameterTypes = Collections.unmodifiableList(new ArrayList<>(builder.parameterTypes));
    this.kind = builder.kind;
    this.fieldOpType = builder.fieldOpType;
    this.peerOverride = builder.peerOverride;
    this.priorityOverride = builder.priorityOverride;
    this.ttlOverride = builder.ttlOverride;
    this.forceImmediateOverride = builder.forceImmediateOverride;
    this.exceptionPolicyOverride = builder.exceptionPolicyOverride;
    this.checkedExceptionPolicyOverride = builder.checkedExceptionPolicyOverride;
  }

  /**
   * Creates a new builder for constructing {@code InterceptSpec} instances.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the fully qualified target class name.
   *
   * @return the target class name
   */
  public String getTargetClass() {
    return targetClass;
  }

  /**
   * Returns the target method or field name.
   *
   * @return the target name
   */
  public String getTargetName() {
    return targetName;
  }

  /**
   * Returns the intercept type (BEFORE, AFTER, AROUND, etc.).
   *
   * @return the intercept type
   */
  public InterceptType getType() {
    return type;
  }

  /**
   * Returns the fully qualified callback class name.
   *
   * @return the callback class name
   */
  public String getCallbackClass() {
    return callbackClass;
  }

  /**
   * Returns the callback method name.
   *
   * @return the callback method name
   */
  public String getCallbackMethod() {
    return callbackMethod;
  }

  /**
   * Returns the parameter types for overloaded method matching.
   *
   * @return an unmodifiable list of parameter type names; empty if not specified
   */
  public List<String> getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Returns the kind of interceptable (METHOD or FIELD).
   *
   * @return the interceptable kind
   */
  public InterceptableKind getKind() {
    return kind;
  }

  /**
   * Returns the field operation type, or {@code null} for method intercepts.
   *
   * @return the field operation type, or {@code null}
   */
  @Nullable
  public FieldOpType getFieldOpType() {
    return fieldOpType;
  }

  /**
   * Returns the peer override, or {@code null} to use bundle defaults.
   *
   * @return the peer override, or {@code null}
   */
  @Nullable
  public String getPeerOverride() {
    return peerOverride;
  }

  /**
   * Returns the priority override, or {@code null} to use bundle defaults.
   *
   * @return the priority override, or {@code null}
   */
  @Nullable
  public Integer getPriorityOverride() {
    return priorityOverride;
  }

  /**
   * Returns the TTL override, or {@code null} to use bundle defaults.
   *
   * @return the TTL override, or {@code null}
   */
  @Nullable
  public Duration getTtlOverride() {
    return ttlOverride;
  }

  /**
   * Returns the force-immediate override, or {@code null} to use bundle defaults.
   *
   * @return the force-immediate override, or {@code null}
   */
  @Nullable
  public Boolean getForceImmediateOverride() {
    return forceImmediateOverride;
  }

  /**
   * Returns the exception policy override, or {@code null} to use bundle defaults.
   *
   * @return the exception policy override, or {@code null}
   */
  @Nullable
  public ExceptionPropagationPolicy getExceptionPolicyOverride() {
    return exceptionPolicyOverride;
  }

  /**
   * Returns the checked exception policy override, or {@code null} to use bundle defaults.
   *
   * @return the checked exception policy override, or {@code null}
   */
  @Nullable
  public CheckedExceptionPolicy getCheckedExceptionPolicyOverride() {
    return checkedExceptionPolicyOverride;
  }

  /**
   * Converts this spec into an {@link InterceptRequest} by resolving overrides against bundle
   * defaults.
   *
   * <p>For each optional field (priority, TTL, forceImmediate, exception policies), the override
   * value from this spec takes precedence over the bundle default. If neither is set, the
   * InterceptRequest default (0 for priority/TTL, false for forceImmediate, null for policies) is
   * used.
   *
   * @param interceptUuid the UUID for the intercept request
   * @param peerUuid the UUID of the callback peer
   * @param defaults the bundle-level defaults to apply when no override is set
   * @return an {@link InterceptRequest} for either a method call or field operation
   * @throws NullPointerException if any required parameter is {@code null}
   */
  public InterceptRequest<? extends Interceptable> toInterceptRequest(
      UUID interceptUuid, UUID peerUuid, InterceptBundleDefaults defaults) {
    Objects.requireNonNull(interceptUuid, "interceptUuid must not be null");
    Objects.requireNonNull(peerUuid, "peerUuid must not be null");
    Objects.requireNonNull(defaults, "defaults must not be null");

    int resolvedPriority = resolveInt(priorityOverride, defaults.getPriority(), 0);
    long resolvedTtlSeconds = resolveTtlSeconds(ttlOverride, defaults.getTtl());
    boolean resolvedForceImmediate =
        resolveBoolean(forceImmediateOverride, defaults.getForceImmediate(), false);
    ExceptionPropagationPolicy resolvedExceptionPolicy =
        resolveNullable(exceptionPolicyOverride, defaults.getExceptionPolicy());
    CheckedExceptionPolicy resolvedCheckedExceptionPolicy =
        resolveNullable(checkedExceptionPolicyOverride, defaults.getCheckedExceptionPolicy());

    if (kind == InterceptableKind.FIELD) {
      InterceptableFieldOp fieldOp = new InterceptableFieldOp(targetName, fieldOpType);
      return new InterceptRequest<>(
          interceptUuid,
          peerUuid,
          type,
          targetClass,
          callbackClass,
          callbackMethod,
          fieldOp,
          resolvedForceImmediate,
          resolvedExceptionPolicy,
          resolvedCheckedExceptionPolicy,
          resolvedPriority,
          resolvedTtlSeconds);
    }

    InterceptableMethodCall methodCall = new InterceptableMethodCall(targetName, parameterTypes);
    return new InterceptRequest<>(
        interceptUuid,
        peerUuid,
        type,
        targetClass,
        callbackClass,
        callbackMethod,
        methodCall,
        resolvedForceImmediate,
        resolvedExceptionPolicy,
        resolvedCheckedExceptionPolicy,
        resolvedPriority,
        resolvedTtlSeconds);
  }

  /**
   * Resolves an integer value: override first, then default, then fallback.
   *
   * @param override the override value, or {@code null}
   * @param defaultValue the default value, or {@code null}
   * @param fallback the fallback value if both are {@code null}
   * @return the resolved integer
   */
  private static int resolveInt(
      @Nullable Integer override, @Nullable Integer defaultValue, int fallback) {
    if (override != null) {
      return override;
    }
    if (defaultValue != null) {
      return defaultValue;
    }
    return fallback;
  }

  /**
   * Resolves TTL seconds from Duration overrides.
   *
   * @param override the override TTL, or {@code null}
   * @param defaultTtl the default TTL, or {@code null}
   * @return the resolved TTL in seconds; 0 if neither is set
   */
  private static long resolveTtlSeconds(
      @Nullable Duration override, @Nullable Duration defaultTtl) {
    if (override != null) {
      return override.toSeconds();
    }
    if (defaultTtl != null) {
      return defaultTtl.toSeconds();
    }
    return 0;
  }

  /**
   * Resolves a boolean value: override first, then default, then fallback.
   *
   * @param override the override value, or {@code null}
   * @param defaultValue the default value, or {@code null}
   * @param fallback the fallback value if both are {@code null}
   * @return the resolved boolean
   */
  private static boolean resolveBoolean(
      @Nullable Boolean override, @Nullable Boolean defaultValue, boolean fallback) {
    if (override != null) {
      return override;
    }
    if (defaultValue != null) {
      return defaultValue;
    }
    return fallback;
  }

  /**
   * Resolves a nullable value: override first, then default.
   *
   * @param override the override value, or {@code null}
   * @param defaultValue the default value, or {@code null}
   * @param <V> the value type
   * @return the resolved value, or {@code null} if neither is set
   */
  @Nullable
  private static <V> V resolveNullable(@Nullable V override, @Nullable V defaultValue) {
    return override != null ? override : defaultValue;
  }

  /**
   * Builder for constructing {@link InterceptSpec} instances.
   *
   * <p>Required fields: {@code targetClass}, {@code targetName}, {@code type}, {@code
   * callbackClass}, {@code callbackMethod}. When {@code kind} is {@link InterceptableKind#FIELD},
   * {@code fieldOpType} is also required.
   */
  public static final class Builder {

    /** The fully qualified target class name. */
    private String targetClass;

    /** The target method or field name. */
    private String targetName;

    /** The intercept type. */
    private InterceptType type;

    /** The fully qualified callback class name. */
    private String callbackClass;

    /** The callback method name. */
    private String callbackMethod;

    /** Parameter types for overloaded method matching. */
    private List<String> parameterTypes = Collections.emptyList();

    /** The kind of interceptable; defaults to METHOD. */
    private InterceptableKind kind = InterceptableKind.METHOD;

    /** The field operation type; {@code null} for method intercepts. */
    @Nullable private FieldOpType fieldOpType;

    /** Peer override; {@code null} to use bundle defaults. */
    @Nullable private String peerOverride;

    /** Priority override; {@code null} to use bundle defaults. */
    @Nullable private Integer priorityOverride;

    /** TTL override; {@code null} to use bundle defaults. */
    @Nullable private Duration ttlOverride;

    /** Force-immediate override; {@code null} to use bundle defaults. */
    @Nullable private Boolean forceImmediateOverride;

    /** Exception policy override; {@code null} to use bundle defaults. */
    @Nullable private ExceptionPropagationPolicy exceptionPolicyOverride;

    /** Checked exception policy override; {@code null} to use bundle defaults. */
    @Nullable private CheckedExceptionPolicy checkedExceptionPolicyOverride;

    /** Constructs a new empty builder. */
    private Builder() {}

    /**
     * Sets the target class name.
     *
     * @param targetClass the fully qualified target class name
     * @return this builder
     */
    public Builder targetClass(String targetClass) {
      this.targetClass = targetClass;
      return this;
    }

    /**
     * Sets the target method or field name.
     *
     * @param targetName the target name
     * @return this builder
     */
    public Builder targetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    /**
     * Sets the intercept type.
     *
     * @param type the intercept type
     * @return this builder
     */
    public Builder type(InterceptType type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the callback class name.
     *
     * @param callbackClass the fully qualified callback class name
     * @return this builder
     */
    public Builder callbackClass(String callbackClass) {
      this.callbackClass = callbackClass;
      return this;
    }

    /**
     * Sets the callback method name.
     *
     * @param callbackMethod the callback method name
     * @return this builder
     */
    public Builder callbackMethod(String callbackMethod) {
      this.callbackMethod = callbackMethod;
      return this;
    }

    /**
     * Sets the parameter types for overloaded method matching.
     *
     * @param parameterTypes the list of parameter type names
     * @return this builder
     */
    public Builder parameterTypes(List<String> parameterTypes) {
      this.parameterTypes = parameterTypes != null ? parameterTypes : Collections.emptyList();
      return this;
    }

    /**
     * Sets the interceptable kind (METHOD or FIELD).
     *
     * @param kind the interceptable kind
     * @return this builder
     */
    public Builder kind(InterceptableKind kind) {
      this.kind = kind;
      return this;
    }

    /**
     * Sets the field operation type. Required when kind is FIELD.
     *
     * @param fieldOpType the field operation type
     * @return this builder
     */
    public Builder fieldOpType(FieldOpType fieldOpType) {
      this.fieldOpType = fieldOpType;
      return this;
    }

    /**
     * Sets the peer override.
     *
     * @param peerOverride the peer name or UUID override
     * @return this builder
     */
    public Builder peerOverride(String peerOverride) {
      this.peerOverride = peerOverride;
      return this;
    }

    /**
     * Sets the priority override.
     *
     * @param priorityOverride the priority override
     * @return this builder
     */
    public Builder priorityOverride(Integer priorityOverride) {
      this.priorityOverride = priorityOverride;
      return this;
    }

    /**
     * Sets the TTL override.
     *
     * @param ttlOverride the TTL duration override
     * @return this builder
     */
    public Builder ttlOverride(Duration ttlOverride) {
      this.ttlOverride = ttlOverride;
      return this;
    }

    /**
     * Sets the force-immediate override.
     *
     * @param forceImmediateOverride the force-immediate override
     * @return this builder
     */
    public Builder forceImmediateOverride(Boolean forceImmediateOverride) {
      this.forceImmediateOverride = forceImmediateOverride;
      return this;
    }

    /**
     * Sets the exception policy override.
     *
     * @param exceptionPolicyOverride the exception policy override
     * @return this builder
     */
    public Builder exceptionPolicyOverride(ExceptionPropagationPolicy exceptionPolicyOverride) {
      this.exceptionPolicyOverride = exceptionPolicyOverride;
      return this;
    }

    /**
     * Sets the checked exception policy override.
     *
     * @param checkedExceptionPolicyOverride the checked exception policy override
     * @return this builder
     */
    public Builder checkedExceptionPolicyOverride(
        CheckedExceptionPolicy checkedExceptionPolicyOverride) {
      this.checkedExceptionPolicyOverride = checkedExceptionPolicyOverride;
      return this;
    }

    /**
     * Builds and validates the {@link InterceptSpec}.
     *
     * @return a new immutable {@code InterceptSpec}
     * @throws NullPointerException if any required field is {@code null}
     * @throws IllegalStateException if {@code kind} is FIELD but {@code fieldOpType} is not set
     */
    public InterceptSpec build() {
      Objects.requireNonNull(targetClass, "targetClass must not be null");
      Objects.requireNonNull(targetName, "targetName must not be null");
      Objects.requireNonNull(type, "type must not be null");
      Objects.requireNonNull(callbackClass, "callbackClass must not be null");
      Objects.requireNonNull(callbackMethod, "callbackMethod must not be null");
      if (kind == InterceptableKind.FIELD && fieldOpType == null) {
        throw new IllegalStateException("fieldOpType is required when kind is FIELD");
      }
      return new InterceptSpec(this);
    }
  }
}
