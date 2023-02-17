package net.ittera.pal.cxn;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String ENDPOINT = "ip://localhost:2379";
  private final String LOGS_PATH = "/pal/logs";
  private final String PEERS_PATH = "/pal/peers";
  private final ByteSequence LOGS_KEY = ByteSequence.from(LOGS_PATH.getBytes());
  private final ByteSequence PEERS_KEY = ByteSequence.from(PEERS_PATH.getBytes());
  private KV kvClient;
  private List<ByteSequence> logsCreated;
  private List<ByteSequence> peersCreated;

  @Before
  public void setup() {
    Client client = Client.builder().target(ENDPOINT).build();
    kvClient = client.getKVClient();
    logsCreated = new ArrayList<>();
    peersCreated = new ArrayList<>();
  }

  @After
  public void deleteAllKVs() throws ExecutionException, InterruptedException {
    CompletableFuture<DeleteResponse> delResp =
        kvClient.delete(LOGS_KEY, DeleteOption.newBuilder().isPrefix(true).build());
    logger.info("{} log entries deleted", delResp.get().getDeleted());
    delResp = kvClient.delete(PEERS_KEY, DeleteOption.newBuilder().isPrefix(true).build());
    logger.info("{} peer entries deleted", delResp.get().getDeleted());
  }

  private void createLog(String logname, UUID logUuid)
      throws ExecutionException, InterruptedException {
    final String completeLogPath = String.format("%s/%s", LOGS_PATH, logname);
    LogInfo logInfo = new LogInfo(logname, logUuid);
    ByteSequence logKey = ByteSequence.from(completeLogPath.getBytes());
    kvClient.put(logKey, ByteSequence.from(logInfo.toJSONString().getBytes())).get();
    logsCreated.add(logKey);
    logger.info("Created log with path: {} -- {}", completeLogPath, logInfo.toJSONString());
  }

  private void createPeer(String peerName, UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final String completePeerPath = String.format("%s/%s", PEERS_PATH, peerUuid.toString());
    PeerInfo peerInfo = new PeerInfo(peerUuid, peerName);
    ByteSequence peerKey = ByteSequence.from(completePeerPath.getBytes());
    kvClient.put(peerKey, ByteSequence.from(peerInfo.toJSONString().getBytes())).get();
    peersCreated.add(peerKey);
    logger.info("Created peer with path: {}", completePeerPath);
  }

  @Test
  public void testLogs() throws ExecutionException, InterruptedException {
    printAllLogs();
    createLog("app_log1", UUID.randomUUID());
    createLog("app_log2", UUID.randomUUID());
    printAllLogs();
    logger.info("creating another app_log1");
    createLog("app_log1", UUID.randomUUID());
    printAllLogs();
  }

  @Test
  public void testPeers() throws ExecutionException, InterruptedException {
    printAllPeers();
    UUID peer1Uuid = UUID.randomUUID();
    createPeer("peer1", peer1Uuid);
    createPeer("peer2", UUID.randomUUID());
    printAllPeers();
    logger.info("creating another peer1");
    createPeer("peer1NewName", peer1Uuid);
    printAllPeers();
  }

  private void printAllLogs() throws ExecutionException, InterruptedException {
    printAllKVs(LOGS_KEY);
  }

  private void printAllPeers() throws ExecutionException, InterruptedException {
    printAllKVs(PEERS_KEY);
  }

  private void printAllKVs(ByteSequence pathKey) throws ExecutionException, InterruptedException {
    GetOption option =
        GetOption.newBuilder()
            .withSortField(GetOption.SortTarget.CREATE)
            .withSortOrder(GetOption.SortOrder.ASCEND)
            .isPrefix(true)
            .build();

    CompletableFuture<GetResponse> futureResponse = kvClient.get(pathKey, option);

    GetResponse response = futureResponse.get();
    if (response.getKvs().isEmpty()) {
      logger.info("Failed to retrieve any key.");
      return;
    }

    for (KeyValue kv : response.getKvs()) {
      logger.info("found key={}, val={}", kv.getKey().toString(), kv.getValue().toString());
    }

    logger.info("Retrieved {} keys", response.getKvs().size());
  }
}
