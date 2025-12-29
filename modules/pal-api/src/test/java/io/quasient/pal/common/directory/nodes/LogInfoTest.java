/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.directory.nodes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.quasient.pal.common.util.ByteSizeConverter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class LogInfoTest {

  private final String name = "myLog";
  private LogInfo logInfo;

  @Before
  public void setUp() {
    logInfo = new LogInfo(name);
  }

  @Test
  public void logInfo_nameAndBootstrapServers() {
    final String logName = "another_log";
    final String bootstrapServers = "localhost:9092,a.second.host:9092";
    LogInfo myLogInfo = new LogInfo(logName, bootstrapServers);
    assertThat(myLogInfo.getName(), is(logName));
    assertThat(myLogInfo.getBootstrapServers(), is(bootstrapServers));
  }

  @Test
  public void getName() {
    assertEquals(name, logInfo.getName());
  }

  @Test
  public void setBootstrapServers_null() {
    logInfo.setBootstrapServers(null);
    assertThat(logInfo.getBootstrapServers(), is(nullValue()));
  }

  @Test
  public void setBootstrapServers() {
    final String bootstrapServers = "localhost:9092,a.second.host:9092";
    logInfo.setBootstrapServers(bootstrapServers);
    assertThat(logInfo.getBootstrapServers(), is(bootstrapServers));
  }

  @Test
  public void setUuid() {
    UUID uuid = UUID.randomUUID();
    logInfo.setUuid(uuid);
    assertThat(logInfo.getUuid(), is(uuid));
  }

  @Test
  public void setBytes() {
    long bytes = 2048;
    logInfo.setBytes(bytes);
    assertThat(logInfo.getBytes(), is(bytes));
    assertThat(
        logInfo.getHumanReadableByteSize(),
        is(ByteSizeConverter.humanReadableByteCount(bytes, false)));
  }

  @Test
  public void setStartOffset() {
    long offset = 3872;
    logInfo.setStartOffset(offset);
    assertThat(logInfo.getStartOffset(), is(offset));
  }

  @Test
  public void setEndOffset() {
    long offset = 3872;
    logInfo.setEndOffset(offset);
    assertThat(logInfo.getEndOffset(), is(offset));
  }

  @Test
  public void compareTo() {
    LogInfo first = new LogInfo("first");
    LogInfo second = new LogInfo("second");
    LogInfo third = new LogInfo("third");
    assertThat(first.compareTo(second), lessThan(0));
    assertThat(third.compareTo(second), greaterThan(0));
  }

  @Test
  public void equalsContract() {
    LogInfo a = new LogInfo("name", "bs1");
    LogInfo b = new LogInfo("name", "bs1");
    LogInfo c = new LogInfo("name", "bs1");
    LogInfo different = new LogInfo("other", "bs1");

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(different, a);
    assertNotEquals(null, a);
    assertNotEquals(new Object(), a);
  }

  @Test
  public void testToString() {

    // set time fields
    long ctime = 22892339L;
    long mtime = 23982349L;
    logInfo.setCtime(ctime);
    logInfo.setMtime(mtime);

    assertThat(
        logInfo.toString(),
        is(
            "LogInfo{name="
                + "'"
                + logInfo.getName()
                + "'"
                + ", logType="
                + logInfo.getLogType()
                + ", uuid=null"
                + ", bootstrapServers="
                + "'"
                + logInfo.getBootstrapServers()
                + "'"
                + ", startOffset=null"
                + ", endOffset=null"
                + ", ctime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                + ", mtime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                + "}"));
  }
}
