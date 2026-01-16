/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static java.lang.String.format;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.maintenance.StatusResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import io.quasient.pal.common.directory.events.InterceptEvent;
import io.quasient.pal.common.directory.events.InterceptNodeListener;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the directory of peers, logs, and intercepts using etcd as the backend service. Provides
 * functionalities to register, unregister, and query peers and logs, as well as manage intercept
 * requests.
 */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification =
        "etcd connection failure should propagate - caller handles initialization errors")
public class PalDirectory {

  /** Logger instance for PalDirectory to log operations. */
  private static final Logger logger = LoggerFactory.getLogger(PalDirectory.class);

  /** Duration specifying the interval between keepalive messages to the etcd client. */
  private static final Duration ETCD_KEEP_ALIVE_TIME = Duration.ofSeconds(60);

  /** Duration specifying the timeout for etcd client keepalive messages. */
  private static final Duration ETCD_KEEP_ALIVE_TIMEOUT = Duration.ofSeconds(20);

  /** Default duration specifying the timeout for etcd connection attempts. */
  private static final Duration DEFAULT_ETCD_CONNECTION_TIMEOUT = Duration.ofSeconds(5);

  /** Instance etcd connection timeout used for health checks and client connect timeout. */
  private final Duration etcdConnectionTimeout;

  /** Constant indicating that no directory URL has been provided. */
  public static final String NO_URL = "<none>";

  /** Default namespace used for organizing directory entries. */
  private static final String DEFAULT_PAL_NAMESPACE = "pal";

  /** Directory name for storing peer information. */
  private static final String PEERS_DIR = "peers";

  /** Directory name for storing log information. */
  private static final String LOGS_DIR = "logs";

  /** Subdirectory name for storing the log name-to-UUID index. */
  private static final String LOGS_BY_NAME_DIR = "by-name";

  /** Subdirectory name for storing log auto-naming counters. */
  private static final String LOGS_COUNTERS_DIR = "counters";

  /** Directory name for storing intercept configurations. */
  private static final String INTERCEPTS_DIR = "intercepts";

  /** Character set used for encoding/decoding strings. */
  private static final Charset UTF8 = StandardCharsets.UTF_8;

  /** URL of the etcd directory service. */
  private final String directoryUrl;

  /** Etcd client used for interacting with the etcd service. */
  private final Client client;

  /** Key-Value client for etcd operations. */
  private final KV kvClient;

  /** Watch client for etcd KVs. */
  private final Watch watchClient;

  /** Intercepts etcd watcher subscription that must be closed on shutdown. Lazily instantiated. */
  @Nullable private Watch.Watcher interceptsWatcher;

  /** Flag for idempotent close(). */
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Namespace used within etcd for organizing entries. */
  private final String namespace;

  /** Listeners subscribed to intercept node events. */
  private final List<InterceptNodeListener> interceptListeners = new CopyOnWriteArrayList<>();

  /** Scheduled executor used to periodically update the live leases. */
  private final ScheduledExecutorService leasePool;

  /** Maps peer UUID's to the leaseId they hold. */
  private final Map<UUID, Long> peerToLeaseIdCache = new ConcurrentHashMap<>();

  /** Gson serializer. */
  private static final Gson gson = new Gson();

  /**
   * Constructs a PalDirectory instance with the specified etcd connection string.
   *
   * @param connectionString the etcd connection string in the format "host:port"
   */
  public PalDirectory(String connectionString) {
    this(connectionString, null, false, DEFAULT_ETCD_CONNECTION_TIMEOUT);
  }

