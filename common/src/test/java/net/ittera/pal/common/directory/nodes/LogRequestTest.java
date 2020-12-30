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

public class LogRequestTest {

  private LogRequest logRequest;
  private LogRequest logRequestNoOutputLog;
  private final UUID uuid = UUID.randomUUID();
  private final LogInfo outputLog = new LogInfo("my_log");

  @Before
  public void setUp() {
    logRequest = new LogRequest(uuid, outputLog);
    logRequestNoOutputLog = new LogRequest(uuid);
  }

  @Test
  public void getUuid() {
    assertEquals(uuid, logRequest.getUuid());
  }

  @Test
  public void getOutputLog() {
    assertEquals(outputLog, logRequest.getOutputLog());
  }

  @Test
  public void getOutputLog_null() {
    assertNull(logRequestNoOutputLog.getOutputLog());
  }

  @Test
  public void compareTo() {
    String uuid1 = "863f7bc4-24ab-4e98-8d8a-3a9e4be4a2d1";
    String uuid2 = "963f7bc4-24ab-4e98-8d8a-3a9e4be4a2d1";
    LogRequest first = new LogRequest(UUID.fromString(uuid1));
    LogRequest second = new LogRequest(UUID.fromString(uuid2));
    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(LogRequest.class)
        .usingGetClass()
        .withIgnoredFields("ctime", "mtime")
        .verify();
  }

  @Test
  public void testToString() {

    // set time fields
    long ctime = 22892339L;
    long mtime = 23982349L;
    logRequest.setCtime(ctime);
    logRequestNoOutputLog.setCtime(ctime);
    logRequest.setMtime(mtime);
    logRequestNoOutputLog.setMtime(mtime);

    assertThat(
        logRequest.toString(),
        is(
            String.format(
                "LogRequest {uuid=%s, outputLog=%s, ctime=%s, mtime=%s}",
                uuid,
                outputLog.getName(),
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC))));

    assertThat(
        logRequestNoOutputLog.toString(),
        is(
            String.format(
                "LogRequest {uuid=%s, ctime=%s, mtime=%s}",
                uuid,
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC))));
  }
}
