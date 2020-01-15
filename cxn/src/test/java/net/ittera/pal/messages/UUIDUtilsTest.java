package net.ittera.pal.messages;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;
import net.ittera.pal.common.util.UUIDUtils;
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