  /**
   * Ensures the etcd endpoint has a URL scheme. jetcd requires endpoints like "http://host:port" or
   * "https://host:port".
   */
  private static String normalizeEndpoint(String endpoint) {
    if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
      return endpoint;
    }
    return "http://" + endpoint;
  }

  /** Returns the library default etcd connection timeout. */
  public static Duration getDefaultConnectionTimeout() {
    return DEFAULT_ETCD_CONNECTION_TIMEOUT;
  }

  /**
   * Constructs a PalDirectory instance with the specified etcd connection string and blocking
   * behavior.
   *
   * @param connectionString the etcd connection string in the format "host:port"
   * @param blocking if true, the constructor will block until a connection to etcd is established
   */
  public PalDirectory(String connectionString, boolean blocking) {
    this(connectionString, null, blocking, DEFAULT_ETCD_CONNECTION_TIMEOUT);
  }

  /**
   * Constructs a PalDirectory instance with a list of etcd endpoint URIs.
   *
   * @param endpoints list of etcd endpoint URIs
   */
  public PalDirectory(List<URI> endpoints) {
    this(
        endpoints.stream().map(URI::toString).collect(Collectors.joining(",")),
        null,
        false,
        DEFAULT_ETCD_CONNECTION_TIMEOUT);
  }

  /**
   * Constructs a PalDirectory instance with specified etcd endpoints, namespace, and blocking
   * behavior.
   *
   * @param endpoints the etcd endpoints as a comma-separated string
   * @param namespace the namespace to use for directory entries; if null, the default namespace is
   *     used
   * @param blocking if true, the constructor will block until a connection to etcd is established
   * @throws RuntimeException if the thread is interrupted or connection to etcd fails when blocking
   *     is true
   */
  public PalDirectory(String endpoints, String namespace, boolean blocking) {
    this(endpoints, namespace, blocking, DEFAULT_ETCD_CONNECTION_TIMEOUT);
  }

  /** Full constructor with configurable connection timeout. */
  public PalDirectory(
      String endpoints, String namespace, boolean blocking, Duration connectionTimeout) {
    this.directoryUrl = endpoints;
    this.etcdConnectionTimeout =
        connectionTimeout == null ? DEFAULT_ETCD_CONNECTION_TIMEOUT : connectionTimeout;
    logger.info(
        "Will connect to etcd endpoints: {} in {} mode",
        endpoints,
        blocking ? "blocking" : "non-blocking");

    if (blocking) {
      // Perform preflight health check to fail fast if etcd is unreachable
      // This works around jetcd's gRPC connection logic which can hang indefinitely
      try {
        List<String> endpointList = Splitter.on(',').splitToList(endpoints);
        EtcdHealthCheck.assertReachable(endpointList, (int) etcdConnectionTimeout.toMillis());
        logger.info("Preflight health check passed for etcd endpoints: {}", endpoints);
      } catch (IllegalStateException e) {
        throw new EtcdUnavailableException(
            "Failed to connect to etcd cluster: " + e.getMessage(), e);
      }
    }

    // Build jetcd client with proper endpoints (do not use a single comma-separated target)
    List<String> endpointListRaw =
        Splitter.on(',').trimResults().omitEmptyStrings().splitToList(endpoints);
    List<String> endpointListNorm =
        endpointListRaw.stream().map(PalDirectory::normalizeEndpoint).toList();
    String[] endpointArray = endpointListNorm.toArray(new String[0]);
    this.client =
        Client.builder()
            .endpoints(endpointArray)
            .connectTimeout(etcdConnectionTimeout)
            .keepaliveTime(ETCD_KEEP_ALIVE_TIME)
            .keepaliveTimeout(ETCD_KEEP_ALIVE_TIMEOUT)
            .keepaliveWithoutCalls(false)
            .build();

    if (blocking) {
      // Verify connection with a per-endpoint status check to avoid hangs
      // already normalized with scheme
      boolean connected = false;
      for (String ep : endpointListNorm) {
        try {
          StatusResponse status =
              client
                  .getMaintenanceClient()
                  .statusMember(ep)
                  .get(etcdConnectionTimeout.toMillis(), TimeUnit.MILLISECONDS);
          logger.info("Connected to etcd endpoint: {} -> {}", ep, status);
          connected = true;
          break;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new EtcdUnavailableException("Thread was interrupted while connecting to etcd", e);
        } catch (TimeoutException | ExecutionException e) {
          logger.warn(
              "Status check to etcd endpoint {} failed within {}s: {}",
              ep,
              etcdConnectionTimeout.toSeconds(),
              e.getMessage());
          // try next endpoint
        }
      }
      if (!connected) {
        throw new EtcdUnavailableException(
            "Failed to connect to any etcd endpoint within "
                + etcdConnectionTimeout.toSeconds()
                + " seconds: "
                + endpoints);
      }
    }

    // init lease pool
    leasePool =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "peer-lease-ka");
              t.setDaemon(true);
              return t;
            });

    this.kvClient = client.getKVClient();
    this.watchClient = client.getWatchClient();
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;
  }

  /**
   * Constructs a PalDirectory instance with specified etcd endpoints and namespace.
   *
   * @param endpoints the etcd endpoints as a comma-separated string
   * @param namespace the namespace to use for directory entries; if null, the default namespace is
   *     used
   */
  public PalDirectory(String endpoints, String namespace) {
    this(endpoints, namespace, false);
  }

  // <editor-fold desc="Peer methods">

  /**
   * Checks if a peer with the specified UUID exists in the directory.
   *
   * @param peerUuid the UUID of the peer to check
   * @return {@code true} if the peer exists, {@code false} otherwise
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public boolean peerExists(UUID peerUuid) throws ExecutionException, InterruptedException {
    return kvClient.get(peerInfoKey(peerUuid)).get().getCount() != 0;
  }

  /**
   * Creates a new peer in the directory. If the peer already exists (key = uuid), then creation is
   * skipped. Information is kept in two separate sub-nodes: /info (static) and /state (updatable)
   *
   * @param peer the information of the peer to create. Must contain its uuid.
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void createPeer(PeerInfo peer) throws ExecutionException, InterruptedException {
    Objects.requireNonNull(peer, "peer cannot be null");
    Objects.requireNonNull(peer.getUuid(), "peer.uuid cannot be null");

    long now = System.currentTimeMillis();
    if (peer.getCTime() == null) peer.setCtime(now);
    if (peer.getMTime() == null) peer.setMtime(now);

    String infoJson = gson.toJson(PeerStatic.from(peer));
    String stateJson = gson.toJson(PeerState.from(peer));

    ByteSequence infoKey = peerInfoKey(peer.getUuid());
    ByteSequence stateKey = peerStateKey(peer.getUuid());

    TxnResponse tx =
        kvClient
            .txn()
            .If(new Cmp(infoKey, Cmp.Op.EQUAL, CmpTarget.version(0)))
            .Then(
                Op.put(infoKey, ByteSequence.from(infoJson, UTF8), PutOption.DEFAULT),
                Op.put(stateKey, ByteSequence.from(stateJson, UTF8), PutOption.DEFAULT))
            .commit()
            .get();

    if (tx.isSucceeded()) {
      logger.info("Created peer {}", peer.getUuid());
    } else {
      logger.warn("Peer {} already exists – skipping", peer.getUuid());
    }
  }

  /**
   * Updates a peer's state, with the provided lease id.
   *
   * @param peer the peer to update, containing its current state
   * @throws ExecutionException on etcd errors
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void updatePeer(PeerInfo peer, long leaseId)
      throws ExecutionException, InterruptedException {

    long now = System.currentTimeMillis();
    peer.setMtime(now);

    String stateJson = gson.toJson(PeerState.from(peer));
    ByteSequence stateKey = peerStateKey(peer.getUuid());

    kvClient
        .put(
            stateKey,
            ByteSequence.from(stateJson, UTF8),
            PutOption.builder().withLeaseId(leaseId).build())
        .get();
  }

  /**
   * Registers the source log for the specified peer.
   *
   * @param peerInfo the information of the peer
   * @param logInfo the log information to register as source-log for the peer
   * @param peerLease the lease assigned to the peer, null if no keep-alive
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the peer does not exist in the directory
   * @throws IllegalStateException if a source log is already registered for this peer
   */
  public void setSourceLog(PeerInfo peerInfo, LogInfo logInfo, @Nullable PeerLease peerLease)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    Objects.requireNonNull(peerInfo, "peerInfo");
    Objects.requireNonNull(logInfo, "logInfo");

    ByteSequence peerInfoKey = peerInfoKey(peerInfo.getUuid());
    ByteSequence sourceLogKey = ByteSequence.from(getPeerSourceLogPath(peerInfo.getUuid()), UTF8);
    ByteSequence sourceLogValue = ByteSequence.from(logInfo.getUuid().toString(), UTF8);

    Op.PutOp putOp;
    if (peerLease != null) {
      putOp =
          Op.put(
              sourceLogKey,
              sourceLogValue,
              PutOption.builder().withLeaseId(peerLease.leaseId).build());
    } else {
      putOp = Op.put(sourceLogKey, sourceLogValue, PutOption.DEFAULT);
    }

    TxnResponse tx =
        kvClient
            .txn()
            // peer must exist
            .If(
                new Cmp(peerInfoKey, Cmp.Op.GREATER, CmpTarget.version(0)),
                // source-log pointer must NOT exist yet
                new Cmp(sourceLogKey, Cmp.Op.EQUAL, CmpTarget.version(0)))
            .Then(putOp)
            .commit()
            .get();

    if (!tx.isSucceeded()) {
      // Decide what “failed” means
      if (!kvClient.get(peerInfoKey).get().getKvs().isEmpty()) {
        throw new IllegalStateException(
            "Source log already registered for peer " + peerInfo.getUuid());
      } else {
        throw new NoPeerInfoNodeException("Peer " + peerInfo.getUuid() + " does not exist");
      }
    }
    logger.info("Registered source log {} for peer {}", logInfo.getName(), peerInfo.getUuid());
  }

  /**
   * Retrieves the UUID of the source log associated with the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the UUID of the source log, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalStateException if multiple source logs are found for the peer
   */
  public UUID getSourceLogId(UUID peerUuid) throws ExecutionException, InterruptedException {
    final ByteSequence sourceLogPathKey =
        ByteSequence.from(getPeerSourceLogPath(peerUuid).getBytes(UTF8));
    final GetResponse response =
        kvClient.get(sourceLogPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException(
          "More than one source log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(UTF8));
  }

  /**
   * Registers the WAL (Write-Ahead Log) for the specified peer.
   *
   * @param peerInfo the information of the peer
   * @param logInfo the log information to register as WAL for the peer
   * @param peerLease the lease assigned to the peer, null if no keep-alive
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the peer does not exist in the directory
   * @throws IllegalStateException if an WAL entry is already registered for this peer
   */
  public void setWalLog(PeerInfo peerInfo, LogInfo logInfo, @Nullable PeerLease peerLease)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {

    Objects.requireNonNull(peerInfo, "peerInfo");
    Objects.requireNonNull(logInfo, "logInfo");

    ByteSequence peerInfoKey = peerInfoKey(peerInfo.getUuid());
    ByteSequence walPathKey = ByteSequence.from(getPeerWALPath(peerInfo.getUuid()), UTF8);
    ByteSequence walInfoValue = ByteSequence.from(logInfo.getUuid().toString(), UTF8);

    Op.PutOp putOp;
    if (peerLease != null) {
      putOp =
          Op.put(
              walPathKey, walInfoValue, PutOption.builder().withLeaseId(peerLease.leaseId).build());
    } else {
      putOp = Op.put(walPathKey, walInfoValue, PutOption.DEFAULT);
    }

    TxnResponse tx =
        kvClient
            .txn()
            .If(
                new Cmp(peerInfoKey, Cmp.Op.GREATER, CmpTarget.version(0)), // peer must exist
                new Cmp(
                    walPathKey,
                    Cmp.Op.EQUAL,
                    CmpTarget.version(0))) // wal-log pointer must NOT exist yet
            .Then(putOp)
            .commit()
            .get();

    if (!tx.isSucceeded()) {
      // Decide what “failed” means
      if (!kvClient.get(peerInfoKey).get().getKvs().isEmpty()) {
        throw new IllegalStateException(
            "A Write-Ahead log is already registered for peer " + peerInfo.getUuid());
      } else {
        throw new NoPeerInfoNodeException("Peer " + peerInfo.getUuid() + " does not exist");
      }
    }
    logger.info("Registered Write-Ahead log {} for peer {}", logInfo.getName(), peerInfo.getUuid());
  }

  /**
   * Retrieves the UUID of the WAL (write-ahead log) associated with the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the UUID of the WAL, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalStateException if multiple WAL logs are found for the peer
   */
  public UUID getWalId(UUID peerUuid) throws ExecutionException, InterruptedException {
    final String peerWalPath = getPeerWALPath(peerUuid);
    final ByteSequence peerWalPathKey = ByteSequence.from(peerWalPath.getBytes(UTF8));
    final GetResponse response =
        kvClient.get(peerWalPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException("More than one WAL-log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(UTF8));
  }

  /**
   * Retrieves the information of the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the {@link PeerInfo} of the peer, or {@code null} if the peer does not exist
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public PeerInfo getPeer(UUID peerUuid) throws ExecutionException, InterruptedException {

    GetResponse info = kvClient.get(peerInfoKey(peerUuid)).get();
    if (info.getCount() == 0) {
      logger.warn("Node for peer w/uuid: {} does not exist", peerUuid);
      return null;
    }

    PeerStatic ps = gson.fromJson(info.getKvs().get(0).getValue().toString(UTF8), PeerStatic.class);
    GetResponse state = kvClient.get(peerStateKey(peerUuid)).get();
    PeerState pst =
        state.getCount() == 0
            ? new PeerState(0L, null, null, null, null)
            : gson.fromJson(state.getKvs().get(0).getValue().toString(UTF8), PeerState.class);

    PeerInfo p = new PeerInfo(ps.uuid());
    if (ps.name() != null) {
      p.setName(ps.name());
    }
    p.setCtime(ps.ctimeMillis());
    if (pst.mtimeMillis() != 0L) { // 0 means “not present”
      p.setMtime(pst.mtimeMillis());
    }
    p.setZmqRpcAddress(pst.zmqRpc());
    p.setJsonrpcAddress(pst.jsonRpc());
    p.setPubAddress(pst.pub());
    p.setJmxAddress(pst.jmx());

    return p;
  }

  /**
   * Retrieves all peers in the directory.
   *
   * @return a {@link Set} of {@link PeerInfo} representing all peers
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  @SuppressWarnings("ComputeIfAbsentAmbiguousReference")
  public Set<PeerInfo> listPeers() throws ExecutionException, InterruptedException {
    String peersPrefix = getPeersPath() + '/'; // “…/peers/”
    GetResponse resp =
        kvClient
            .get(ByteSequence.from(peersPrefix, UTF8), GetOption.builder().isPrefix(true).build())
            .get();

    Map<UUID, PeerInfo> map = new HashMap<>();

    for (KeyValue kv : resp.getKvs()) {
      String key = kv.getKey().toString(UTF8);

      // Extract the uuid segment:  "/<ns>/peers/<uuid>/<suffix>"
      int start = peersPrefix.length();
      int slash = key.indexOf('/', start);
      if (slash == -1) { // unexpected
        continue;
      }
      UUID uuid = UUID.fromString(key.substring(start, slash));
      String suffix = key.substring(slash + 1); // "info", "state", …

      // Get or create a PeerInfo holder
      PeerInfo p = map.computeIfAbsent(uuid, PeerInfo::new);

      switch (suffix) {
        case "info" -> {
          PeerStatic ps = gson.fromJson(kv.getValue().toString(UTF8), PeerStatic.class);
          if (ps.name() != null) {
            p.setName(ps.name());
          }
          p.setCtime(ps.ctimeMillis()); // InfoNode long setter
        }
        case "state" -> {
          PeerState st = gson.fromJson(kv.getValue().toString(UTF8), PeerState.class);
          if (st.mtimeMillis() != 0) p.setMtime(st.mtimeMillis());
          p.setZmqRpcAddress(st.zmqRpc());
          p.setJsonrpcAddress(st.jsonRpc());
          p.setPubAddress(st.pub());
          p.setJmxAddress(st.jmx());
        }
        default -> {
          /* ignore logs/… */
        }
      }
    }

    Set<PeerInfo> peers = new TreeSet<>(map.values());

    if (logger.isDebugEnabled()) {
      logger.debug("all peers: {}", peers);
    }
    return peers;
  }

  /**
   * Deletes all peer nodes except those specified in the exclusion set, and cleans up associated
   * intercept requests.
   *
   * <p>This method removes all peers not in the exclusion set and their associated intercept
   * requests. When the exclusion set is null or empty, uses a fast-path that deletes the entire
   * peers subtree and all intercept requests in a single batch operation.
   *
   * @param excludePeers a {@link Set} of peer UUIDs to exclude from deletion; may be {@code null}
   * @return the number of peers deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public long purgePeersExcept(@Nullable Set<UUID> excludePeers)
      throws ExecutionException, InterruptedException {
    long deleted = 0;
    if (excludePeers != null && !excludePeers.isEmpty()) {
      /* Gather peers we *can* delete */
      for (UUID peerUuid :
          listPeers().stream().map(PeerInfo::getUuid).collect(Collectors.toSet())) {
        if (!excludePeers.contains(peerUuid)) {
          deletePeer(peerUuid);
          deleted++;
        }
      }
    } else {
      /* Fast-path: wipe the entire peers subtree */
      final DeleteResponse deleteResponse =
          kvClient.delete(getPeersPathKey(), DeleteOption.builder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();

      // Clean up all intercept requests since we deleted all peers
      if (deleted > 0) {
        final DeleteResponse interceptsDeleteResponse =
            kvClient
                .delete(getInterceptsPathKey(), DeleteOption.builder().isPrefix(true).build())
                .get();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Deleted {} intercept request(s) after deleting all peers",
              interceptsDeleteResponse.getDeleted());
        }
      }
    }
    if (deleted == 0) {
      logger.warn("No peers found to delete");
    } else {
      logger.info("Deleted {} peers", deleted);
    }
    return deleted;
  }

  /**
   * Deletes all peer nodes in the directory and cleans up all associated intercept requests.
   *
   * <p>This is a convenience method that calls {@link #purgePeersExcept(Set)} with a null exclusion
   * set, resulting in a fast-path deletion of all peers and intercept requests.
   *
   * @return the number of peers deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public long deletePeers() throws ExecutionException, InterruptedException {
    return purgePeersExcept(null);
  }

  /**
   * Deletes a peer subtree atomically (root + logs + state) and cleans up associated intercept
   * requests.
   *
   * <p>This method deletes the peer's entire subtree and then removes all intercept requests
   * registered by this peer. Intercept requests are stored separately under {@code
   * /pal/intercepts/<peer-uuid>} and are not automatically deleted with the peer subtree.
   *
   * @param peerUuid the peer's UUID
   * @throws ExecutionException on etcd errors
   * @throws InterruptedException if the thread is interrupted
   */
  public void deletePeer(UUID peerUuid) throws ExecutionException, InterruptedException {

    // 1) Read the immutable node "/peers/<uuid>/info"
    ByteSequence infoKey = peerInfoKey(peerUuid);
    ByteSequence peerRoot = ByteSequence.from(getPeerPath(peerUuid), UTF8);

    GetResponse get = kvClient.get(infoKey).get();
    if (get.getCount() == 0) {
      logger.warn("Cannot delete peer {}: node does not exist", peerUuid);
      return;
    }
    long version = get.getKvs().get(0).getVersion(); // optimistic-lock token

    // 2) Delete the entire subtree iff the /info node is unchanged
    TxnResponse tx =
        kvClient
            .txn()
            .If(new Cmp(infoKey, Cmp.Op.EQUAL, CmpTarget.version(version)))
            .Then(Op.delete(peerRoot, DeleteOption.builder().isPrefix(true).build()))
            .commit()
            .get();

    if (tx.isSucceeded()) {
      logger.info("Deleted peer {}", peerUuid);
      // Clean up intercept requests for this peer
      deleteInterceptsForPeer(peerUuid);
    } else {
      logger.warn("Failed to delete peer {}: node was modified concurrently", peerUuid);
    }
  }

  /**
   * Grants a TTL lease, attaches the peer’s /state key to it, and starts automatic keep-alive's.
   * Call once right after {@code createPeer()}.
   *
   * @param peerUuid the peer’s UUID
   * @param ttlSeconds desired TTL in seconds (e.g. 60)
   * @return handle you must {@code close()} on graceful shutdown
   * @throws ExecutionException on etcd errors
   * @throws InterruptedException if thread is interrupted while waiting
   * @throws IllegalStateException if the peer is stale or does not exist
   */
  public PeerLease createPeerLease(UUID peerUuid, long ttlSeconds)
      throws ExecutionException, InterruptedException, IllegalStateException {

    ByteSequence stateKey = peerStateKey(peerUuid); // "/…/peers/<uuid>/state"
    GetResponse resp = kvClient.get(stateKey).get();

    if (resp.getCount() == 0) {
      throw new IllegalStateException("Peer does not exist or is stale (no /state node)");
    }

    Lease leaseClient = client.getLeaseClient();
    long leaseId = leaseClient.grant(ttlSeconds).get().getID();

    /* -------------- keep-alive every TTL/3 seconds ------------------ */
    ScheduledFuture<?> ka =
        leasePool.scheduleAtFixedRate(
            () -> {
              try {
                @SuppressWarnings("unused")
                var unused = leaseClient.keepAliveOnce(leaseId);
              } catch (Exception e) {
                logger.warn("Lease keep-alive failed for peer {}", peerUuid, e);
              }
            },
            ttlSeconds / 3,
            ttlSeconds / 3,
            TimeUnit.SECONDS);

    /* -------------- attach /state key under the lease --------------- */
    ByteSequence stateVal = resp.getKvs().get(0).getValue(); // keep existing bytes

    kvClient.put(stateKey, stateVal, PutOption.builder().withLeaseId(leaseId).build()).get();

    // cache the leaseId
    peerToLeaseIdCache.put(peerUuid, leaseId);

    logger.info("Attached live lease {} (TTL {}s) to peer {}", leaseId, ttlSeconds, peerUuid);
    return new PeerLease(leaseId, ka, leaseClient);
  }

  // </editor-fold>

  // <editor-fold desc="Intercept methods">

  /**
   * Creates an {@link InterceptEvent} based on the received etcd watch event. Handles any namespace
   * that may itself contain '/' characters.
   *
   * @param event the etcd watch event
   * @return the constructed {@link InterceptEvent}, or {@code null} if the event type is unexpected
   *     or invalid
   */
  private InterceptEvent createInterceptEvent(WatchEvent event) {

    // 1) Map etcd event → domain event type
    final InterceptEvent.Type type;
    switch (event.getEventType()) {
      case PUT -> type = InterceptEvent.Type.INTERCEPT_ADDED;
      case DELETE -> type = InterceptEvent.Type.INTERCEPT_REMOVED;
      default -> {
        logger.error("Unexpected watch event: {}", event.getEventType());
        return null;
      }
    }

    // 2) Peel off the known prefix   "/<namespace>/intercepts/"
    String fullPath = event.getKeyValue().getKey().toString(UTF8);
    String interceptsRoot = getInterceptsPath() + '/';

    if (!fullPath.startsWith(interceptsRoot)) {
      logger.warn("Intercept path '{}' does not start with '{}'", fullPath, interceptsRoot);
      return null;
    }

    String remainder = fullPath.substring(interceptsRoot.length()); // "<peer>/<id>..." or "<peer>"
    String[] parts = remainder.split("/", -1);

    // 3) We care only about “…/intercepts/<peerUuid>/<interceptId>”
    if (parts.length < 2) { // event on the peer directory itself → ignore
      return null;
    }

    try {
      UUID peerUuid = UUID.fromString(parts[0]);
      String interceptId = parts[1];

      InterceptRequest<?> request = null;
      if (type == InterceptEvent.Type.INTERCEPT_ADDED) {
        byte[] bytes = event.getKeyValue().getValue().getBytes();
        request = InterceptRequest.fromBytes(bytes, UTF8);
      }

      return new InterceptEvent(type, fullPath, peerUuid, interceptId, request);

    } catch (IllegalArgumentException ex) { // malformed UUID or bad data
      logger.warn("Invalid intercept path '{}'", fullPath, ex);
      return null;
    }
  }

  /**
   * Consumes etcd watch responses and notifies intercept listeners of relevant events.
   *
   * @param watchResponse the etcd watch response containing events
   */
  private void interceptEventConsumer(WatchResponse watchResponse) {
    for (WatchEvent event : watchResponse.getEvents()) {
      switch (event.getEventType()) {
        case PUT, DELETE -> {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "New intercept event {} -> key:{} - value:{}",
                event.getEventType().name(),
                event.getKeyValue().getKey().toString(),
                event.getKeyValue().getValue().toString());
          }
          final InterceptEvent interceptEvent = createInterceptEvent(event);
          if (interceptEvent != null) {
            interceptListeners.forEach(l -> l.interceptEvent(interceptEvent));
          }
        }
        case UNRECOGNIZED ->
            logger.warn(
                "New intercepts UNRECOGNIZED event -> key:{} - value:{}",
                event.getKeyValue().getKey().toString(),
                event.getKeyValue().getValue().toString());
        default ->
            throw new IllegalStateException("Unexpected event type: " + event.getEventType());
      }
    }
  }

  /**
   * Creates an intercept; if ttlSeconds &gt; 0 a dedicated lease with that TTL is granted. If
   * ttlSeconds == 0, the intercept is attached to the peer’s live lease. If ttlSeconds == 0 and the
   * peer has no lease, the intercept is created without a lease.
   *
   * @param intercept the intercept request to create
   * @param ttlSeconds the value in seconds for the TTL assigned to this intercept; 0 == use the
   *     peer's lease.
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the associated peer does not exist in the directory
   * @throws IllegalArgumentException if the intercept request is already created for this peer
   */
  public void createIntercept(InterceptRequest<?> intercept, long ttlSeconds)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {

    UUID peerUuid = intercept.getPeer();
    UUID interceptUuid = intercept.getUuid();

    ByteSequence infoKey = peerInfoKey(peerUuid); // "/peers/<uuid>/info"
    String path = getInterceptsPathForPeer(peerUuid) + '/' + interceptUuid;
    ByteSequence intKey = ByteSequence.from(path, UTF8);
    ByteSequence intValue = ByteSequence.from(intercept.toBytes(UTF8));

    /* ---- decide which lease to use ---- */
    long leaseId =
        (ttlSeconds > 0)
            ? client.getLeaseClient().grant(ttlSeconds).get().getID()
            : currentPeerLeaseId(peerUuid); // helper (may return 0)

    Op.PutOp putOp;
    if (leaseId == 0 && ttlSeconds == 0) {
      putOp = Op.put(intKey, intValue, PutOption.DEFAULT);
    } else {
      putOp = Op.put(intKey, intValue, PutOption.builder().withLeaseId(leaseId).build());
    }

    /* ---- single Txn: peer must exist & intercept must be absent ---- */
    TxnResponse tx =
        kvClient
            .txn()
            .If(
                new Cmp(infoKey, Cmp.Op.GREATER, CmpTarget.version(0)), // peer exists
                new Cmp(intKey, Cmp.Op.EQUAL, CmpTarget.version(0))) // intercept absent
            .Then(putOp)
            .commit()
            .get();

    if (!tx.isSucceeded()) {
      // classify failure with one extra read *only when needed*
      if (kvClient.get(infoKey).get().getCount() == 0) {
        throw new NoPeerInfoNodeException("Peer " + peerUuid + " does not exist");
      } else {
        throw new IllegalArgumentException(
            "Intercept " + interceptUuid + " already exists for peer " + peerUuid);
      }
    }

    String leaseDesc =
        (ttlSeconds > 0)
            ? "dedicated lease " + leaseId + " (TTL " + ttlSeconds + " s)"
            : (leaseId != 0) ? "peer lease " + leaseId : "no lease";

    logger.info("Created intercept {} for peer {} — {}", interceptUuid, peerUuid, leaseDesc);
  }

  /**
   * Creates a new intercept request in the directory, with no custom TTL. Use this method to create
   * an intercept that uses the peer lease.
   *
   * @param interceptRequest the intercept request to create
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the associated peer does not exist in the directory
   * @throws IllegalArgumentException if the intercept request is already created for this peer
   */
  public void createIntercept(InterceptRequest<?> interceptRequest)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    createIntercept(interceptRequest, 0);
  }

  /**
   * Retrieves all intercept requests associated with a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @return a {@link Set} of {@link InterceptRequest} associated with the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<InterceptRequest<?>> listInterceptsForPeer(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final ByteSequence peerInterceptsPathKey = ByteSequence.from(peerInterceptsPath.getBytes(UTF8));
    final GetResponse response =
        kvClient.get(peerInterceptsPathKey, GetOption.builder().isPrefix(true).build()).get();
    for (KeyValue kv : response.getKvs()) {
      final String interceptPath = kv.getKey().toString(UTF8);
      interceptRequests.add(getIntercept(interceptPath));
    }
    return interceptRequests;
  }

  /**
   * Retrieves all intercept requests in the directory.
   *
   * @return a {@link Set} of all {@link InterceptRequest} instances
   */
  public Set<InterceptRequest<?>> listAllIntercepts() {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    try {
      final GetResponse response =
          kvClient.get(getInterceptsPathKey(), GetOption.builder().isPrefix(true).build()).get();
      for (KeyValue kv : response.getKvs()) {
        final String interceptPath = kv.getKey().toString(UTF8);
        interceptRequests.add(getIntercept(interceptPath));
      }
    } catch (ExecutionException | InterruptedException e) {
      logger.error("Error getting all intercept requests", e);
    }
    return interceptRequests;
  }

  /**
   * Retrieves a specific intercept request based on its path.
   *
   * @param interceptPath the etcd path of the intercept request
   * @return the {@link InterceptRequest} corresponding to the path, or {@code null} if not found
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public InterceptRequest<?> getIntercept(String interceptPath)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    List<KeyValue> kvs =
        kvClient.get(ByteSequence.from(interceptPath.getBytes(UTF8))).get().getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    data = kvs.get(0).getValue().getBytes();
    return InterceptRequest.fromBytes(data, UTF8);
  }

  /**
   * Adds a listener for intercept node events.
   *
   * @param listener the {@link InterceptNodeListener} to add
   */
  public void addInterceptListener(InterceptNodeListener listener) {

    // with the first intercept listener, start the lazy interceptsClient
    if (interceptsWatcher == null) {
      // Save the watcher so we can close it explicitly during shutdown.
      this.interceptsWatcher =
          watchClient.watch(
              getInterceptsPathKey(),
              WatchOption.builder().isPrefix(true).build(),
              this::interceptEventConsumer);
    }

    interceptListeners.add(listener);
    if (logger.isDebugEnabled()) {
      logger.debug("Added intercept node listener of class: {}", listener.getClass().getName());
    }
  }

  /**
   * Deletes all intercept requests associated with a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void deleteInterceptsForPeer(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    if (logger.isDebugEnabled()) {
      logger.debug("Deleting all intercept requests for peer w/uuid: {}", peerUuid);
    }
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(peerInterceptsPath.getBytes(UTF8)),
                DeleteOption.builder().isPrefix(true).build())
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn("No intercept requests found for peer w/uuid: {}", peerUuid);
    } else {
      logger.info(
          "Deleted {} intercept request(s) for peer w/uuid: {}",
          deleteResponse.getDeleted(),
          peerUuid);
    }
  }

  /**
   * Deletes a specific intercept request associated with a peer.
   *
   * @param peerUuid the UUID of the peer
   * @param interceptRequestUuid the UUID of the intercept request to delete
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void deleteIntercept(UUID peerUuid, UUID interceptRequestUuid)
      throws ExecutionException, InterruptedException {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", peerInterceptsPath, interceptRequestUuid.toString())
                        .getBytes(UTF8)))
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn(
          "No intercept request w/uuid: {} found for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    } else {
      logger.info(
          "Deleted intercept request w/uuid: {} for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    }
  }

  // </editor-fold>

  // <editor-fold desc="Log methods">

  /**
   * Creates a new log in the directory. If the log already exists (key = name), creation is
   * skipped.
   *
   * <p>For Kafka logs, bootstrap servers must be provided. For Chronicle logs, bootstrap servers
   * should be null or empty.
   *
   * @param logInfo the information of the log to create
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalArgumentException if a Kafka log is missing bootstrap servers or a Chronicle log
   *     has bootstrap servers set
   */
  public void createLog(LogInfo logInfo) throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logInfo, "logInfo cannot be null");

    // Validate bootstrapServers based on log type
    if (logInfo.getLogType() == LogInfo.LogType.KAFKA) {
      Objects.requireNonNull(
          logInfo.getBootstrapServers(), "logInfo.bootstrapServers cannot be null for Kafka logs");
    } else if (logInfo.getLogType() == LogInfo.LogType.CHRONICLE) {
      if (logInfo.getBootstrapServers() != null && !logInfo.getBootstrapServers().isEmpty()) {
        throw new IllegalArgumentException(
            "logInfo.bootstrapServers must be null or empty for Chronicle logs");
      }
    }

    // Ensure UUID is set
    if (logInfo.getUuid() == null) {
      logInfo.setUuid(UUID.randomUUID());
    }

    // Ensure timestamps are set
    final Instant now = Instant.now();
    if (logInfo.getCTime() == null) {
      logInfo.setCtime(now.toEpochMilli());
    }
    if (logInfo.getMTime() == null) {
      logInfo.setMtime(now.toEpochMilli());
    }

    // Prepare keys and data for atomic transaction
    // Primary: /<ns>/logs/<uuid> → LogInfo JSON
    final ByteSequence logKey = ByteSequence.from(getLogPath(logInfo.getUuid()).getBytes(UTF8));
    final ByteSequence logData = ByteSequence.from(logInfo.toJson().getBytes(UTF8));

    // Secondary index: /<ns>/logs/by-name/<filename>/<uuid> → "" (marker)
    final ByteSequence byNameKey =
        ByteSequence.from(
            getLogByNameEntryPath(logInfo.getName(), logInfo.getUuid()).getBytes(UTF8));
    final ByteSequence byNameData = ByteSequence.from("", UTF8); // Empty marker

    // Check if this specific log already exists (same name + servers/path combination)
    if (logExists(logInfo.getUuid())) {
      logger.warn(
          "Log {} w/uuid {} already exists - skipping", logInfo.getName(), logInfo.getUuid());
      return;
    }

    // Check for duplicates: same name + same distinguishing characteristic
    List<LogInfo> existingLogs = getLogsInfoByName(logInfo.getName());
    for (LogInfo existing : existingLogs) {
      if (logInfo.getLogType() == LogInfo.LogType.KAFKA) {
        // For Kafka: check if name + bootstrapServers combination already exists
        if (logInfo.getBootstrapServers().equals(existing.getBootstrapServers())) {
          logger.warn(
              "Log {} with bootstrap servers {} already exists - skipping",
              logInfo.getName(),
              logInfo.getBootstrapServers());
          return;
        }
      } else if (logInfo.getLogType() == LogInfo.LogType.CHRONICLE) {
        // For Chronicle: check if full path (name field) already exists
        if (logInfo.getName().equals(existing.getName())) {
          logger.warn("Chronicle log with path {} already exists - skipping", logInfo.getName());
          return;
        }
      }
    }

    // Atomic transaction: create both primary and index entries
    TxnResponse response =
        kvClient
            .txn()
            .If(
                new Cmp(
                    byNameKey, Cmp.Op.EQUAL, CmpTarget.version(0))) // index entry must not exist
            .Then(
                Op.put(logKey, logData, PutOption.DEFAULT),
                Op.put(byNameKey, byNameData, PutOption.DEFAULT))
            .commit()
            .get();

    if (response.isSucceeded()) {
      logger.info("Created log {} w/uuid {}", logInfo.getName(), logInfo.getUuid());
    } else {
      logger.warn(
          "Log {} w/uuid {} already exists - skipping", logInfo.getName(), logInfo.getUuid());
    }
  }

  /**
   * Creates a new log with a specified name prefix and bootstrap servers. The log name is generated
   * by appending a monotonically increasing counter to the prefix.
   *
   * @param logNamePrefix the prefix for the log name
   * @param logServers the bootstrap servers for the new log
   * @return the newly created {@link LogInfo}
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo createAutoLog(String logNamePrefix, String logServers)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logNamePrefix, "logNamePrefix cannot be null");
    Objects.requireNonNull(logServers, "logServers cannot be null");

    // 1) Atomically increment /<ns>/logs/counters/<prefix>
    ByteSequence counterKey = ByteSequence.from(getLogCounterPath(logNamePrefix), UTF8);

    long nextIdx;
    while (true) {
      GetResponse resp = kvClient.get(counterKey).get(); // read current counter
      long current =
          resp.getCount() == 0 ? 0 : Long.parseLong(resp.getKvs().get(0).getValue().toString(UTF8));
      long version = resp.getCount() == 0 ? 0 : resp.getKvs().get(0).getVersion();
      nextIdx = current + 1;

      TxnResponse tx =
          kvClient
              .txn() // CAS on the version
              .If(new Cmp(counterKey, Cmp.Op.EQUAL, CmpTarget.version(version)))
              .Then(
                  Op.put(
                      counterKey,
                      ByteSequence.from(Long.toString(nextIdx), UTF8),
                      PutOption.DEFAULT))
              .commit()
              .get();

      if (tx.isSucceeded()) break; // someone else updated? retry
    }

    String logName = format("%s%010d", logNamePrefix, nextIdx);

    // 2) Create the actual log node (guaranteed unique now)
    return writeLogInfo(logName, logServers); // helper below
  }

  /**
   * Auxiliary method that writes the LogInfo JSON using the new storage pattern: primary key by
   * UUID and secondary index by name.
   *
   * @param logName the log name
   * @param logServers the bootstrap servers for the new log
   * @return the newly created {@link LogInfo}
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  private LogInfo writeLogInfo(String logName, String logServers)
      throws ExecutionException, InterruptedException {

    LogInfo info = new LogInfo(logName, logServers);
    info.setUuid(UUID.randomUUID());
    long now = Instant.now().toEpochMilli();
    info.setCtime(now);
    info.setMtime(now);

    // Primary: /<ns>/logs/<uuid> → LogInfo JSON
    ByteSequence logKey = ByteSequence.from(getLogPath(info.getUuid()), UTF8);
    ByteSequence logValue = ByteSequence.from(info.toJson(), UTF8);

    // Secondary index: /<ns>/logs/by-name/<filename>/<uuid> → "" (marker)
    ByteSequence byNameKey =
        ByteSequence.from(getLogByNameEntryPath(logName, info.getUuid()), UTF8);
    ByteSequence byNameValue = ByteSequence.from("", UTF8);

    // Write both entries (no need for CAS here since auto-log names are guaranteed unique by
    // counter)
    kvClient.put(logKey, logValue).get();
    kvClient.put(byNameKey, byNameValue).get();

    logger.info("Created new log {} (uuid={})", logName, info.getUuid());

    return info;
  }

  /**
   * Retrieves the information of a specific log by its UUID (direct lookup).
   *
   * @param logUuid the UUID of the log
   * @return the {@link LogInfo} of the log, or {@code null} if the log does not exist
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo getLogInfo(UUID logUuid) throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(ByteSequence.from(getLogPath(logUuid).getBytes(UTF8))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for log w/UUID: {} does not exist", logUuid);
      return null;
    }
    return LogInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(UTF8));
  }

  /**
   * Retrieves all logs with the specified name/path (via by-name index).
   *
   * <p>Multiple logs can have the same filename (basename) but differ by full path (Chronicle) or
   * bootstrap servers (Kafka).
   *
   * @param logName the full name/path of the log
   * @return a {@link List} of all {@link LogInfo} instances matching the name
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public List<LogInfo> getLogsInfoByName(String logName)
      throws ExecutionException, InterruptedException {
    // Step 1: Scan by-name index for all UUIDs with this filename
    final String filename = extractFilename(logName);
    final String byNamePrefixPath = getLogByNamePrefixPath(filename);
    final GetResponse byNameResponse =
        kvClient
            .get(
                ByteSequence.from(byNamePrefixPath.getBytes(UTF8)),
                GetOption.builder().isPrefix(true).build())
            .get();

    if (byNameResponse.getCount() == 0) {
      logger.debug("No logs found with filename: {}", filename);
      return List.of();
    }

    // Step 2: Extract UUIDs from keys and fetch primary entries
    List<LogInfo> logs = new ArrayList<>();
    for (KeyValue kv : byNameResponse.getKvs()) {
      String fullPath = kv.getKey().toString(UTF8);
      // Extract UUID from path: /<ns>/logs/by-name/<filename>/<uuid>
      String uuidStr = fullPath.substring(byNamePrefixPath.length());
      try {
        UUID logUuid = UUID.fromString(uuidStr);
        LogInfo logInfo = getLogInfo(logUuid);
        if (logInfo != null) {
          logs.add(logInfo);
        }
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid UUID in by-name index: {}", uuidStr);
      }
    }

    return logs;
  }

  /**
   * Retrieves the information of a specific log by its name (via by-name index).
   *
   * <p>If multiple logs match the name, throws {@link IllegalStateException}. For handling multiple
   * matches, use {@link #getLogsInfoByName(String)}.
   *
   * @param logName the name of the log
   * @return the {@link LogInfo} of the log, or {@code null} if the log does not exist
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalStateException if multiple logs match the name
   */
  public LogInfo getLogInfo(String logName) throws ExecutionException, InterruptedException {
    List<LogInfo> logs = getLogsInfoByName(logName);

    if (logs.isEmpty()) {
      logger.warn("No log found with name: {}", logName);
      return null;
    }

    if (logs.size() > 1) {
      throw new IllegalStateException(
          format(
              "Multiple logs (%d) found with filename '%s'. Use getLogsInfoByName() or specify by UUID. Matching logs: %s",
              logs.size(),
              extractFilename(logName),
              logs.stream().map(l -> l.getUuid().toString()).collect(Collectors.joining(", "))));
    }

    return logs.get(0);
  }

  /**
   * Checks if a log with the specified UUID exists in the directory (direct lookup).
   *
   * @param logUuid the UUID of the log to check
   * @return {@code true} if the log exists, {@code false} otherwise
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public boolean logExists(UUID logUuid) throws ExecutionException, InterruptedException {
    return kvClient.get(ByteSequence.from(getLogPath(logUuid).getBytes(UTF8))).get().getCount()
        != 0;
  }

  /**
   * Checks if any log with the specified name/filename exists in the directory (via by-name index).
   *
   * @param logName the name of the log to check
   * @return {@code true} if at least one log with the filename exists, {@code false} otherwise
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public boolean logExists(String logName) throws ExecutionException, InterruptedException {
    return !getLogsInfoByName(logName).isEmpty();
  }

  /**
   * Retrieves all logs that have filenames starting with the specified prefix (via by-name index
   * scan).
   *
   * @param filenamePrefix the prefix of the log filenames (not full paths) to retrieve
   * @return a {@link Set} of {@link LogInfo} matching the prefix
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<LogInfo> listLogsWithPrefix(String filenamePrefix)
      throws ExecutionException, InterruptedException {
    // Step 1: Prefix scan on by-name index: /<ns>/logs/by-name/<filenamePrefix>
    final String byNamePrefixPath =
        format("%s/%s/%s", getLogsPath(), LOGS_BY_NAME_DIR, filenamePrefix);
    final GetResponse byNameResponse =
        kvClient
            .get(
                ByteSequence.from(byNamePrefixPath.getBytes(UTF8)),
                GetOption.builder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();

    // Step 2: Extract UUIDs from keys and fetch primary entries
    final Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : byNameResponse.getKvs()) {
      String fullPath = kv.getKey().toString(UTF8);
      // Path format: /<ns>/logs/by-name/<filename>/<uuid>
      // Extract UUID: skip to last '/' and take remainder
      int lastSlash = fullPath.lastIndexOf('/');
      if (lastSlash != -1) {
        String uuidStr = fullPath.substring(lastSlash + 1);
        try {
          UUID logUuid = UUID.fromString(uuidStr);
          LogInfo logInfo = getLogInfo(logUuid);
          if (logInfo != null) {
            logs.add(logInfo);
          }
        } catch (IllegalArgumentException e) {
          logger.warn("Invalid UUID in by-name index: {}", uuidStr);
        }
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug("returning from listLogsWithPrefix: {}", logs);
    }
    return logs;
  }

  /**
   * Retrieves all logs in the directory (by scanning UUID-keyed primary entries).
   *
   * @return a {@link Set} of all {@link LogInfo} instances
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<LogInfo> listAllLogs() throws ExecutionException, InterruptedException {
    final String logsRoot = getLogsPath(); // "/<ns>/logs"
    final String logsPrefix = logsRoot + '/'; // "/<ns>/logs/"

    GetResponse resp =
        kvClient
            .get(
                ByteSequence.from(logsPrefix.getBytes(UTF8)),
                GetOption.builder().isPrefix(true).build())
            .get();

    Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : resp.getKvs()) {
      String fullPath = kv.getKey().toString(UTF8); // e.g. "/<ns>/logs/<uuid>"
      String remainder = fullPath.substring(logsPrefix.length());

      // Skip anything in subdirectories (by-name/, counters/, etc.)
      if (remainder.contains("/")) {
        continue;
      }

      // Parse UUID to verify this is a primary log entry
      try {
        UUID.fromString(remainder); // Validate it's a UUID
        logs.add(LogInfo.fromJson(kv.getValue().toString(UTF8)));
      } catch (IllegalArgumentException e) {
        // Not a UUID, skip (shouldn't happen with new storage pattern)
        logger.warn("Skipping non-UUID key in logs directory: {}", fullPath);
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from listAllLogs: {}", logs);
    }
    return logs;
  }

  /**
   * Retrieves the last log with the specified name prefix based on creation time (via by-name index
   * scan with client-side sorting).
   *
   * @param logNamePrefix the prefix of the log name
   * @return the last {@link LogInfo} with the specified prefix, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo getLatestLogWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    // Get all logs with the prefix and sort by creation time on the client side
    final Set<LogInfo> logsWithPrefix = listLogsWithPrefix(logNamePrefix);

    if (logsWithPrefix.isEmpty()) {
      return null;
    }

    // Find the log with the latest creation time
    LogInfo latest = null;
    for (LogInfo log : logsWithPrefix) {
      if (latest == null || log.getCTime().isAfter(latest.getCTime())) {
        latest = log;
      }
    }

    logger.info(
        "With prefix '{}' got latest: {}",
        logNamePrefix,
        latest != null ? latest.getName() : "null");
    return latest;
  }

  /**
   * Retrieves the count of logs that have names starting with the specified prefix.
   *
   * @param logNamePrefix the prefix of the log names to count
   * @return the number of logs matching the prefix
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public int countLogsWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    return listLogsWithPrefix(logNamePrefix).size();
  }

  /**
   * Deletes a specific log info node identified by its UUID (direct deletion). Ensures that no peer
   * is using the log before deletion.
   *
   * @param logUuid the UUID of the log to delete
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalArgumentException if a peer is currently using the log
   */
  public void deleteLog(UUID logUuid) throws ExecutionException, InterruptedException {
    ByteSequence logKey = ByteSequence.from(getLogPath(logUuid), UTF8);

    // Fetch the node once (for name + version token)
    GetResponse resp = kvClient.get(logKey).get();
    if (resp.getCount() == 0) {
      logger.warn("Cannot delete log with UUID '{}': node does not exist", logUuid);
      return;
    }
    LogInfo logInfo = LogInfo.fromJson(resp.getKvs().get(0).getValue().toString(UTF8));
    long version = resp.getKvs().get(0).getVersion();

    // Single call to discover *all* used logs
    if (collectUsedLogIds().contains(logInfo.getUuid())) {
      throw new IllegalArgumentException(
          "Cannot delete log with UUID '"
              + logUuid
              + "' (name: '"
              + logInfo.getName()
              + "'): it is in use by at least one peer");
    }

    // For UUID-based deletion, we need to delete both the primary entry and the by-name index
    ByteSequence byNameKey =
        ByteSequence.from(getLogByNameEntryPath(logInfo.getName(), logUuid), UTF8);

    // Compare-and-delete both entries atomically (optimistic-lock on primary version)
    TxnResponse tx =
        kvClient
            .txn()
            .If(new Cmp(logKey, Cmp.Op.EQUAL, CmpTarget.version(version)))
            .Then(
                Op.delete(logKey, DeleteOption.DEFAULT), Op.delete(byNameKey, DeleteOption.DEFAULT))
            .commit()
            .get();

    if (tx.isSucceeded()) {
      logger.info("Deleted log with UUID '{}' (name: '{}')", logUuid, logInfo.getName());
    } else {
      logger.warn("Failed to delete log with UUID '{}': node changed concurrently", logUuid);
    }
  }

  /**
   * Deletes a specific log info node identified by its name (via by-name index). Ensures that no
   * peer is using the log before deletion.
   *
   * <p>If multiple logs match the name, throws {@link IllegalStateException}.
   *
   * @param logName the name of the log to delete
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalArgumentException if a peer is currently using the log
   * @throws IllegalStateException if multiple logs match the name
   */
  public void deleteLog(String logName) throws ExecutionException, InterruptedException {
    // Step 1: Resolve name → UUIDs via by-name index
    List<LogInfo> logs = getLogsInfoByName(logName);

    if (logs.isEmpty()) {
      logger.warn("Cannot delete log '{}': no logs found with this name", logName);
      return;
    }

    if (logs.size() > 1) {
      throw new IllegalStateException(
          format(
              "Multiple logs (%d) found with filename '%s'. Specify by UUID. Matching logs: %s",
              logs.size(),
              extractFilename(logName),
              logs.stream().map(l -> l.getUuid().toString()).collect(Collectors.joining(", "))));
    }

    // Step 2: Delegate to UUID-based deletion (which handles both primary and index)
    deleteLog(logs.get(0).getUuid());
  }

  /**
   * Collects every log-UUID referenced by any peer under …/logs/source and …/logs/wal.
   *
   * @return the set of UUID's corresponding to all Source and Write-Ahead logs used by peers
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  private Set<UUID> collectUsedLogIds() throws ExecutionException, InterruptedException {

    // Prefix "…/peers/" (include trailing slash, so the root node itself is skipped)
    ByteSequence peersPrefix = ByteSequence.from((getPeersPath() + '/'), UTF8);

    GetResponse resp = kvClient.get(peersPrefix, GetOption.builder().isPrefix(true).build()).get();

    // Filter client-side for “…/logs/source” and “…/logs/wal”
    Set<UUID> usedLogs = new HashSet<>();
    for (KeyValue kv : resp.getKvs()) {
      String key = kv.getKey().toString(UTF8);
      if (key.endsWith("/logs/source") || key.endsWith("/logs/wal")) {
        usedLogs.add(UUID.fromString(kv.getValue().toString(UTF8)));
      }
    }
    return usedLogs;
  }

  /**
   * Deletes all log nodes that have names starting with the specified prefix (via by-name index
   * scan). Deletes both primary and index entries for each log.
   *
   * @param logNamePrefix the prefix of the log names to delete
   * @return the number of logs deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  long deleteLogsWithPrefix(String logNamePrefix) throws ExecutionException, InterruptedException {
    // Step 1: Scan by-name index to get all logs with the prefix
    final String byNamePrefixPath =
        format("%s/%s/%s", getLogsPath(), LOGS_BY_NAME_DIR, logNamePrefix);
    final GetResponse byNameResponse =
        kvClient
            .get(
                ByteSequence.from(byNamePrefixPath.getBytes(UTF8)),
                GetOption.builder().isPrefix(true).build())
            .get();

    if (byNameResponse.getCount() == 0) {
      logger.warn("No logs found with prefix '{}'", logNamePrefix);
      return 0;
    }

    // Step 2: Delete each log (both primary and index entries)
    long deletedCount = 0;
    for (KeyValue kv : byNameResponse.getKvs()) {
      String fullPath = kv.getKey().toString(UTF8);
      // Path format: /<ns>/logs/by-name/<filename>/<uuid>
      // Extract UUID from last segment
      int lastSlash = fullPath.lastIndexOf('/');
      if (lastSlash == -1) {
        logger.warn("Invalid by-name index path: {}", fullPath);
        continue;
      }

      String uuidStr = fullPath.substring(lastSlash + 1);
      try {
        UUID logUuid = UUID.fromString(uuidStr);

        // Delete both primary and by-name index entries
        final ByteSequence logKey = ByteSequence.from(getLogPath(logUuid), UTF8);
        final ByteSequence byNameKey = ByteSequence.from(fullPath, UTF8);

        // Use transaction to delete both atomically
        TxnResponse tx =
            kvClient
                .txn()
                .Then(
                    Op.delete(logKey, DeleteOption.DEFAULT),
                    Op.delete(byNameKey, DeleteOption.DEFAULT))
                .commit()
                .get();

        if (tx.isSucceeded()) {
          deletedCount++;
        }
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid UUID in by-name index: {}", uuidStr);
      }
    }

    if (deletedCount > 0) {
      logger.info("Deleted {} log(s) with filename prefix '{}'", deletedCount, logNamePrefix);
    } else {
      logger.warn("Failed to delete any logs with filename prefix '{}'", logNamePrefix);
    }

    return deletedCount;
  }

  /**
   * Deletes all log nodes except those specified in the exclusion set.
   *
   * @param excludeLogs a {@link Set} of log UUIDs to exclude from deletion; may be {@code null}
   * @return the number of log entries deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  long purgeLogsExcept(@Nullable Set<UUID> excludeLogs)
      throws ExecutionException, InterruptedException {
    /* Gather logs we *can* delete */
    List<LogInfo> toDelete;
    if (excludeLogs != null && !excludeLogs.isEmpty()) {
      toDelete = listAllLogs().stream().filter(l -> !excludeLogs.contains(l.getUuid())).toList();
    } else {
      toDelete = new ArrayList<>(listAllLogs());
    }

    if (toDelete.isEmpty()) {
      logger.warn("No logs deleted");
      return 0;
    }

    /* Build a single transaction with all delete operations (both primary and index) */
    Txn txn = kvClient.txn();

    // Add delete operations for each log (2 ops per log: primary + index)
    Op[] deleteOps = new Op[toDelete.size() * 2];
    for (int i = 0; i < toDelete.size(); i++) {
      LogInfo log = toDelete.get(i);
      // Primary: /<ns>/logs/<uuid>
      ByteSequence logKey = ByteSequence.from(getLogPath(log.getUuid()), UTF8);
      deleteOps[i * 2] = Op.delete(logKey, DeleteOption.DEFAULT);

      // Secondary index: /<ns>/logs/by-name/<filename>/<uuid>
      ByteSequence byNameKey =
          ByteSequence.from(getLogByNameEntryPath(log.getName(), log.getUuid()), UTF8);
      deleteOps[i * 2 + 1] = Op.delete(byNameKey, DeleteOption.DEFAULT);
    }

    // Execute all deletes in a single transaction
    TxnResponse tx = txn.Then(deleteOps).commit().get();

    long deleted = toDelete.size();
    if (!tx.isSucceeded()) {
      logger.warn("Transaction failed while deleting logs");
    } else {
      logger.info("Deleted {} log(s)", deleted);
    }
    return deleted;
  }

  // </editor-fold>

  // <editor-fold desc="Misc methods">

  /**
   * Closes the PalDirectory, releasing all resources including the etcd client and key-value
   * client.
   */
  public void close() {

    // Fast-path: already closed?
    if (!closed.compareAndSet(false, true)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Directory to {} already closed – skipping", directoryUrl);
      }
      return;
    }

    RuntimeException firstError = null;

    // 1) Stop watch(es) first so no more callbacks hit a closing gRPC loop.
    try {
      if (interceptsWatcher != null) {
        interceptsWatcher.close();
      }
    } catch (RuntimeException e) {
      firstError = e;
      logger.warn("Failed to close intercepts watcher", e);
    }

    // 2) Stop keep-alive executor *before* we close the etcd client
    try {
      leasePool.shutdownNow(); // cancels KA tasks immediately
      if (!leasePool.awaitTermination(5, TimeUnit.SECONDS) && logger.isDebugEnabled()) {
        logger.debug("leasePool did not terminate within 5 s");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      RuntimeException e = new RuntimeException("Interrupted while shutting down leasePool", ie);
      if (firstError == null) {
        firstError = e;
      } else {
        firstError.addSuppressed(e);
      }
    } catch (RuntimeException e) {
      if (firstError == null) {
        firstError = e;
      } else {
        firstError.addSuppressed(e);
      }
      logger.warn("Failed to shut down leasePool", e);
    }

    // 3) Child resources
    try {
      if (kvClient != null) {
        kvClient.close();
      }
    } catch (RuntimeException e) {
      if (firstError == null) {
        firstError = e;
      } else {
        firstError.addSuppressed(e);
      }
      logger.warn("Failed to close KV client", e);
    }

    // 4) Parent client
    try {
      if (client != null) {
        client.close(); // safe even if kvClient.close() already did it
      }
    } catch (RuntimeException e) {
      if (firstError == null) {
        firstError = e;
      } else {
        firstError.addSuppressed(e);
      }
      logger.warn("Failed to close etcd client", e);
    }

    // 5) Propagate aggregated failure (if any)
    if (firstError != null) {
      throw firstError; // AutoCloseable allows unchecked exceptions
    }

    logger.info("Closed Directory connection to {}", directoryUrl);
  }

  // </editor-fold>

  // <editor-fold desc="private helpers">

  /**
   * Retrieves the etcd path for a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer
   */
  private String getPeerPath(UUID peerUuid) {
    return format("%s/%s", getPeersPath(), peerUuid);
  }

  /**
   * Retrieves the etcd path for a peer's source log.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer's source log
   */
  private String getPeerSourceLogPath(UUID peerUuid) {
    return format("%s/logs/source", getPeerPath(peerUuid));
  }

  /**
   * Retrieves the etcd path for a peer's WAL log.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer's WAL
   */
  private String getPeerWALPath(UUID peerUuid) {
    return format("%s/logs/wal", getPeerPath(peerUuid));
  }

  /**
   * Retrieves the etcd path for a specific log by its UUID (primary key).
   *
   * @param logUuid the UUID of the log
   * @return the etcd path for the log
   */
  private String getLogPath(UUID logUuid) {
    return format("%s/%s", getLogsPath(), logUuid);
  }

  /**
   * Extracts the filename (basename) from a log name/path.
   *
   * <p>For Chronicle logs, the name is typically a full path like "/tmp/wal/mylog". For Kafka logs,
   * the name is typically just a simple name like "my-kafka-log". This method extracts the last
   * component of the path.
   *
   * @param logName the full log name/path
   * @return the filename (basename) portion
   */
  private String extractFilename(String logName) {
    if (logName == null || logName.isEmpty()) {
      return logName;
    }
    int lastSlash = logName.lastIndexOf('/');
    if (lastSlash == -1) {
      return logName; // No slash, already just a name
    }
    return logName.substring(lastSlash + 1);
  }

  /**
   * Retrieves the etcd path for a specific log name-to-UUID index entry.
   *
   * <p>The by-name index uses the filename (basename) as the key, allowing multiple logs with the
   * same filename but different full paths or bootstrap servers to coexist.
   *
   * @param logName the full name/path of the log
   * @param logUuid the UUID of the log
   * @return the etcd path for the log's name index entry
   */
  private String getLogByNameEntryPath(String logName, UUID logUuid) {
    String filename = extractFilename(logName);
    return format("%s/%s/%s/%s", getLogsPath(), LOGS_BY_NAME_DIR, filename, logUuid);
  }

  /**
   * Retrieves the etcd path prefix for all logs with the specified filename.
   *
   * @param filename the filename (basename) to search for
   * @return the etcd path prefix for the filename's index entries
   */
  private String getLogByNamePrefixPath(String filename) {
    return format("%s/%s/%s/", getLogsPath(), LOGS_BY_NAME_DIR, filename);
  }

  /**
   * Retrieves the etcd path for a log auto-naming counter.
   *
   * @param prefix the log name prefix
   * @return the etcd path for the counter
   */
  private String getLogCounterPath(String prefix) {
    return format("%s/%s/%s", getLogsPath(), LOGS_COUNTERS_DIR, prefix);
  }

  /**
   * Retrieves the base etcd path for all peers.
   *
   * @return the etcd path for peers
   */
  private String getPeersPath() {
    return format("/%s/%s", namespace, PEERS_DIR);
  }

  /**
   * Retrieves the etcd key sequence for the peers' path.
   *
   * @return the {@link ByteSequence} representing the peers path key
   */
  private ByteSequence getPeersPathKey() {
    return ByteSequence.from(getPeersPath().getBytes(UTF8));
  }

  /**
   * Retrieves the base etcd path for all logs.
   *
   * @return the etcd path for logs
   */
  private String getLogsPath() {
    return format("/%s/%s", namespace, LOGS_DIR);
  }

  /**
   * Retrieves the base etcd path for all intercepts.
   *
   * @return the etcd path for intercepts
   */
  private String getInterceptsPath() {
    return format("/%s/%s", namespace, INTERCEPTS_DIR);
  }

  /**
   * Retrieves the etcd key sequence for the intercepts' path.
   *
   * @return the {@link ByteSequence} representing the intercepts path key
   */
  private ByteSequence getInterceptsPathKey() {
    return ByteSequence.from(getInterceptsPath().getBytes(UTF8));
  }

  /**
   * Retrieves the etcd path for intercepts associated with a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer's intercepts
   */
  private String getInterceptsPathForPeer(UUID peerUuid) {
    return format("%s/%s", getInterceptsPath(), peerUuid);
  }

  /**
   * Converts an {@link OffsetDateTime} to the number of milliseconds since the epoch.
   *
   * @param odt the {@code OffsetDateTime} to convert; may be {@code null}
   * @return the epoch millisecond value, or {@code 0L} if {@code odt} is {@code null}
   */
  private static long toMillis(OffsetDateTime odt) {
    return odt == null ? 0L : odt.toInstant().toEpochMilli();
  }

  /**
   * Builds the etcd key path for a peer’s info node.
   *
   * @param uuid the UUID of the peer
   * @return the string path under which the peer’s info is stored (i.e. "{@code
   *     /<peers-path>/<uuid>/info}")
   */
  private String getPeerInfoPath(UUID uuid) {
    return getPeerPath(uuid) + "/info";
  }

  /**
   * Builds the etcd key path for a peer’s state node.
   *
   * @param uuid the UUID of the peer
   * @return the string path under which the peer’s state is stored (i.e. "{@code
   *     /<peers-path>/<uuid>/state}")
   */
  private String getPeerStatePath(UUID uuid) {
    return getPeerPath(uuid) + "/state";
  }

  /**
   * Creates a {@link ByteSequence} for the peer info key, using UTF-8 encoding.
   *
   * @param uuid the UUID of the peer
   * @return a {@code ByteSequence} representing the key for the peer’s info node
   */
  private ByteSequence peerInfoKey(UUID uuid) {
    return ByteSequence.from(getPeerInfoPath(uuid), UTF8);
  }

  /**
   * Creates a {@link ByteSequence} for the peer state key, using UTF-8 encoding.
   *
   * @param uuid the UUID of the peer
   * @return a {@code ByteSequence} representing the key for the peer’s state node
   */
  private ByteSequence peerStateKey(UUID uuid) {
    return ByteSequence.from(getPeerStatePath(uuid), UTF8);
  }

  /**
   * Get the lease ID for the given peer. This method first queries a cache; in case of a miss, it
   * looks up the lease of the peer's /state
   *
   * @param peerUuid the ID of the peer with an assigned lease
   * @return the leaseId set on the peer's /state node, 0 if none
   * @throws ExecutionException on etcd errors
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  private long currentPeerLeaseId(UUID peerUuid) throws ExecutionException, InterruptedException {

    Long leaseId = peerToLeaseIdCache.get(peerUuid);
    if (leaseId == null) {
      // should not happen, but we can fall back to looking up the lease in the peer '/state'
      GetResponse resp = kvClient.get(peerStateKey(peerUuid)).get();
      return resp.getCount() == 0 ? 0 : resp.getKvs().get(0).getLease();
    }
    // cache hit
    return leaseId;
  }

  /**
   * Checks if a peer has an active (non-expired) lease. A peer with an active lease is considered
   * "alive" and should not be removed under normal circumstances.
   *
   * @param peerUuid the UUID of the peer to check
   * @return true if the peer has an active lease with TTL &gt; 0, false otherwise
   * @throws ExecutionException on etcd errors
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public boolean isPeerAlive(UUID peerUuid) throws ExecutionException, InterruptedException {
    // First check if the peer's /state key still exists with a lease
    GetResponse resp = kvClient.get(peerStateKey(peerUuid)).get();
    if (resp.getCount() == 0) {
      // No /state key means peer doesn't exist or has been removed
      return false;
    }

    long leaseId = resp.getKvs().get(0).getLease();
    if (leaseId == 0) {
      // No lease attached means peer is not alive
      return false;
    }

    // The peer has a lease attached; verify it's still active
    // If the lease expired, etcd would have already deleted the /state key,
    // so if we got here, the lease is active
    return true;
  }

  // </editor-fold>

  // <editor-fold desc="private records">

  /**
   * Holds the immutable static metadata for a peer.
   *
   * @param uuid the unique identifier of the peer
   * @param name the human-readable name of the peer
   * @param ctimeMillis the creation time in epoch milliseconds
   */
  private record PeerStatic(UUID uuid, String name, long ctimeMillis) {

    /**
     * Creates a new {@code PeerStatic} instance from the given {@link PeerInfo}.
     *
     * @param p the source peer info
     * @return a {@code PeerStatic} containing the peer’s UUID, name, and creation timestamp
     */
    static PeerStatic from(PeerInfo p) {
      return new PeerStatic(p.getUuid(), p.getName(), toMillis(p.getCTime()));
    }
  }

  /**
   * Holds the mutable state information for a peer.
   *
   * @param mtimeMillis the last-modified time in epoch milliseconds
   * @param zmqRpc the ZMQ RPC endpoint address
   * @param jsonRpc the JSON-RPC endpoint address
   * @param pub the publish-subscribe endpoint address
   * @param jmx the JMX management endpoint address
   */
  private record PeerState(
      long mtimeMillis, String zmqRpc, String jsonRpc, String pub, String jmx) {

    /**
     * Creates a new {@code PeerState} instance from the given {@link PeerInfo}.
     *
     * @param p the source peer info
     * @return a {@code PeerState} containing the peer’s mutable endpoint addresses and modification
     *     timestamp
     */
    static PeerState from(PeerInfo p) {
      return new PeerState(
          toMillis(p.getMTime()),
          p.getZmqRpcAddress(),
          p.getJsonrpcAddress(),
          p.getPubAddress(),
          p.getJmxAddress());
    }
  }
  // </editor-fold>
}
