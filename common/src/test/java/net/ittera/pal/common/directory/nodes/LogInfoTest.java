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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ittera.pal.common.directory.kafka.KafkaBrokerEndpoint;
import net.ittera.pal.common.directory.kafka.KafkaBrokerInfo;
import net.ittera.pal.common.util.ByteSizeConverter;
import nl.jqno.equalsverifier.EqualsVerifier;
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
  public void logInfo_nameAndBrokers() {
    String logName = "another_log";
    KafkaBrokerEndpoint endpoint1 = new KafkaBrokerEndpoint("plain://", "kafka_host:6091");
    KafkaBrokerEndpoint endpoint2 = new KafkaBrokerEndpoint("ssl://", "kafka_host:9073");
    LogInfo myLogInfo = new LogInfo(logName, createKafkaBrokerInfoSet(endpoint1, endpoint2));
    verifyBootstrapServersForEndpoints(myLogInfo, endpoint1, endpoint2);
    assertThat(myLogInfo.getName(), is(logName));
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
  public void logInfo_nameBrokersAndUuid() {
    String logName = "another_log";
    KafkaBrokerEndpoint endpoint1 = new KafkaBrokerEndpoint("plain://", "kafka_host:6091");
    KafkaBrokerEndpoint endpoint2 = new KafkaBrokerEndpoint("ssl://", "kafka_host:9073");
    UUID uuid = UUID.randomUUID();
    LogInfo myLogInfo = new LogInfo(logName, createKafkaBrokerInfoSet(endpoint1, endpoint2), uuid);
    verifyBootstrapServersForEndpoints(myLogInfo, endpoint1, endpoint2);
    assertThat(myLogInfo.getName(), is(logName));
    assertThat(myLogInfo.getUuid(), is(uuid));
  }

  @Test
  public void getName() {
    assertEquals(name, logInfo.getName());
  }

  @Test
  public void setBrokerInfoSet_null() {
    logInfo.setBrokerInfoSet(null);
    assertThat(logInfo.getBootstrapServers(), is(nullValue()));
  }

  @Test
  public void setBootstrapServers_null() {
    logInfo.setBootstrapServers(null);
    assertThat(logInfo.getBootstrapServers(), is(nullValue()));
  }

  private static Set<KafkaBrokerInfo> createKafkaBrokerInfoSet(
      KafkaBrokerEndpoint endpoint1, KafkaBrokerEndpoint endpoint2) {
    Set<KafkaBrokerInfo> brokerInfoSet = new HashSet<>();
    brokerInfoSet.add(
        new KafkaBrokerInfo(
            1, "aHost", 3000, 3001, new KafkaBrokerEndpoint[] {endpoint1, endpoint2}, "20:20:02"));
    return brokerInfoSet;
  }

  private static void verifyBootstrapServersForEndpoints(
      LogInfo aLogInfo, KafkaBrokerEndpoint endpoint1, KafkaBrokerEndpoint endpoint2) {
    assertThat(
        aLogInfo.getBootstrapServers(),
        is(
            Stream.of(endpoint1, endpoint2)
                .map(KafkaBrokerEndpoint::toURL)
                .collect(Collectors.joining(","))));
  }

  @Test
  public void setBrokerInfoSet() {
    KafkaBrokerEndpoint endpoint1 = new KafkaBrokerEndpoint("plain://", "kafka_server:9091");
    KafkaBrokerEndpoint endpoint2 = new KafkaBrokerEndpoint("ssl://", "kafka_server:9093");
    logInfo.setBrokerInfoSet(createKafkaBrokerInfoSet(endpoint1, endpoint2));
    verifyBootstrapServersForEndpoints(logInfo, endpoint1, endpoint2);
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
  public void setExists() {
    Stream.of(true, false)
        .forEach(
            exists -> {
              logInfo.setExists(exists);
              assertThat(logInfo.isExists(), is(exists));
            });
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
    EqualsVerifier.forClass(LogInfo.class)
        .usingGetClass()
        .withIgnoredFields(
            "uuid",
            "startOffset",
            "endOffset",
            "bytes",
            "exists",
            "humanReadableByteSize",
            "bootstrapServers",
            "ctime",
            "mtime")
        .verify();
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
            "Log {name="
                + logInfo.getName()
                + ", bootstrapServers="
                + logInfo.getBootstrapServers()
                + ", ctime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                + ", mtime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                + "}"));
  }
}
