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

package net.ittera.pal.common.directory.kafka;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class KafkaBrokerInfoTest {

  int version;
  private String host;
  private int port;
  private int jmxPort;
  private KafkaBrokerEndpoint[] endpoints;
  private String endpoint_host;
  private int endpoint_port;
  private String timestamp;

  KafkaBrokerInfo brokerInfo;

  @Before
  public void setUp() throws Exception {
    version = 101;
    host = "the-host.com";
    port = 3982;
    jmxPort = 3827;
    timestamp = "492740445";
    endpoint_host = "server_abc";
    endpoint_port = 3199;
    endpoints =
        new KafkaBrokerEndpoint[] {
          new KafkaBrokerEndpoint(
              "INSIDE://", String.format("%s:%d", endpoint_host, endpoint_port)),
          new KafkaBrokerEndpoint(
              "OUTSIDE://", String.format("%s:%d", endpoint_host, endpoint_port)),
        };
    brokerInfo = new KafkaBrokerInfo(version, host, port, jmxPort, endpoints, timestamp);
  }

  @Test
  public void getVersion() {
    assertThat(brokerInfo.getVersion(), is(version));
  }

  @Test
  public void getHost() {
    assertThat(brokerInfo.getHost(), is(host));
  }

  @Test
  public void getPort() {
    assertThat(brokerInfo.getPort(), is(port));
  }

  @Test
  public void getJmxPort() {
    assertThat(brokerInfo.getJmxPort(), is(jmxPort));
  }

  @Test
  public void getEndpoints() {
    assertThat(brokerInfo.getEndpoints(), is(endpoints));
  }

  @Test
  public void getTimestamp() {
    assertThat(brokerInfo.getTimestamp(), is(timestamp));
  }

  @Test
  public void parseFromJSON() {
    String json =
        '{'
            + "\"listener_security_protocol_map\":"
            + "{"
            + "\"INSIDE\":\"PLAINTEXT\","
            + "\"OUTSIDE\":\"PLAINTEXT\""
            + "}"
            + String.format(
                ",\"endpoints\":[\"INSIDE://%s:%d\",\"OUTSIDE://%s:%d\"]",
                endpoints[0].getHost(),
                endpoints[0].getPort(),
                endpoints[1].getHost(),
                endpoints[1].getPort())
            + ",\"jmx_port\":"
            + jmxPort
            + ",\"host\":"
            + "\""
            + host
            + "\""
            + ",\"timestamp\":"
            + "\""
            + timestamp
            + "\""
            + ",\"port\":"
            + port
            + ",\"version\":"
            + version
            + '}';

    KafkaBrokerInfo brokerInfoFromJson = KafkaBrokerInfo.parseFromJSON(json);

    assertThat(brokerInfoFromJson.getVersion(), is(version));
    assertThat(brokerInfoFromJson.getHost(), is(host));
    assertThat(brokerInfoFromJson.getPort(), is(port));
    assertThat(brokerInfoFromJson.getJmxPort(), is(jmxPort));
    List<KafkaBrokerEndpoint> parsedEndpoints = new ArrayList<>();
    Stream.of(endpoints)
        .forEach(
            e ->
                parsedEndpoints.add(
                    new KafkaBrokerEndpoint(
                        "PLAINTEXT", String.format("%s:%d", endpoint_host, endpoint_port))));
    assertThat(
        brokerInfoFromJson.getEndpoints(), is(parsedEndpoints.toArray(new KafkaBrokerEndpoint[0])));
    assertThat(brokerInfoFromJson.getTimestamp(), is(timestamp));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(KafkaBrokerInfo.class)
        .withIgnoredFields("version", "jmxPort", "timestamp")
        .usingGetClass()
        .verify();
  }
}
