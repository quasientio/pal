package net.ittera.pal.common.znodes;

import static java.lang.String.format;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.UUID;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.Interceptable;
import net.ittera.pal.common.lang.intercept.Interceptable.InterceptableType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;

public class InterceptRequest<T extends Interceptable> extends UTCTimestampedInfo {

  private static final String LINE_SEP = "##";
  private final UUID uuid;
  private final UUID peer;
  private final InterceptType type;
  private final String clazz;
  private final String callbackClass;
  private final String callbackMethod;
  private final T interceptable;

  public InterceptRequest(
      UUID uuid,
      UUID peer,
      InterceptType type,
      String clazz,
      String callbackClass,
      String callbackMethod,
      T interceptable) {
    this.uuid = uuid;
    this.peer = peer;
    this.type = type;
    this.clazz = clazz;
    this.callbackClass = callbackClass;
    this.callbackMethod = callbackMethod;
    this.interceptable = interceptable;
  }

  public UUID getUuid() {
    return uuid;
  }

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
        && interceptable.equals(that.interceptable)
        && callbackClass.equals(that.callbackClass)
        && callbackMethod.equals(that.callbackMethod);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, peer, type, clazz, interceptable, callbackClass, callbackMethod);
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

  public static InterceptRequest fromBytes(byte[] serialized, Charset charset) {
    final String[] parts = new String(serialized, charset).split(LINE_SEP);
    final UUID uuid = UUID.fromString(parts[0]);
    final UUID peer = UUID.fromString(parts[1]);
    final InterceptType type = InterceptType.values[Integer.parseInt(parts[2])];
    final String clazz = parts[3];
    final String callbackClass = parts[4];
    final String callbackMethod = parts[5];
    final InterceptableType interceptableType =
        InterceptableType.values[Integer.parseInt(parts[6])];
    final Interceptable interceptable;
    switch (interceptableType) {
      case METHOD_CALL:
        interceptable = InterceptableMethodCall.fromSerializedString(parts[7]);
        break;
      case FIELD_OP:
        interceptable = InterceptableFieldOp.fromSerializedString(parts[7]);
        break;
      default:
        interceptable = null;
    }
    return new InterceptRequest<>(
        uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable);
  }

  public UUID getPeer() {
    return peer;
  }

  public InterceptType getType() {
    return type;
  }

  public String getClazz() {
    return clazz;
  }

  public String getCallbackClass() {
    return callbackClass;
  }

  public String getCallbackMethod() {
    return callbackMethod;
  }

  @Override
  public String toString() {
    return "InterceptRequest{"
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
        + '}';
  }
}
