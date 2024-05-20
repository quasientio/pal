package net.ittera.pal;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.types.RpcType;
import org.zeromq.ZContext;

public abstract class AbstractIntegrationTest {

  private static String PAL_DIRECTORY_URL;
  private static String KAFKA_SERVERS;

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

  protected static Optional<PeerInfo> findRpcPeer(RpcType rpcType)
      throws ExecutionException, InterruptedException {
    Predicate<PeerInfo> hasRpcType =
        peerInfo -> {
          if (rpcType == RpcType.RPC) {
            return peerInfo.getRpcAddress() != null;
          } else {
            return peerInfo.getJsonrpcAddress() != null;
          }
        };

    try (PalDirectory palDirectory = new PalDirectory(getPalDirectoryUrl())) {
      return palDirectory.getAllPeers().stream().filter(hasRpcType).findFirst();
    }
  }

  protected static ZContext createZmqContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }
}
