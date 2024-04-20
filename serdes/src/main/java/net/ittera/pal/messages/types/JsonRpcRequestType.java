package net.ittera.pal.messages.types;

public enum JsonRpcRequestType {
  REQUEST,
  NOTIFICATION;

  public static JsonRpcRequestType fromByte(byte typeAsByte) {
    return JsonRpcRequestType.values()[typeAsByte - 1];
  }

  public byte toByte() {
    return (byte) (this.ordinal() + 1);
  }
}
