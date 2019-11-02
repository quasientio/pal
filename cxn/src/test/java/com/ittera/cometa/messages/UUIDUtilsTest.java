package com.ittera.cometa.messages;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.common.util.UUIDUtils;
import java.util.UUID;
import org.junit.Test;

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
