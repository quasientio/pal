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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;

public class PeerInfoTest {

  private PeerInfo peerInfo;
  private final UUID peerUuid = UUID.randomUUID();
  private final String peerName = "peer-ab";
  private final String rpcAddress = "localhost:2991";
  private final String jsonRpcAddress = "localhost:8993";
  private final String pubAddress = "localhost:2992";
  private final String jmxAddress = "localhost:2993";

  @Before
  public void setUp() {
    peerInfo = new PeerInfo(peerUuid, peerName);
    peerInfo.setRpcAddress(rpcAddress);
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
  public void getRpcAddress() {
    assertEquals(rpcAddress, peerInfo.getRpcAddress());
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
    EqualsVerifier.forClass(PeerInfo.class)
        .usingGetClass()
        .withIgnoredFields("pubAddress", "jmxAddress", "name", "ctime", "mtime")
        .suppress(Warning.NONFINAL_FIELDS)
        .verify();
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
                + ", rpcAddress='"
                + rpcAddress
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
