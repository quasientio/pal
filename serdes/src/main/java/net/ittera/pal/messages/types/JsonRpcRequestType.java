package net.ittera.pal.messages.types;

public enum JsonRpcRequestType {
  REQUEST((byte) 1),
  NOTIFICATION((byte) 2);

  private final byte idx;

  JsonRpcRequestType(byte idx) {
    this.idx = idx;
  }

  public static JsonRpcRequestType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> REQUEST;
      case 2 -> NOTIFICATION;
      default -> throw new IllegalArgumentException("Unknown JSON-RPC request type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
