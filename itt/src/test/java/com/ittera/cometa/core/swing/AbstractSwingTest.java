package com.ittera.cometa.core.swing;

import com.ittera.cometa.core.AbstractPeerMessageIT;
import com.ittera.cometa.cxn.ThinPeer;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

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
