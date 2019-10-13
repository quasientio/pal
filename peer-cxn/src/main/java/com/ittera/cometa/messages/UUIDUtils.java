package com.ittera.cometa.messages;

import com.google.common.primitives.Longs;

import java.util.UUID;

public class UUIDUtils {

	public static byte[] toBytes(String uuid) {
		return toBytes(UUID.fromString(uuid));
	}

	public static byte[] toBytes(UUID uuid) {

		byte[] lsbB = Longs.toByteArray(uuid.getLeastSignificantBits());
		byte[] msbB = Longs.toByteArray(uuid.getMostSignificantBits());
		byte[] uuidBytes = new byte[16];
		System.arraycopy(lsbB, 0, uuidBytes, 0, 8);
		System.arraycopy(msbB, 0, uuidBytes, 8, 8);
		return uuidBytes;
	}

	public static UUID fromBytes(byte[] bytes) {
		byte[] lsbB = new byte[8];
		byte[] msbB = new byte[8];
		System.arraycopy(bytes, 0, lsbB, 0, 8);
		System.arraycopy(bytes, 8, msbB, 0, 8);

		return new UUID(Longs.fromByteArray(msbB), Longs.fromByteArray(lsbB));
	}
}
