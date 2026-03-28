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
package io.quasient.pal.common.directory.nodes;

import static java.lang.String.format;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.common.lang.intercept.Interceptable.InterceptableType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a request to intercept a specific call or field op. It encapsulates all necessary
 * information required to perform the interception, including the type of intercept, the target
 * class and method, and the specific interceptable action. Requests are serialized in the Pal
 * directory for later processing by this or any peer in the system.
 *
 * @param <T> The type of {@link Interceptable} associated with this request.
 * @see Interceptable
 * @see InterceptableMethodCall
 * @see InterceptableFieldOp
 */
public final class InterceptRequest<T extends Interceptable> extends InfoNode {

  /** Logger instance for logging events related to {@code InterceptRequest}. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequest.class);

  /** Delimiter used to separate fields during serialization. */
  private static final String LINE_SEP = "##";

  /** Unique identifier for this intercept request. */
  @Nonnull private final UUID uuid;

  /** Identifier of the peer that initiated this intercept request. */
  @Nonnull private final UUID peer;

  /** The type of interception being requested. */
  @Nonnull private final InterceptType type;

  /** The fully qualified name of the class where the interception is to occur. */
  @Nonnull private final String clazz;

  /** The fully qualified name of the callback class associated with this interception. */
  @Nonnull private final String callbackClass;

  /** The method name in the callback class that should be invoked upon interception. */
  @Nonnull private final String callbackMethod;

  /** The interceptable action that this request targets. */
  @Nonnull private final T interceptable;

  /**
   * Whether to force immediate application of this intercept without waiting for in-flight calls to
   * complete. This field overrides the peer's global {@code WITH_IN_FLIGHT_TRACKING} configuration
   * for this specific intercept.
   *
   * <p>When {@code true}, the intercept is applied immediately, even if matching method calls are
   * currently in-flight. This is useful for hot-patching methods that are stuck in loops,
   * recursion, or hanging on external resources where waiting for completion would never succeed.
   *
   * <p>When {@code false}, the intercept waits for in-flight calls to complete before activation
   * (if the peer has {@code WITH_IN_FLIGHT_TRACKING} enabled).
   *
   * <p><strong>Example use case:</strong> A method is hanging on an external API call. Setting
   * {@code forceImmediate=true} allows the intercept to redirect the method to a fallback
   * implementation immediately, without waiting for the hanging call to complete.
   *
   * @see io.quasient.pal.core.options.RunOptions#WITH_IN_FLIGHT_TRACKING
   */
  private final boolean forceImmediate;

  /**
   * Exception propagation policy for this intercept. Determines how exceptions thrown by the
   * intercept callback are handled.
   *
   * <p>When {@code null}, the policy defers to the global peer configuration. When non-null, this
   * policy overrides the global configuration for this specific intercept.
   *
   * @see ExceptionPropagationPolicy
   */
  @Nullable private final ExceptionPropagationPolicy exceptionPropagationPolicy;

  /**
   * Checked exception policy for this intercept. Determines how checked exceptions set by the
   * callback are validated against the intercepted method's declared exceptions.
   *
   * <p>When {@code null}, the policy defers to the global peer configuration. When non-null, this
   * policy overrides the global configuration for this specific intercept.
   *
   * @see CheckedExceptionPolicy
   */
  @Nullable private final CheckedExceptionPolicy checkedExceptionPolicy;

  /** Execution priority within local/remote group. Lower values execute first. Default 0. */
  private final int priority;

  /** TTL in seconds for this intercept. 0 = no dedicated TTL. */
  private final long ttlSeconds;

  /**
   * Callback timeout in milliseconds for this intercept. Controls how long the intercepted peer
   * waits for the callback peer to respond to a synchronous BEFORE/AFTER callback.
   *
   * <p>When {@code null}, the timeout defers to the global peer configuration (set via {@code
   * --callback-timeout-ms} on the intercepted peer). When {@code 0}, no timeout is applied
   * (infinite wait). When positive, overrides the global timeout for this specific intercept.
   */
  @Nullable private final Long callbackTimeoutMs;

