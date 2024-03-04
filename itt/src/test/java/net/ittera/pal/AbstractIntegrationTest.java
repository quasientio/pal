package net.ittera.pal;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.types.RPCType;

public abstract class AbstractIntegrationTest {

  private static String PAL_DIRECTORY_URL;
  private static String KAFKA_SERVERS;

  protected static String getPALDirectoryURL() {
    if (PAL_DIRECTORY_URL == null) {
      final String palDirectoryURL = System.getenv("PAL_DIRECTORY");
      if (palDirectoryURL == null || palDirectoryURL.isEmpty()) {
        throw new RuntimeException(
            "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2379)");
      }
      PAL_DIRECTORY_URL = palDirectoryURL;
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

  protected static Optional<PeerInfo> findRPCPeer(RPCType rpcType)
      throws ExecutionException, InterruptedException {
    Predicate<PeerInfo> hasRpcType =
        peerInfo -> {
          if (rpcType == RPCType.RPC) {
            return peerInfo.getRpcAddress() != null;
          } else {
            return peerInfo.getJsonrpcAddress() != null;
          }
        };

    try (PALDirectory palDirectory = new PALDirectory(getPALDirectoryURL())) {
      return palDirectory.getAllPeers().stream().filter(hasRpcType).findFirst();
    }
  }
}
