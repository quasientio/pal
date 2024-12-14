package net.ittera.pal.messages.types;

public enum JsonRpcRequestType {
  CONSTRUCTOR((byte) 1),
  INSTANCE_METHOD((byte) 2),
  CLASS_METHOD((byte) 3),
  GET_STATIC((byte) 4),
  GET_FIELD((byte) 5),
  PUT_STATIC((byte) 6),
  PUT_FIELD((byte) 7),
  PUT_STATIC_DONE((byte) 8),
  PUT_FIELD_DONE((byte) 9),
  THROWABLE((byte) 10),
  RETURN_VALUE((byte) 11);

  private final byte idx;

  JsonRpcRequestType(byte idx) {
    this.idx = idx;
  }

  public static JsonRpcRequestType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> CONSTRUCTOR;
      case 2 -> INSTANCE_METHOD;
      case 3 -> CLASS_METHOD;
      case 4 -> GET_STATIC;
      case 5 -> GET_FIELD;
      case 6 -> PUT_STATIC;
      case 7 -> PUT_FIELD;
      case 8 -> PUT_STATIC_DONE;
      case 9 -> PUT_FIELD_DONE;
      case 10 -> THROWABLE;
      case 11 -> RETURN_VALUE;
      default -> throw new IllegalArgumentException("Unknown JSON-RPC request type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
