/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.directory.nodes;

import static java.lang.String.format;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.Interceptable;
import net.ittera.pal.common.lang.intercept.Interceptable.InterceptableType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates information and provides serialization methods for intercept requests so they can be
 * saved to the registry/directory and processed by peers.
 *
 * @param <T> The type of interceptable of this request.
 */
public final class InterceptRequest<T extends Interceptable> extends InfoNode {

  private static final Logger logger = LoggerFactory.getLogger(InterceptRequest.class);
  private static final String LINE_SEP = "##";
  @Nonnull private final UUID uuid;
  @Nonnull private final UUID peer;
  @Nonnull private final InterceptType type;
  @Nonnull private final String clazz;
  @Nonnull private final String callbackClass;
  @Nonnull private final String callbackMethod;
  @Nonnull private final T interceptable;

  public InterceptRequest(
      @Nonnull UUID uuid,
      @Nonnull UUID peer,
      @Nonnull InterceptType type,
      @Nonnull String clazz,
      @Nonnull String callbackClass,
      @Nonnull String callbackMethod,
      @Nonnull T interceptable) {
    this.uuid = Objects.requireNonNull(uuid);
    this.peer = Objects.requireNonNull(peer);
    this.type = Objects.requireNonNull(type);
    this.clazz = Objects.requireNonNull(clazz);
    this.callbackClass = Objects.requireNonNull(callbackClass);
    this.callbackMethod = Objects.requireNonNull(callbackMethod);
    this.interceptable = Objects.requireNonNull(interceptable);
  }

  @Nonnull
  public UUID getUuid() {
    return uuid;
  }

  @Nonnull
  public T getInterceptable() {
    return interceptable;
  }

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

  @Override
  public int hashCode() {
    return Objects.hash(uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable);
  }

  public byte[] toBytes(Charset charset) {
    final String s =
        format(
            "%s" + LINE_SEP + // 0. uuid
                "%s" + LINE_SEP + // 1. peer
                "%d" + LINE_SEP + // 2. type
                "%s" + LINE_SEP + // 3. clazz
                "%s" + LINE_SEP + // 4. callbackClass
                "%s" + LINE_SEP + // 5. callbackMethod
                "%d" + LINE_SEP + // 6. interceptableType
                "%s", // 7. interceptable
            uuid,
            peer,
            type.ordinal(),
            clazz,
            callbackClass,
            callbackMethod,
            interceptable.getType().ordinal(),
            interceptable.toSerializedString());
    return s.getBytes(charset);
  }

  @SuppressWarnings("rawtypes")
  public static InterceptRequest fromBytes(byte[] serialized, Charset charset) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Deserializing intercept request from bytes (len={}): {}",
          serialized.length,
          new String(serialized, charset));
    }
    final String[] parts = new String(serialized, charset).split(LINE_SEP);
    final UUID uuid = UUID.fromString(parts[0]);
    final UUID peer = UUID.fromString(parts[1]);
    final InterceptType type = InterceptType.values()[Integer.parseInt(parts[2])];
    final String clazz = parts[3];
    final String callbackClass = parts[4];
    final String callbackMethod = parts[5];
    final InterceptableType interceptableType =
        InterceptableType.values()[Integer.parseInt(parts[6])];
    final Interceptable interceptable;
    switch (interceptableType) {
      case METHOD_CALL:
        interceptable = InterceptableMethodCall.fromSerializedString(parts[7]);
        break;
      case FIELD_OP:
        interceptable = InterceptableFieldOp.fromSerializedString(parts[7]);
        break;
      default:
        throw new IllegalArgumentException("Unsupported interceptable type: " + interceptableType);
    }
    return new InterceptRequest<>(
        uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable);
  }

  @Nonnull
  public UUID getPeer() {
    return peer;
  }

  @Nonnull
  public InterceptType getType() {
    return type;
  }

  @Nonnull
  public String getClazz() {
    return clazz;
  }

  @Nonnull
  public String getCallbackClass() {
    return callbackClass;
  }

  @Nonnull
  public String getCallbackMethod() {
    return callbackMethod;
  }

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
