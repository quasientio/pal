package com.ittera.cometa.messages;

import static org.hamcrest.CoreMatchers.*;

import com.ittera.cometa.common.util.UUIDUtils;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class UUIDUtilsTest {

	@Test
	public void toFromBytes() {

		UUID uuid = UUID.randomUUID();

		// 8 + 8 bytes
		byte[] uuidBytes = UUIDUtils.toBytes(uuid);

		assertThat(uuidBytes.length, is(16));
		assertThat(UUIDUtils.fromBytes(uuidBytes), is(uuid));
	}
}
