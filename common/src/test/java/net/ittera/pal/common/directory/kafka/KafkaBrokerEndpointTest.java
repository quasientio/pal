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

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class KafkaBrokerEndpointTest {

  private KafkaBrokerEndpoint brokerEndpoint;
  private String protocol;
  private String host;
  private int port;

  @Before
  public void setUp() throws Exception {
    protocol = "plain";
    host = "serverAB";
    port = 9009;
    brokerEndpoint = new KafkaBrokerEndpoint(protocol, String.format("%s:%d", host, port));
  }

  @Test
  public void getProtocol() {
    assertThat(brokerEndpoint.getProtocol(), is(protocol));
  }

  @Test
  public void getHost() {
    assertThat(brokerEndpoint.getHost(), is(host));
  }

  @Test
  public void getPort() {
    assertThat(brokerEndpoint.getPort(), is(port));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(KafkaBrokerEndpoint.class).usingGetClass().verify();
  }

  @Test
  public void testToString() {
    assertThat(brokerEndpoint.toString(), is(protocol + "://" + host + ':' + port));
  }

  @Test
  public void toURL() {
    assertThat(brokerEndpoint.toURL(), is(host + ':' + port));
  }
}
