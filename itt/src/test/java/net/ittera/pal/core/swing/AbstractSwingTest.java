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

package net.ittera.pal.core.swing;

import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import net.ittera.pal.core.AbstractPeerMessageIT;
import net.ittera.pal.cxn.ThinPeer;

public abstract class AbstractSwingTest {

  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";
  protected static final UUID clientId = UUID.randomUUID();

  protected ThinPeer getThinPeer() throws Exception {
    final Properties consumerProperties = new Properties();
    try (final InputStream stream =
        AbstractPeerMessageIT.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      consumerProperties.load(stream);
    }
    final Properties producerProperties = new Properties();
    try (final InputStream stream =
        AbstractPeerMessageIT.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      producerProperties.load(stream);
    }
    final String palDirectoryURL = System.getenv("PAL_DIRECTORY");
    if (palDirectoryURL == null) {
      throw new RuntimeException(
          "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2181)");
    }
    return new ThinPeer()
        .withUUID(clientId)
        .withDirectoryURL(palDirectoryURL)
        .withConsumerProperties(consumerProperties)
        .withProducerProperties(producerProperties)
        .init();
  }
}