  /**
   * Constructs a new {@code InterceptRequest} with all parameters including callback timeout.
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @param forceImmediate whether to force immediate application without waiting for in-flight
   *     calls
   * @param exceptionPropagationPolicy exception propagation policy for this intercept, or {@code
   *     null} to defer to global configuration
   * @param checkedExceptionPolicy checked exception policy for this intercept, or {@code null} to
   *     defer to global configuration
   * @param priority execution priority within local/remote group; lower values execute first
   * @param ttlSeconds TTL in seconds for this intercept; 0 means no dedicated TTL
   * @param callbackTimeoutMs callback timeout in milliseconds, or {@code null} to defer to global
   * @throws NullPointerException if any of the required object parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable,
      boolean forceImmediate,
      @Nullable ExceptionPropagationPolicy exceptionPropagationPolicy,
      @Nullable CheckedExceptionPolicy checkedExceptionPolicy,
      int priority,
      long ttlSeconds,
      @Nullable Long callbackTimeoutMs) {
    this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
    this.peer = Objects.requireNonNull(peer, "peer must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    this.callbackClass = Objects.requireNonNull(callbackClass, "callbackClass must not be null");
    this.callbackMethod = Objects.requireNonNull(callbackMethod, "callbackMethod must not be null");
    this.interceptable = Objects.requireNonNull(interceptable, "interceptable must not be null");
    this.forceImmediate = forceImmediate;
    this.exceptionPropagationPolicy = exceptionPropagationPolicy;
    this.checkedExceptionPolicy = checkedExceptionPolicy;
    this.priority = priority;
    this.ttlSeconds = ttlSeconds;
    this.callbackTimeoutMs = callbackTimeoutMs;
  }

  /**
   * Constructs a new {@code InterceptRequest} with all parameters except callback timeout.
   *
   * <p>This constructor defaults {@code callbackTimeoutMs} to {@code null} (defer to global).
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @param forceImmediate whether to force immediate application without waiting for in-flight
   *     calls
   * @param exceptionPropagationPolicy exception propagation policy for this intercept, or {@code
   *     null} to defer to global configuration
   * @param checkedExceptionPolicy checked exception policy for this intercept, or {@code null} to
   *     defer to global configuration
   * @param priority execution priority within local/remote group; lower values execute first
   * @param ttlSeconds TTL in seconds for this intercept; 0 means no dedicated TTL
   * @throws NullPointerException if any of the required object parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable,
      boolean forceImmediate,
      @Nullable ExceptionPropagationPolicy exceptionPropagationPolicy,
      @Nullable CheckedExceptionPolicy checkedExceptionPolicy,
      int priority,
      long ttlSeconds) {
    this(
        uuid,
        peer,
        type,
        clazz,
        callbackClass,
        callbackMethod,
        interceptable,
        forceImmediate,
        exceptionPropagationPolicy,
        checkedExceptionPolicy,
        priority,
        ttlSeconds,
        null);
  }

  /**
   * Constructs a new {@code InterceptRequest} with all parameters including priority and default
   * TTL of 0.
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @param forceImmediate whether to force immediate application without waiting for in-flight
   *     calls
   * @param exceptionPropagationPolicy exception propagation policy for this intercept, or {@code
   *     null} to defer to global configuration
   * @param checkedExceptionPolicy checked exception policy for this intercept, or {@code null} to
   *     defer to global configuration
   * @param priority execution priority within local/remote group; lower values execute first
   * @throws NullPointerException if any of the required object parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable,
      boolean forceImmediate,
      @Nullable ExceptionPropagationPolicy exceptionPropagationPolicy,
      @Nullable CheckedExceptionPolicy checkedExceptionPolicy,
      int priority) {
    this(
        uuid,
        peer,
        type,
        clazz,
        callbackClass,
        callbackMethod,
        interceptable,
        forceImmediate,
        exceptionPropagationPolicy,
        checkedExceptionPolicy,
        priority,
        0,
        null);
  }

  /**
   * Constructs a new {@code InterceptRequest} with the specified parameters and default priority of
   * 0.
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @param forceImmediate whether to force immediate application without waiting for in-flight
   *     calls
   * @param exceptionPropagationPolicy exception propagation policy for this intercept, or {@code
   *     null} to defer to global configuration
   * @param checkedExceptionPolicy checked exception policy for this intercept, or {@code null} to
   *     defer to global configuration
   * @throws NullPointerException if any of the required object parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable,
      boolean forceImmediate,
      @Nullable ExceptionPropagationPolicy exceptionPropagationPolicy,
      @Nullable CheckedExceptionPolicy checkedExceptionPolicy) {
    this(
        uuid,
        peer,
        type,
        clazz,
        callbackClass,
        callbackMethod,
        interceptable,
        forceImmediate,
        exceptionPropagationPolicy,
        checkedExceptionPolicy,
        0);
  }

  /**
   * Constructs a new {@code InterceptRequest} with the specified parameters. This is a convenience
   * constructor that defaults {@code forceImmediate} to {@code false} and both policies to {@code
   * null}.
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @throws NullPointerException if any of the parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable) {
    this(uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable, false, null, null);
  }

  /**
   * Constructs a new {@code InterceptRequest} with the specified parameters. This is a convenience
   * constructor that defaults both policies to {@code null}.
   *
   * @param uuid the unique identifier for this request; must not be {@code null}
   * @param peer the identifier of the peer initiating the request; must not be {@code null}
   * @param type the type of interception; must not be {@code null}
   * @param clazz the target class name for interception; must not be {@code null}
   * @param callbackClass the callback class name; must not be {@code null}
   * @param callbackMethod the callback method name; must not be {@code null}
   * @param interceptable the interceptable action; must not be {@code null}
   * @param forceImmediate whether to force immediate application without waiting for in-flight
   *     calls
   * @throws NullPointerException if any of the parameters are {@code null}
   */
  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable,
      boolean forceImmediate) {
    this(
        uuid,
        peer,
        type,
        clazz,
        callbackClass,
        callbackMethod,
        interceptable,
        forceImmediate,
        null,
        null);
  }

  /**
   * Retrieves the unique identifier of this intercept request.
   *
   * @return the {@code UUID} representing this request's unique identifier
   */
  @Nonnull
  public UUID getUuid() {
    return uuid;
  }

  /**
   * Retrieves the interceptable action associated with this request.
   *
   * @return the {@code Interceptable} action targeted by this request
   */
  @Nonnull
  public T getInterceptable() {
    return interceptable;
  }

  /**
   * Retrieves the identifier of the peer that initiated this intercept request.
   *
   * @return the {@code UUID} of the initiating peer
   */
  @Nonnull
  public UUID getPeer() {
    return peer;
  }

  /**
   * Retrieves the type of interception being requested.
   *
   * @return the {@code InterceptType} of this request
   */
  @Nonnull
  public InterceptType getType() {
    return type;
  }

  /**
   * Retrieves the name of the class where the interception is to occur.
   *
   * @return the fully qualified class name as a {@code String}
   */
  @Nonnull
  public String getClazz() {
    return clazz;
  }

  /**
   * Retrieves the name of the callback class associated with this interception.
   *
   * @return the fully qualified callback class name as a {@code String}
   */
  @Nonnull
  public String getCallbackClass() {
    return callbackClass;
  }

  /**
   * Retrieves the name of the callback method to be invoked upon interception.
   *
   * @return the callback method name as a {@code String}
   */
  @Nonnull
  public String getCallbackMethod() {
    return callbackMethod;
  }

  /**
   * Indicates whether this intercept should be applied immediately without waiting for in-flight
   * calls to complete.
   *
   * <p>When {@code true}, this intercept overrides the peer's global {@code
   * WITH_IN_FLIGHT_TRACKING} configuration and applies immediately. This is useful for hot-patching
   * methods that are stuck in loops, recursion, or hanging on external resources.
   *
   * @return {@code true} if the intercept should be applied immediately, {@code false} to wait for
   *     quiescence
   */
  public boolean isForceImmediate() {
    return forceImmediate;
  }

  /**
   * Retrieves the exception propagation policy for this intercept.
   *
   * <p>When {@code null}, the policy defers to the global peer configuration.
   *
   * @return the exception propagation policy, or {@code null} to defer to global configuration
   */
  @Nullable
  public ExceptionPropagationPolicy getExceptionPropagationPolicy() {
    return exceptionPropagationPolicy;
  }

  /**
   * Retrieves the checked exception policy for this intercept.
   *
   * <p>When {@code null}, the policy defers to the global peer configuration.
   *
   * @return the checked exception policy, or {@code null} to defer to global configuration
   */
  @Nullable
  public CheckedExceptionPolicy getCheckedExceptionPolicy() {
    return checkedExceptionPolicy;
  }

  /**
   * Retrieves the execution priority for this intercept. Lower values execute first within
   * local/remote groups.
   *
   * @return the priority value; default is 0
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Retrieves the TTL in seconds for this intercept.
   *
   * @return the TTL in seconds; 0 means no dedicated TTL
   */
  public long getTtlSeconds() {
    return ttlSeconds;
  }

  /**
   * Retrieves the callback timeout in milliseconds for this intercept.
   *
   * <p>When {@code null}, defers to the global peer configuration. When {@code 0}, no timeout is
   * applied (infinite wait). When positive, overrides the global timeout.
   *
   * @return the callback timeout in milliseconds, or {@code null} to defer to global configuration
   */
  @Nullable
  public Long getCallbackTimeoutMs() {
    return callbackTimeoutMs;
  }

  /**
   * Compares this {@code InterceptRequest} to the specified object.
   *
   * @param o the object to compare this {@code InterceptRequest} against
   * @return {@code true} if the given object represents an {@code InterceptRequest} equivalent to
   *     this request, {@code false} otherwise
   * @see Object#equals(Object)
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptRequest<?> that = (InterceptRequest<?>) o;
    return forceImmediate == that.forceImmediate
        && priority == that.priority
        && ttlSeconds == that.ttlSeconds
        && uuid.equals(that.uuid)
        && peer.equals(that.peer)
        && type == that.type
        && clazz.equals(that.clazz)
        && callbackClass.equals(that.callbackClass)
        && callbackMethod.equals(that.callbackMethod)
        && interceptable.equals(that.interceptable)
        && exceptionPropagationPolicy == that.exceptionPropagationPolicy
        && checkedExceptionPolicy == that.checkedExceptionPolicy
        && Objects.equals(callbackTimeoutMs, that.callbackTimeoutMs);
  }

  /**
   * Returns a hash code value for this {@code InterceptRequest}.
   *
   * @return a hash code value for this object
   * @see Object#hashCode()
   */
  @Override
  public int hashCode() {
    return Objects.hash(
        uuid,
        peer,
        type,
        clazz,
        callbackClass,
        callbackMethod,
        interceptable,
        forceImmediate,
        exceptionPropagationPolicy,
        checkedExceptionPolicy,
        priority,
        ttlSeconds,
        callbackTimeoutMs);
  }

  /**
   * Serializes this {@code InterceptRequest} into a byte array using the specified {@code Charset}.
   * The serialization format separates fields using the predefined line separator.
   *
   * @param charset the {@code Charset} to use for encoding the serialized string
   * @return a byte array representing the serialized form of this request
   * @throws NullPointerException if {@code charset} is {@code null}
   */
  public byte[] toBytes(Charset charset) {
    final String s =
        format(
            "%s"
                + LINE_SEP // 0. uuid
                + "%s"
                + LINE_SEP // 1. peer
                + "%d"
                + LINE_SEP // 2. type
                + "%s"
                + LINE_SEP // 3. clazz
                + "%s"
                + LINE_SEP // 4. callbackClass
                + "%s"
                + LINE_SEP // 5. callbackMethod
                + "%d"
                + LINE_SEP // 6. interceptableType
                + "%s"
                + LINE_SEP // 7. interceptable
                + "%b"
                + LINE_SEP // 8. forceImmediate
                + "%s"
                + LINE_SEP // 9. exceptionPropagationPolicy
                + "%s"
                + LINE_SEP // 10. checkedExceptionPolicy
                + "%s"
                + LINE_SEP // 11. ctime (epoch millis)
                + "%s"
                + LINE_SEP // 12. mtime (epoch millis)
                + "%d"
                + LINE_SEP // 13. priority
                + "%d"
                + LINE_SEP // 14. ttlSeconds
                + "%s", // 15. callbackTimeoutMs
            uuid,
            peer,
            type.toByte(),
            clazz,
            callbackClass,
            callbackMethod,
            interceptable.getType().toByte(),
            interceptable.toSerializedString(),
            forceImmediate,
            exceptionPropagationPolicy != null ? exceptionPropagationPolicy.name() : "null",
            checkedExceptionPolicy != null ? checkedExceptionPolicy.name() : "null",
            getCtimeMillis() != null ? getCtimeMillis() : "null",
            getMtimeMillis() != null ? getMtimeMillis() : "null",
            priority,
            ttlSeconds,
            callbackTimeoutMs != null ? callbackTimeoutMs : "null");
    return s.getBytes(charset);
  }

  /**
   * Deserializes a byte array into an {@code InterceptRequest} object using the specified {@code
   * Charset}. This method parses the byte array based on the predefined line separator and
   * reconstructs the {@code InterceptRequest} with the extracted information.
   *
   * @param serialized the byte array containing the serialized {@code InterceptRequest}
   * @param charset the {@code Charset} to use for decoding the byte array
   * @return a new instance of {@code InterceptRequest} reconstructed from the byte array
   * @throws NullPointerException if {@code serialized} or {@code charset} is {@code null}
   */
  public static InterceptRequest<?> fromBytes(byte[] serialized, Charset charset) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Deserializing intercept request from bytes (len={}): {}",
          serialized.length,
          new String(serialized, charset));
    }
    @SuppressWarnings("StringSplitter")
    final String[] parts = new String(serialized, charset).split(LINE_SEP);
    final UUID uuid = UUID.fromString(parts[0]);
    final UUID peer = UUID.fromString(parts[1]);
    final InterceptType type = InterceptType.fromByte(Byte.parseByte(parts[2]));
    final String clazz = parts[3];
    final String callbackClass = parts[4];
    final String callbackMethod = parts[5];
    final InterceptableType interceptableType =
        InterceptableType.fromByte(Byte.parseByte(parts[6]));
    final Interceptable interceptable =
        switch (interceptableType) {
          case METHOD_CALL -> InterceptableMethodCall.fromSerializedString(parts[7]);
          case FIELD_OP -> InterceptableFieldOp.fromSerializedString(parts[7]);
        };
    final boolean forceImmediate = Boolean.parseBoolean(parts[8]);
    final ExceptionPropagationPolicy exceptionPropagationPolicy =
        parts[9].equals("null") ? null : ExceptionPropagationPolicy.valueOf(parts[9]);
    final CheckedExceptionPolicy checkedExceptionPolicy =
        parts[10].equals("null") ? null : CheckedExceptionPolicy.valueOf(parts[10]);

    int priority = Integer.parseInt(parts[13]);
    long ttlSeconds = Long.parseLong(parts[14]);
    Long callbackTimeoutMs = parts[15].equals("null") ? null : Long.parseLong(parts[15]);

    InterceptRequest<?> request =
        new InterceptRequest<>(
            uuid,
            peer,
            type,
            clazz,
            callbackClass,
            callbackMethod,
            interceptable,
            forceImmediate,
            exceptionPropagationPolicy,
            checkedExceptionPolicy,
            priority,
            ttlSeconds,
            callbackTimeoutMs);

    // ctime/mtime fields added after the original 11 fields
    if (parts.length > 11 && !parts[11].equals("null")) {
      request.setCtime(Long.parseLong(parts[11]));
    }
    if (parts.length > 12 && !parts[12].equals("null")) {
      request.setMtime(Long.parseLong(parts[12]));
    }

    return request;
  }

  /**
   * Returns a string representation of this {@code InterceptRequest}, including all its fields.
   *
   * @return a string representation of this intercept request
   */
  @Override
  public String toString() {
    return "InterceptRequest {"
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
        + interceptable
        + ", callbackClass='"
        + callbackClass
        + '\''
        + ", callbackMethod='"
        + callbackMethod
        + '\''
        + ", forceImmediate="
        + forceImmediate
        + ", priority="
        + priority
        + ", ttlSeconds="
        + ttlSeconds
        + ", callbackTimeoutMs="
        + callbackTimeoutMs
        + ", ctime="
        + getCTime()
        + ", mtime="
        + getMTime()
        + '}';
  }

  /**
   * Creates a new {@link Builder} for constructing {@code InterceptRequest} instances.
   *
   * @param <T> the type of {@link Interceptable} for the request
   * @return a new builder instance
   */
  public static <T extends Interceptable> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * A builder for constructing {@link InterceptRequest} instances.
   *
   * <p>Required fields must be set before calling {@link #build()}:
   *
   * <ul>
   *   <li>{@link #uuid(UUID)}
   *   <li>{@link #peer(UUID)}
   *   <li>{@link #type(InterceptType)}
   *   <li>{@link #clazz(String)}
   *   <li>{@link #callbackClass(String)}
   *   <li>{@link #callbackMethod(String)}
   *   <li>{@link #interceptable(Interceptable)}
   * </ul>
   *
   * <p>Optional fields have sensible defaults:
   *
   * <ul>
   *   <li>{@code forceImmediate} = {@code false}
   *   <li>{@code priority} = {@code 0}
   *   <li>{@code ttlSeconds} = {@code 0}
   *   <li>{@code callbackTimeoutMs} = {@code null} (defer to global)
   *   <li>{@code exceptionPropagationPolicy} = {@code null} (defer to global)
   *   <li>{@code checkedExceptionPolicy} = {@code null} (defer to global)
   * </ul>
   *
   * @param <T> the type of {@link Interceptable} for the request
   */
  public static final class Builder<T extends Interceptable> {

    /** Unique identifier for this intercept request. */
    private UUID uuid;

    /** Identifier of the peer that initiated this intercept request. */
    private UUID peer;

    /** The type of interception being requested. */
    private InterceptType type;

    /** The fully qualified name of the class where the interception is to occur. */
    private String clazz;

    /** The fully qualified name of the callback class. */
    private String callbackClass;

    /** The callback method name. */
    private String callbackMethod;

    /** The interceptable action that this request targets. */
    private T interceptable;

    /** Whether to force immediate application. Default: {@code false}. */
    private boolean forceImmediate;

    /** Exception propagation policy. Default: {@code null} (defer to global). */
    @Nullable private ExceptionPropagationPolicy exceptionPropagationPolicy;

    /** Checked exception policy. Default: {@code null} (defer to global). */
    @Nullable private CheckedExceptionPolicy checkedExceptionPolicy;

    /** Execution priority. Default: {@code 0}. */
    private int priority;

    /** TTL in seconds. Default: {@code 0} (no dedicated TTL). */
    private long ttlSeconds;

    /** Callback timeout in milliseconds. Default: {@code null} (defer to global). */
    @Nullable private Long callbackTimeoutMs;

    /** Creates an empty builder. Use {@link InterceptRequest#builder()} instead. */
    private Builder() {}

    /**
     * Sets the unique identifier for this intercept request.
     *
     * @param uuid the UUID; must not be {@code null}
     * @return this builder
     */
    public Builder<T> uuid(@Nonnull UUID uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * Sets the peer identifier.
     *
     * @param peer the peer UUID; must not be {@code null}
     * @return this builder
     */
    public Builder<T> peer(@Nonnull UUID peer) {
      this.peer = peer;
      return this;
    }

    /**
     * Sets the intercept type.
     *
     * @param type the intercept type; must not be {@code null}
     * @return this builder
     */
    public Builder<T> type(@Nonnull InterceptType type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the target class name.
     *
     * @param clazz the fully qualified class name; must not be {@code null}
     * @return this builder
     */
    public Builder<T> clazz(@Nonnull String clazz) {
      this.clazz = clazz;
      return this;
    }

    /**
     * Sets the callback class name.
     *
     * @param callbackClass the fully qualified callback class name; must not be {@code null}
     * @return this builder
     */
    public Builder<T> callbackClass(@Nonnull String callbackClass) {
      this.callbackClass = callbackClass;
      return this;
    }

    /**
     * Sets the callback method name.
     *
     * @param callbackMethod the callback method name; must not be {@code null}
     * @return this builder
     */
    public Builder<T> callbackMethod(@Nonnull String callbackMethod) {
      this.callbackMethod = callbackMethod;
      return this;
    }

    /**
     * Sets the interceptable action.
     *
     * @param interceptable the interceptable action; must not be {@code null}
     * @return this builder
     */
    public Builder<T> interceptable(@Nonnull T interceptable) {
      this.interceptable = interceptable;
      return this;
    }

    /**
     * Sets whether to force immediate application without waiting for in-flight calls.
     *
     * @param forceImmediate {@code true} to apply immediately
     * @return this builder
     */
    public Builder<T> forceImmediate(boolean forceImmediate) {
      this.forceImmediate = forceImmediate;
      return this;
    }

    /**
     * Sets the exception propagation policy for this intercept.
     *
     * @param policy the policy, or {@code null} to defer to global configuration
     * @return this builder
     */
    public Builder<T> exceptionPropagationPolicy(@Nullable ExceptionPropagationPolicy policy) {
      this.exceptionPropagationPolicy = policy;
      return this;
    }

    /**
     * Sets the checked exception policy for this intercept.
     *
     * @param policy the policy, or {@code null} to defer to global configuration
     * @return this builder
     */
    public Builder<T> checkedExceptionPolicy(@Nullable CheckedExceptionPolicy policy) {
      this.checkedExceptionPolicy = policy;
      return this;
    }

    /**
     * Sets the execution priority. Lower values execute first.
     *
     * @param priority the priority value
     * @return this builder
     */
    public Builder<T> priority(int priority) {
      this.priority = priority;
      return this;
    }

    /**
     * Sets the TTL in seconds. {@code 0} means no dedicated TTL.
     *
     * @param ttlSeconds the TTL in seconds
     * @return this builder
     */
    public Builder<T> ttlSeconds(long ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
      return this;
    }

    /**
     * Sets the callback timeout in milliseconds.
     *
     * <p>{@code null} defers to the global peer configuration. {@code 0} means no timeout (infinite
     * wait). Positive values override the global timeout.
     *
     * @param callbackTimeoutMs the timeout in milliseconds, or {@code null} to defer to global
     * @return this builder
     */
    public Builder<T> callbackTimeoutMs(@Nullable Long callbackTimeoutMs) {
      this.callbackTimeoutMs = callbackTimeoutMs;
      return this;
    }

    /**
     * Builds the {@code InterceptRequest} from this builder's state.
     *
     * @return a new {@code InterceptRequest} instance
     * @throws NullPointerException if any required field is {@code null}
     */
    public InterceptRequest<T> build() {
      return new InterceptRequest<>(
          uuid,
          peer,
          type,
          clazz,
          callbackClass,
          callbackMethod,
          interceptable,
          forceImmediate,
          exceptionPropagationPolicy,
          checkedExceptionPolicy,
          priority,
          ttlSeconds,
          callbackTimeoutMs);
    }
  }
}
