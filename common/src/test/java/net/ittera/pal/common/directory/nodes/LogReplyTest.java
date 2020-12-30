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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class LogReplyTest {

  private final UUID uuid = UUID.randomUUID();
  private final UUID peerUuid = UUID.randomUUID();
  private final UUID isReplyTo = UUID.randomUUID();
  private final long offset = 13;
  private LogReply logReply;

  @Before
  public void setUp() {
    logReply = new LogReply(uuid, peerUuid, isReplyTo, offset);
  }

  @Test
  public void getUuid() {
    assertEquals(uuid, logReply.getUuid());
  }

  @Test
  public void getOffset() {
    assertEquals(offset, logReply.getOffset());
  }

  @Test
  public void getIsReplyTo() {
    assertEquals(isReplyTo, logReply.getIsReplyTo());
  }

  @Test
  public void getPeerUuid() {
    assertEquals(peerUuid, logReply.getPeerUuid());
  }

  @Test
  public void compareTo() {
    LogReply first = new LogReply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10001);
    LogReply second = new LogReply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10002);
    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(LogReply.class)
        .usingGetClass()
        .withIgnoredFields("ctime", "mtime")
        .verify();
  }

  @Test
  public void testToString() {

    // set time fields
    long ctime = 22892339L;
    long mtime = 23982349L;
    logReply.setCtime(ctime);
    logReply.setMtime(mtime);

    assertThat(
        logReply.toString(),
        is(
            "LogReply {uuid="
                + uuid
                + ", offset="
                + offset
                + ", from-peer="
                + peerUuid
                + ", isReplyTo="
                + isReplyTo
                + ", ctime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                + ", mtime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                + '}'));
  }
}
