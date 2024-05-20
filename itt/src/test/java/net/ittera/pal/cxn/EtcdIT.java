package net.ittera.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import net.ittera.pal.AbstractIntegrationTest;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String LOGS_PATH = "/pal/logs";
  private static final String PEERS_PATH = "/pal/peers";
  private static final String INTERCEPTS_PATH = "/pal/intercepts";
  private static final ByteSequence PEERS_KEY =
      ByteSequence.from(PEERS_PATH.getBytes(StandardCharsets.UTF_8));
  private static final ByteSequence INTERCEPTS_KEY =
      ByteSequence.from(INTERCEPTS_PATH.getBytes(StandardCharsets.UTF_8));
  private Client etcdClient;
  private KV kvClient;
  private Watch watchClient;
  private List<ByteSequence> logsCreated;
  private List<ByteSequence> peersCreated;
  private List<ByteSequence> interceptsCreated;

  @Before
  public void setup() {
    etcdClient = Client.builder().target(getPalDirectoryUrl()).build();
    kvClient = etcdClient.getKVClient();
    watchClient = etcdClient.getWatchClient();
    logsCreated = new ArrayList<>();
    peersCreated = new ArrayList<>();
    interceptsCreated = new ArrayList<>();
  }

  @After
  public void cleanup() {
    // delete all created key-values
    Stream.of(logsCreated, peersCreated, interceptsCreated)
        .flatMap(Collection::stream)
        .forEach(
            k -> {
              try {
                kvClient.delete(k).get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    kvClient.close();
    watchClient.close();
    etcdClient.close();
  }

  private LogInfo createLog(String logName, UUID logUuid)
      throws ExecutionException, InterruptedException {
    final String completeLogPath = String.format("%s/%s", LOGS_PATH, logName);
    LogInfo logInfo = new LogInfo(logName, logUuid);
    ByteSequence logKey = ByteSequence.from(completeLogPath.getBytes(StandardCharsets.UTF_8));
    kvClient
        .put(logKey, ByteSequence.from(logInfo.toJson().getBytes(StandardCharsets.UTF_8)))
        .get();
    logsCreated.add(logKey);
    logger.info("Created log with path: {} -- {}", completeLogPath, logInfo.toJson());
    return logInfo;
  }

  private void createPeer(String peerName, UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final String completePeerPath = String.format("%s/%s", PEERS_PATH, peerUuid.toString());
    PeerInfo peerInfo = new PeerInfo(peerUuid, peerName);
    ByteSequence peerKey = ByteSequence.from(completePeerPath.getBytes(StandardCharsets.UTF_8));
    kvClient
        .put(peerKey, ByteSequence.from(peerInfo.toJson().getBytes(StandardCharsets.UTF_8)))
        .get();
    peersCreated.add(peerKey);
    logger.info("Created peer with path: {}", completePeerPath);
  }

  private void createInterceptRequest(InterceptRequest<?> interceptRequest)
      throws ExecutionException, InterruptedException {
    final byte[] interceptData = interceptRequest.toBytes(StandardCharsets.UTF_8);
    final String interceptPath =
        String.format(
            "%s/%s/%s", INTERCEPTS_PATH, interceptRequest.getPeer(), interceptRequest.getUuid());
    final ByteSequence interceptKey =
        ByteSequence.from(interceptPath.getBytes(StandardCharsets.UTF_8));
    kvClient.put(interceptKey, ByteSequence.from(interceptData)).get();
    interceptsCreated.add(interceptKey);
  }

  @Test
  public void testLogsWithPrefix() throws ExecutionException, InterruptedException {
    List<LogInfo> appLogs = new ArrayList<>();
    appLogs.add(createLog("app_log1", UUID.randomUUID()));
    appLogs.add(createLog("app_log2", UUID.randomUUID()));
    appLogs.add(createLog("app_log3", UUID.randomUUID()));
    List<LogInfo> vmLogs = new ArrayList<>();
    vmLogs.add(createLog("vm_log1", UUID.randomUUID()));
    vmLogs.add(createLog("vm_log2", UUID.randomUUID()));
    List<KeyValue> appLogEntries =
        getAllKeyValues(
            ByteSequence.from(
                String.format("%s/%s", LOGS_PATH, "app_log").getBytes(StandardCharsets.UTF_8)));
    List<KeyValue> vmLogEntries =
        getAllKeyValues(
            ByteSequence.from(
                String.format("%s/%s", LOGS_PATH, "vm_log").getBytes(StandardCharsets.UTF_8)));
    assertThat(appLogEntries.size(), is(appLogs.size()));
    assertThat(vmLogEntries.size(), is(vmLogs.size()));
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

  @Test
  public void testIntercepts() throws ExecutionException, InterruptedException {
    UUID peerUuid = UUID.randomUUID();
    createPeer("peer1forIntercepts", peerUuid);
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    CountDownLatch countDownLatch = new CountDownLatch(1);
    watchClient.watch(
        INTERCEPTS_KEY,
        WatchOption.builder().isPrefix(true).build(),
        watchResponse -> {
          for (WatchEvent event : watchResponse.getEvents()) {
            switch (event.getEventType()) {
              case PUT:
                countDownLatch.countDown();
                break;
              case DELETE:
              case UNRECOGNIZED:
                break;
              default:
                throw new IllegalStateException("Unexpected value: " + event.getEventType());
            }
          }
        });
    createInterceptRequest(interceptRequest);
    countDownLatch.await();
  }

  private void printAllPeers() throws ExecutionException, InterruptedException {
    List<KeyValue> kvs = getAllKeyValues(PEERS_KEY);

    if (kvs.isEmpty()) {
      logger.info("Failed to retrieve any key.");
      return;
    }

    for (KeyValue kv : kvs) {
      logger.info("found key={}, val={}", kv.getKey().toString(), kv.getValue().toString());
    }

    logger.info("Retrieved {} keys", kvs.size());
  }

  private List<KeyValue> getAllKeyValues(ByteSequence pathKey)
      throws ExecutionException, InterruptedException {
    GetOption option =
        GetOption.builder()
            .withSortField(GetOption.SortTarget.CREATE)
            .withSortOrder(GetOption.SortOrder.ASCEND)
            .isPrefix(true)
            .build();

    CompletableFuture<GetResponse> futureResponse = kvClient.get(pathKey, option);
    return futureResponse.get().getKvs();
  }
}
