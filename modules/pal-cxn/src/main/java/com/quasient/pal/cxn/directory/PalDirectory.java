/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

import static java.lang.String.format;

import com.google.gson.Gson;
import com.quasient.pal.common.directory.events.InterceptEvent;
import com.quasient.pal.common.directory.events.InterceptNodeListener;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
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
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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
public class PalDirectory implements AutoCloseable {

  /** Logger instance for PalDirectory to log operations. */
  private static final Logger logger = LoggerFactory.getLogger(PalDirectory.class);

  /** Duration specifying the interval between keepalive messages to the etcd client. */
  private static final Duration ETCD_KEEP_ALIVE_TIME = Duration.ofSeconds(60);

  /** Duration specifying the timeout for etcd client keepalive messages. */
  private static final Duration ETCD_KEEP_ALIVE_TIMEOUT = Duration.ofSeconds(20);

  /** Constant indicating that no directory URL has been provided. */
  public static final String NO_URL = "<none>";

  /** Default namespace used for organizing directory entries. */
  private static final String DEFAULT_PAL_NAMESPACE = "pal";

  /** Directory name for storing peer information. */
  private static final String PEERS_DIR = "peers";

  /** Directory name for storing log information. */
  private static final String LOGS_DIR = "logs";

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
    this(connectionString, null, false);
  }

  /**
   * Constructs a PalDirectory instance with the specified etcd connection string and blocking
   * behavior.
   *
   * @param connectionString the etcd connection string in the format "host:port"
   * @param blocking if true, the constructor will block until a connection to etcd is established
   */
  public PalDirectory(String connectionString, boolean blocking) {
    this(connectionString, null, blocking);
  }

  /**
   * Constructs a PalDirectory instance with a list of etcd endpoint URIs.
   *
   * @param endpoints list of etcd endpoint URIs
   */
  public PalDirectory(List<URI> endpoints) {
    this(endpoints.stream().map(URI::toString).collect(Collectors.joining(",")), null);
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
    this.directoryUrl = endpoints;
    logger.info("Will connect to etcd endpoints: {}", endpoints);
    this.client =
        Client.builder()
            .target(endpoints)
            .keepaliveTime(ETCD_KEEP_ALIVE_TIME)
            .keepaliveTimeout(ETCD_KEEP_ALIVE_TIMEOUT)
            .keepaliveWithoutCalls(false)
            .build();

    if (blocking) {
      // perform a status check to block until connected
      try {
        // block until the etcd cluster responds with status
        StatusResponse status = client.getMaintenanceClient().statusMember(endpoints).get();
        logger.info("Connected to etcd cluster: {}", status);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        throw new RuntimeException("Thread was interrupted while connecting to etcd", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Failed to connect to etcd cluster", e.getCause());
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
    Watch watchClient = client.getWatchClient();
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;

    watchClient.watch(
        getInterceptsPathKey(),
        WatchOption.builder().isPrefix(true).build(),
        this::interceptEventConsumer);
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
   * Deletes all peer nodes except those specified in the exclusion set.
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
    }
    if (deleted == 0) {
      logger.warn("No peers found to delete");
    } else {
      logger.info("Deleted {} peers", deleted);
    }
    return deleted;
  }

  /**
   * Deletes all peer nodes in the directory.
   *
   * @return the number of peers deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public long deletePeers() throws ExecutionException, InterruptedException {
    return purgePeersExcept(null);
  }

  /**
   * Deletes a peer subtree atomically (root + logs + state).
   *
   * @param peerUuid the peer’s UUID
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
   * @param logInfo the information of the log to create
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void createLog(LogInfo logInfo) throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logInfo, "logInfo cannot be null");
    Objects.requireNonNull(
        logInfo.getBootstrapServers(), "logInfo.bootstrapServers cannot be null");

    if (logInfo.getUuid() == null) {
      logInfo.setUuid(UUID.randomUUID());
    }
    final Instant now = Instant.now();
    if (logInfo.getCTime() == null) {
      logInfo.setCtime(now.toEpochMilli());
    }
    if (logInfo.getMTime() == null) {
      logInfo.setMtime(now.toEpochMilli());
    }
    final ByteSequence logKey = ByteSequence.from(getLogPath(logInfo.getName()).getBytes(UTF8));
    final ByteSequence logData = ByteSequence.from(logInfo.toJson().getBytes(UTF8));

    TxnResponse response =
        kvClient
            .txn()
            .If(new Cmp(logKey, Cmp.Op.EQUAL, CmpTarget.version(0))) // key must not exist
            .Then(Op.put(logKey, logData, PutOption.DEFAULT))
            .commit()
            .get();

    if (response.isSucceeded()) {
      logger.info("Created log {} w/uuid {}", logInfo.getName(), logInfo.getUuid());
    } else {
      logger.warn("Log {} already exists - skipping", logInfo.getName());
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
    ByteSequence counterKey =
        ByteSequence.from(String.format("%s/counters/%s", getLogsPath(), logNamePrefix), UTF8);

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

    String logName = String.format("%s%010d", logNamePrefix, nextIdx);

    // 2) Create the actual log node (guaranteed unique now)
    return writeLogInfo(logName, logServers); // helper below
  }

  /**
   * Auxiliary method that writes the LogInfo JSON under “…/logs/<name>”.
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

    ByteSequence logKey = ByteSequence.from(getLogPath(logName), UTF8);
    ByteSequence logValue = ByteSequence.from(info.toJson(), UTF8);

    kvClient.put(logKey, logValue).get();
    logger.info("Created new log {} (uuid={})", logName, info.getUuid());

    return info;
  }

  /**
   * Retrieves the information of a specific log by its name.
   *
   * @param logName the name of the log
   * @return the {@link LogInfo} of the log, or {@code null} if the log does not exist
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo getLogInfo(String logName) throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(ByteSequence.from(getLogPath(logName).getBytes(UTF8))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for log w/name: {} does not exist", logName);
      return null;
    }
    return LogInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(UTF8));
  }

  /**
   * Checks if a log with the specified name exists in the directory.
   *
   * @param logName the name of the log to check
   * @return {@code true} if the log exists, {@code false} otherwise
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public boolean logExists(String logName) throws ExecutionException, InterruptedException {
    return kvClient.get(ByteSequence.from(getLogPath(logName).getBytes(UTF8))).get().getCount()
        != 0;
  }

  /**
   * Retrieves all logs that have names starting with the specified prefix.
   *
   * @param logNamePrefix the prefix of the log names to retrieve
   * @return a {@link Set} of {@link LogInfo} matching the prefix
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<LogInfo> listLogsWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient
            .get(
                ByteSequence.from(format("%s/%s", getLogsPath(), logNamePrefix).getBytes(UTF8)),
                GetOption.builder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      logs.add(LogInfo.fromJson(kv.getValue().toString(UTF8)));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from listLogsWithPrefix: {}", logs);
    }
    return logs;
  }

  /**
   * Retrieves all logs in the directory.
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
      String fullPath = kv.getKey().toString(UTF8); // e.g. "/<ns>/logs/app0000000001"
      String remainder = fullPath.substring(logsPrefix.length());

      // skip anything in subdirectories such as "counters/<prefix>"
      if (remainder.contains("/")) {
        continue;
      }
      logs.add(LogInfo.fromJson(kv.getValue().toString(UTF8)));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from listAllLogs: {}", logs);
    }
    return logs;
  }

  /**
   * Retrieves the last log with the specified name prefix based on creation time.
   *
   * @param logNamePrefix the prefix of the log name
   * @return the last {@link LogInfo} with the specified prefix, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo getLatestLogWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {

    GetResponse resp =
        kvClient
            .get(
                ByteSequence.from(
                    String.format("%s/%s", getLogsPath(), logNamePrefix).getBytes(UTF8)),
                GetOption.builder()
                    .withSortField(GetOption.SortTarget.KEY)
                    .withSortOrder(GetOption.SortOrder.DESCEND)
                    .withLimit(1)
                    .isPrefix(true)
                    .build())
            .get();
    if (resp.getCount() == 0) {
      return null;
    }
    LogInfo last = LogInfo.fromJson(resp.getKvs().get(0).getValue().toString(UTF8));
    logger.info("With prefix '{}' got {}", logNamePrefix, last.getName());
    return last;
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
   * Deletes a specific log info node identified by its name. Ensures that no peer is using the log
   * before deletion.
   *
   * @param logName the name of the log to delete
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalArgumentException if a peer is currently using the log
   */
  public void deleteLog(String logName) throws ExecutionException, InterruptedException {

    ByteSequence logKey = ByteSequence.from(getLogPath(logName), UTF8);

    // Fetch the node once (for UUID + version token)
    GetResponse resp = kvClient.get(logKey).get();
    if (resp.getCount() == 0) {
      logger.warn("Cannot delete log '{}': node does not exist", logName);
      return;
    }
    LogInfo logInfo = LogInfo.fromJson(resp.getKvs().get(0).getValue().toString(UTF8));
    long version = resp.getKvs().get(0).getVersion();

    // Single call to discover *all* used logs
    if (collectUsedLogIds().contains(logInfo.getUuid())) {
      throw new IllegalArgumentException(
          "Cannot delete log '" + logName + "': it is in use by at least one peer");
    }

    // Compare-and-delete (optimistic-lock on version)
    TxnResponse tx =
        kvClient
            .txn()
            .If(new Cmp(logKey, Cmp.Op.EQUAL, CmpTarget.version(version)))
            .Then(Op.delete(logKey, DeleteOption.DEFAULT))
            .commit()
            .get();

    if (tx.isSucceeded()) {
      logger.info("Deleted log '{}'", logName);
    } else {
      logger.warn("Failed to delete log '{}': node changed concurrently", logName);
    }
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
   * Deletes all log nodes that have names starting with the specified prefix.
   *
   * @param logNamePrefix the prefix of the log names to delete
   * @return the number of logs deleted
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  long deleteLogsWithPrefix(String logNamePrefix) throws ExecutionException, InterruptedException {

    ByteSequence prefixKey =
        ByteSequence.from(String.format("%s/%s", getLogsPath(), logNamePrefix), UTF8);

    DeleteResponse del =
        kvClient.delete(prefixKey, DeleteOption.builder().isPrefix(true).build()).get();

    if (del.getDeleted() == 0) {
      logger.warn("No logs found with prefix '{}'", logNamePrefix);
    } else {
      logger.info("Deleted {} log(s) with prefix '{}'", del.getDeleted(), logNamePrefix);
    }
    return del.getDeleted();
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
    long deleted = 0;

    if (excludeLogs != null && !excludeLogs.isEmpty()) {
      /* Gather logs we *can* delete */
      List<LogInfo> toDelete =
          listAllLogs().stream().filter(l -> !excludeLogs.contains(l.getUuid())).toList();

      /* One Txn per key (keeps code simple & safe) */
      for (LogInfo log : toDelete) {
        deleteLog(log.getName()); // re-use the atomic delete above
        deleted++;
      }
    } else {
      /* Fast-path: wipe the entire logs subtree */
      DeleteResponse del =
          kvClient.delete(getLogsPathKey(), DeleteOption.builder().isPrefix(true).build()).get();

      deleted = del.getDeleted();
    }

    if (deleted == 0) {
      logger.warn("No logs deleted");
    } else {
      logger.info("Deleted {} log(s)", deleted);
    }
    return deleted;
  }

  // </editor-fold>

  // <editor-fold desc="Misc methods">

  /**
   * {@inheritDoc}
   *
   * <p>Closes the PalDirectory, releasing all resources including the etcd client and key-value
   * client.
   */
  @Override
  public void close() {

    // Fast-path: already closed?
    if (!closed.compareAndSet(false, true)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Directory to {} already closed – skipping", directoryUrl);
      }
      return;
    }

    RuntimeException firstError = null;

    // 1) Stop keep-alive executor *before* we close the etcd client
    try {
      leasePool.shutdownNow(); // cancels KA tasks immediately
      if (!leasePool.awaitTermination(5, TimeUnit.SECONDS) && logger.isDebugEnabled()) {
        logger.debug("leasePool did not terminate within 5 s");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      firstError = new RuntimeException("Interrupted while shutting down leasePool", ie);
    } catch (RuntimeException e) {
      firstError = e;
      logger.warn("Failed to shut down leasePool", e);
    }

    // 2) Child resources
    try {
      if (kvClient != null) {
        kvClient.close();
      }
    } catch (RuntimeException e) {
      if (firstError != null) {
        firstError.addSuppressed(e);
      } else {
        firstError = e;
      }
      logger.warn("Failed to close KV client", e);
    }

    // 3) Parent client
    try {
      if (client != null) {
        client.close(); // safe even if kvClient.close() already did it
      }
    } catch (RuntimeException e) {
      if (firstError != null) {
        firstError.addSuppressed(e);
      } else {
        firstError = e;
      }
      logger.warn("Failed to close etcd client", e);
    }

    // 4) Propagate aggregated failure (if any)
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
   * Retrieves the etcd path for a specific log.
   *
   * @param logName the name of the log
   * @return the etcd path for the log
   */
  private String getLogPath(String logName) {
    return format("%s/%s", getLogsPath(), logName);
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
   * Retrieves the etcd key sequence for the logs' path.
   *
   * @return the {@link ByteSequence} representing the logs path key
   */
  private ByteSequence getLogsPathKey() {
    return ByteSequence.from(getLogsPath().getBytes(UTF8));
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
