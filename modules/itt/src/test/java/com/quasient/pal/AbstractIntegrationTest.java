/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal;

import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.util.Base62UuidGenerator;
import com.quasient.pal.common.util.IdGenerator;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.messages.types.RpcType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import org.zeromq.ZContext;

public abstract class AbstractIntegrationTest {

  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

  private static String PAL_DIRECTORY_URL;
  private static String KAFKA_SERVERS;
  private static final IdGenerator idGenerator = new Base62UuidGenerator();

  protected static Properties getKafkaConsumerProperties() throws IOException {
    var properties = new Properties();
    try (final InputStream stream =
        AbstractIntegrationTest.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    return properties;
  }

  protected static Properties getKafkaProducerProperties() throws IOException {
    var properties = new Properties();
    try (final InputStream stream =
        AbstractIntegrationTest.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    return properties;
  }

  protected static String getPalDirectoryUrl() {
    if (PAL_DIRECTORY_URL == null) {
      final String palDirectoryUrl = System.getenv("PAL_DIRECTORY");
      if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
        throw new RuntimeException(
            "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2379)");
      }
      PAL_DIRECTORY_URL = palDirectoryUrl;
    }
    return PAL_DIRECTORY_URL;
  }

  protected static String getKafkaServers() {
    if (KAFKA_SERVERS == null) {
      final String kafkaServers = System.getenv("KAFKA_SERVERS");
      if (kafkaServers == null || kafkaServers.isEmpty()) {
        throw new RuntimeException(
            "Please set the environment variable KAFKA_SERVERS (eg. KAFKA_SERVERS=localhost:9092)");
      }
      KAFKA_SERVERS = kafkaServers;
    }
    return KAFKA_SERVERS;
  }

  protected static Optional<PeerInfo> findRpcPeer(
      RpcType rpcType, DirectoryConnectionProvider directoryConnectionProvider)
      throws ExecutionException, InterruptedException {
    Predicate<PeerInfo> hasRpcType =
        peerInfo -> {
          if (rpcType == RpcType.BIN_RPC) {
            return peerInfo.getRpcAddress() != null;
          } else {
            return peerInfo.getJsonrpcAddress() != null;
          }
        };
    PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    return palDirectory.getAllPeers().stream().filter(hasRpcType).findFirst();
  }

  protected static ZContext createZmqContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  protected static String generateId() {
    return idGenerator.nextId();
  }
}
