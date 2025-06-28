/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.directory.nodes;

import static java.lang.String.format;

import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.Interceptable;
import com.quasient.pal.common.lang.intercept.Interceptable.InterceptableType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
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
   * Constructs a new {@code InterceptRequest} with the specified parameters.
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
    this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
    this.peer = Objects.requireNonNull(peer, "peer must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    this.callbackClass = Objects.requireNonNull(callbackClass, "callbackClass must not be null");
    this.callbackMethod = Objects.requireNonNull(callbackMethod, "callbackMethod must not be null");
    this.interceptable = Objects.requireNonNull(interceptable, "interceptable must not be null");
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
    return uuid.equals(that.uuid)
        && peer.equals(that.peer)
        && type == that.type
        && clazz.equals(that.clazz)
        && callbackClass.equals(that.callbackClass)
        && callbackMethod.equals(that.callbackMethod)
        && interceptable.equals(that.interceptable);
  }

  /**
   * Returns a hash code value for this {@code InterceptRequest}.
   *
   * @return a hash code value for this object
   * @see Object#hashCode()
   */
  @Override
  public int hashCode() {
    return Objects.hash(uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable);
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
                + "%s", // 7. interceptable
            uuid,
            peer,
            type.toByte(),
            clazz,
            callbackClass,
            callbackMethod,
            interceptable.getType().toByte(),
            interceptable.toSerializedString());
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
    return new InterceptRequest<>(
        uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable);
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
        + ", ctime="
        + getCTime()
        + ", mtime="
        + getMTime()
        + '}';
  }
}
