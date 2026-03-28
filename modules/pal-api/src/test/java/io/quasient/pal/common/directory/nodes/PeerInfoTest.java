/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.directory.nodes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class PeerInfoTest {

  private PeerInfo peerInfo;
  private final UUID peerUuid = UUID.randomUUID();
  private final String peerName = "peer-ab";
  private final String zmqRpcAddress = "localhost:2991";
  private final String jsonRpcAddress = "localhost:8993";
  private final String pubAddress = "localhost:2992";
  private final String jmxAddress = "localhost:2993";

  @Before
  public void setUp() {
    peerInfo = new PeerInfo(peerUuid, peerName);
    peerInfo.setZmqRpcAddress(zmqRpcAddress);
    peerInfo.setJsonrpcAddress(jsonRpcAddress);
    peerInfo.setPubAddress(pubAddress);
    peerInfo.setJmxAddress(jmxAddress);
  }

  @Test
  public void getUuid() {
    assertEquals(peerUuid, peerInfo.getUuid());
  }

  @Test
  public void getName() {
    assertEquals(peerName, peerInfo.getName());
  }

  @Test
  public void getZmqRpcAddress() {
    assertEquals(zmqRpcAddress, peerInfo.getZmqRpcAddress());
  }

  @Test
  public void getJsonRpcAddress() {
    assertEquals(jsonRpcAddress, peerInfo.getJsonrpcAddress());
  }

  @Test
  public void getPubAddress() {
    assertEquals(pubAddress, peerInfo.getPubAddress());
  }

  @Test
  public void getJmxAddress() {
    assertEquals(jmxAddress, peerInfo.getJmxAddress());
  }

  @Test
  public void compareTo() {
    String uuid1 = "863f7bc4-24ab-4e98-8d8a-3a9e4be4a2d1";
    String uuid2 = "863f7bc4-24ab-4e98-8d8a-3a9e4be4a2d2";
    PeerInfo first = new PeerInfo(UUID.fromString(uuid1));
    PeerInfo second = new PeerInfo(UUID.fromString(uuid2));
    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  public void equalsContract() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    PeerInfo a = new PeerInfo(id, "peer");
    PeerInfo b = new PeerInfo(id, "peer");
    PeerInfo c = new PeerInfo(id, "peer");
    PeerInfo different =
        new PeerInfo(UUID.fromString("00000000-0000-0000-0000-0000000000ab"), "peer");

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }

  @Test
  public void toJsonAndFromJsonRoundTrip() {
    peerInfo.setCtime(1700000000000L);
    peerInfo.setMtime(1700001000000L);

    String json = peerInfo.toJson();
    PeerInfo restored = PeerInfo.fromJson(json);

    assertThat(restored.getUuid(), is(peerUuid));
    assertThat(restored.getName(), is(peerName));
    assertThat(restored.getZmqRpcAddress(), is(zmqRpcAddress));
    assertThat(restored.getJsonrpcAddress(), is(jsonRpcAddress));
    assertThat(restored.getPubAddress(), is(pubAddress));
    assertThat(restored.getJmxAddress(), is(jmxAddress));
    assertThat(restored.getCTime(), is(peerInfo.getCTime()));
    assertThat(restored.getMTime(), is(peerInfo.getMTime()));
  }

  @Test
  public void fromJsonWithMinimalFields() {
    UUID uuid = UUID.randomUUID();
    PeerInfo minimal = new PeerInfo(uuid);
    String json = minimal.toJson();
    PeerInfo restored = PeerInfo.fromJson(json);
    assertThat(restored.getUuid(), is(uuid));
    assertThat(restored.getName(), is(nullValue()));
  }

  @Test
  public void testToString() {

    // set time fields
    long ctime = 22892339L;
    long mtime = 23982349L;
    peerInfo.setCtime(ctime);
    peerInfo.setMtime(mtime);

    assertThat(
        peerInfo.toString(),
        is(
            "Peer {"
                + "uuid="
                + peerUuid
                + ", name='"
                + peerName
                + '\''
                + ", zmqRpcAddress='"
                + zmqRpcAddress
                + '\''
                + ", jsonRpcAddress='"
                + jsonRpcAddress
                + '\''
                + ", pubAddress='"
                + pubAddress
                + '\''
                + ", jmxAddress='"
                + jmxAddress
                + '\''
                + ", ctime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctime), ZoneOffset.UTC)
                + ", mtime="
                + OffsetDateTime.ofInstant(Instant.ofEpochMilli(mtime), ZoneOffset.UTC)
                + '}'));
  }
}
