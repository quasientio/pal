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

package net.ittera.pal.cxn;

import static java.lang.String.format;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.maintenance.StatusResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.util.Strings;
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

  /** Character set used for encoding/decoding strings. Initialized lazily to UTF-8. */
  private static Charset loadedCharset;

  /** URL of the etcd directory service. */
  private final String directoryUrl;

  /** Etcd client used for interacting with the etcd service. */
  private final Client client;

  /** Key-Value client for etcd operations. */
  private final KV kvClient;

  /** Namespace used within etcd for organizing entries. */
  private final String namespace;

  /** Listeners subscribed to intercept node events. */
  private final List<InterceptNodeListener> interceptListeners = new ArrayList<>();

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

  /**
   * Retrieves the URL of the etcd directory service.
   *
   * @return the directory URL
   */
  public String getDirectoryUrl() {
    return directoryUrl;
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
    return kvClient
            .get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())))
            .get()
            .getCount()
        != 0;
  }

  /**
   * Registers a new peer in the directory. If the peer already exists, registration is skipped.
   *
   * @param peerInfo the information of the peer to register
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void registerPeer(PeerInfo peerInfo) throws ExecutionException, InterruptedException {
    if (peerExists(peerInfo.getUuid())) {
      logger.warn("Skipping registration of existing peer with uuid: {}", peerInfo.getUuid());
      return;
    }
    final Instant now = Instant.now();
    if (peerInfo.getCTime() == null) {
      peerInfo.setCtime(now.toEpochMilli());
    }
    if (peerInfo.getMTime() == null) {
      peerInfo.setMtime(now.toEpochMilli());
    }
    final ByteSequence peerKey =
        ByteSequence.from(getPeerPath(peerInfo.getUuid()).getBytes(getEncodingCharset()));
    final ByteSequence peerData =
        ByteSequence.from(peerInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(peerKey, peerData).get();
    logger.info("Registered peer w/uuid: {}, {}", peerInfo.getUuid(), peerInfo);
  }

  /**
   * Registers an incoming log for the specified peer.
   *
   * @param peerInfo the information of the peer
   * @param logInfo the log information to register as incoming for the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the peer does not exist in the directory
   */
  public void registerPeerInLog(PeerInfo peerInfo, LogInfo logInfo)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(peerInfo.getUuid())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", peerInfo.getUuid()));
    }
    final String peerLogsInPath = getPeerLogsInPath(peerInfo.getUuid());
    final ByteSequence peerLogsInPathKey =
        ByteSequence.from(peerLogsInPath.getBytes(getEncodingCharset()));
    final ByteSequence logData =
        ByteSequence.from(logInfo.getUuid().toString().getBytes(getEncodingCharset()));
    kvClient.put(peerLogsInPathKey, logData).get();
    logger.info(
        "Registered IN log w/name: {} for peer w/uuid: {}", logInfo.getName(), peerInfo.getUuid());
  }

  /**
   * Retrieves the UUID of the incoming log associated with the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the UUID of the incoming log, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalStateException if multiple incoming logs are found for the peer
   */
  public UUID getPeerInLog(UUID peerUuid) throws ExecutionException, InterruptedException {
    final String peerLogsInPath = getPeerLogsInPath(peerUuid);
    final ByteSequence peerLogsInPathKey =
        ByteSequence.from(peerLogsInPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerLogsInPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException("More than one in-log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(getEncodingCharset()));
  }

  /**
   * Registers an outgoing log for the specified peer.
   *
   * @param peerInfo the information of the peer
   * @param logInfo the log information to register as outgoing for the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the peer does not exist in the directory
   */
  public void registerPeerOutLog(PeerInfo peerInfo, LogInfo logInfo)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(peerInfo.getUuid())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", peerInfo.getUuid()));
    }
    final String peerLogsOutPath = getPeerLogsOutPath(peerInfo.getUuid());
    final ByteSequence peerLogsOutPathKey =
        ByteSequence.from(peerLogsOutPath.getBytes(getEncodingCharset()));
    final ByteSequence logData =
        ByteSequence.from(logInfo.getUuid().toString().getBytes(getEncodingCharset()));
    kvClient.put(peerLogsOutPathKey, logData).get();
    logger.info(
        "Registered OUT log w/name: {} for peer w/uuid: {}", logInfo.getName(), peerInfo.getUuid());
  }

  /**
   * Retrieves the UUID of the outgoing log associated with the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the UUID of the outgoing log, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalStateException if multiple outgoing logs are found for the peer
   */
  public UUID getPeerOutLog(UUID peerUuid) throws ExecutionException, InterruptedException {
    final String peerLogsOutPath = getPeerLogsOutPath(peerUuid);
    final ByteSequence peerLogsOutPathKey =
        ByteSequence.from(peerLogsOutPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerLogsOutPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException("More than one out-log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(getEncodingCharset()));
  }

  /**
   * Retrieves the information of the specified peer.
   *
   * @param peerUuid the UUID of the peer
   * @return the {@link PeerInfo} of the peer, or {@code null} if the peer does not exist
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public PeerInfo getPeerInfo(UUID peerUuid) throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset()))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for peer w/uuid: {} does not exist", peerUuid);
      return null;
    }
    return PeerInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(getEncodingCharset()));
  }

  /**
   * Retrieves all registered peers in the directory.
   *
   * @return a {@link Set} of {@link PeerInfo} representing all peers
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<PeerInfo> getAllPeers() throws ExecutionException, InterruptedException {
    final GetResponse response =
        kvClient.get(getPeersPathKey(), GetOption.builder().isPrefix(true).build()).get();
    final Set<PeerInfo> allPeers = new TreeSet<>();
    for (KeyValue kv : response.getKvs()) {
      // skip root peers path
      if (kv.getKey().equals(getPeersPathKey())) {
        continue;
      }
      // only add peers, not logs inside the peer's path
      String keyPath = kv.getKey().toString(getEncodingCharset());
      long slashCount = keyPath.chars().filter(ch -> ch == '/').count();
      if (slashCount == 3) {
        allPeers.add(PeerInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
      }
    }
    return allPeers;
  }

  /**
   * Unregisters all peers except those specified in the exclusion set.
   *
   * @param excludePeers a {@link Set} of peer UUIDs to exclude from unregistration; may be {@code
   *     null}
   * @return the number of peers unregistered
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public long unregisterAllPeersWithExcludes(@Nullable Set<UUID> excludePeers)
      throws ExecutionException, InterruptedException {
    long deleted = 0;
    if (excludePeers != null && !excludePeers.isEmpty()) {
      for (UUID peerUuid :
          getAllPeers().stream().map(PeerInfo::getUuid).collect(Collectors.toSet())) {
        if (!excludePeers.contains(peerUuid)) {
          unregisterPeer(peerUuid);
          deleted++;
        }
      }
    } else {
      final DeleteResponse deleteResponse =
          kvClient.delete(getPeersPathKey(), DeleteOption.builder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();
    }
    if (deleted == 0) {
      logger.warn("No peers found to unregister");
    } else {
      logger.info("Unregistered {} peers", deleted);
    }
    return deleted;
  }

  /**
   * Unregisters all peers in the directory.
   *
   * @return the number of peers unregistered
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public long unregisterAllPeers() throws ExecutionException, InterruptedException {
    return unregisterAllPeersWithExcludes(null);
  }

  /**
   * Unregisters a specific peer identified by its UUID.
   *
   * @param peerUuid the UUID of the peer to unregister
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void unregisterPeer(UUID peerUuid) throws ExecutionException, InterruptedException {
    DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();
    if (deleteResponse.getDeleted() == 1) {
      logger.info("Unregistered peer with uuid: {}", peerUuid);
    } else {
      logger.warn("Could not unregister peer with uuid: {}, peer does not exist.", peerUuid);
    }
  }

  // </editor-fold>

  // <editor-fold desc="Intercept request methods">

  /**
   * Creates an {@link InterceptEvent} based on the received etcd watch event.
   *
   * @param event the etcd watch event
   * @return the constructed {@link InterceptEvent}, or {@code null} if the event type is unexpected
   *     or invalid
   */
  private InterceptEvent createInterceptEvent(WatchEvent event) {
    final InterceptEvent.Type type;
    switch (event.getEventType()) {
      case PUT:
        type = InterceptEvent.Type.INTERCEPT_ADDED;
        break;
      case DELETE:
        type = InterceptEvent.Type.INTERCEPT_REMOVED;
        break;
      default:
        logger.error("Unexpected event of type: {}", event.getEventType().name());
        return null;
    }
    String path = event.getKeyValue().getKey().toString(getEncodingCharset());
    String[] parts =
        Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    if (parts.length == 4) {
      try {
        final UUID peerUuid = UUID.fromString(parts[2]);
        final String interceptId = parts[3];
        final byte[] data = event.getKeyValue().getValue().getBytes();
        final InterceptRequest<?> interceptRequest;
        logger.debug(
            "Creating intercept event from path: '{}' with value: '{}'",
            path,
            new String(data, getEncodingCharset()));
        if (type == InterceptEvent.Type.INTERCEPT_ADDED) {
          interceptRequest = InterceptRequest.fromBytes(data, getEncodingCharset());
        } else {
          interceptRequest = null;
        }
        return new InterceptEvent(type, path, peerUuid, interceptId, interceptRequest);
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid UUID or unexpected path of len=4: {}", path, e);
      }
    }
    return null;
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
   * Registers a new intercept request in the directory.
   *
   * @param interceptRequest the intercept request to register
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws NoPeerInfoNodeException if the associated peer does not exist in the directory
   */
  public void registerIntercept(InterceptRequest<?> interceptRequest)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(interceptRequest.getPeer())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", interceptRequest.getPeer()));
    }
    final byte[] interceptData = interceptRequest.toBytes(getEncodingCharset());
    final String interceptPath =
        format(
            "%s/%s",
            getInterceptsPathForPeer(interceptRequest.getPeer()), interceptRequest.getUuid());
    kvClient
        .put(
            ByteSequence.from(interceptPath.getBytes(getEncodingCharset())),
            ByteSequence.from(interceptData))
        .get();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "created new node for intercept request: {} at path: {}",
          interceptRequest,
          interceptPath);
    }
  }

  /**
   * Asynchronously registers a new intercept request in the directory.
   *
   * @param interceptRequest the intercept request to register
   * @return a {@link CompletableFuture} representing the pending completion of the put operation
   * @throws NoPeerInfoNodeException if the associated peer does not exist in the directory
   * @throws ExecutionException if an error occurs while checking peer existence
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public CompletableFuture<PutResponse> registerInterceptAsync(InterceptRequest<?> interceptRequest)
      throws NoPeerInfoNodeException, ExecutionException, InterruptedException {
    if (!peerExists(interceptRequest.getPeer())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", interceptRequest.getPeer()));
    }
    final byte[] interceptData = interceptRequest.toBytes(getEncodingCharset());
    final String interceptPath =
        format(
            "%s/%s",
            getInterceptsPathForPeer(interceptRequest.getPeer()), interceptRequest.getUuid());
    return kvClient.put(
        ByteSequence.from(interceptPath.getBytes(getEncodingCharset())),
        ByteSequence.from(interceptData));
  }

  /**
   * Retrieves all intercept requests associated with a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @return a {@link Set} of {@link InterceptRequest} associated with the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<InterceptRequest<?>> getPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final ByteSequence peerInterceptsPathKey =
        ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerInterceptsPathKey, GetOption.builder().isPrefix(true).build()).get();
    for (KeyValue kv : response.getKvs()) {
      final String interceptPath = kv.getKey().toString(getEncodingCharset());
      interceptRequests.add(getInterceptRequest(interceptPath));
    }
    return interceptRequests;
  }

  /**
   * Retrieves all intercept requests in the directory.
   *
   * @return a {@link Set} of all {@link InterceptRequest} instances
   */
  public Set<InterceptRequest<?>> getAllInterceptRequests() {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    try {
      final GetResponse response =
          kvClient.get(getInterceptsPathKey(), GetOption.builder().isPrefix(true).build()).get();
      for (KeyValue kv : response.getKvs()) {
        final String interceptPath = kv.getKey().toString(getEncodingCharset());
        interceptRequests.add(getInterceptRequest(interceptPath));
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
  public InterceptRequest<?> getInterceptRequest(String interceptPath)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    List<KeyValue> kvs =
        kvClient
            .get(ByteSequence.from(interceptPath.getBytes(getEncodingCharset())))
            .get()
            .getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    data = kvs.get(0).getValue().getBytes();
    return InterceptRequest.fromBytes(data, getEncodingCharset());
  }

  /**
   * Adds a listener for intercept node events.
   *
   * @param listener the {@link InterceptNodeListener} to add
   */
  public void addInterceptNodeListener(InterceptNodeListener listener) {
    interceptListeners.add(listener);
    if (logger.isDebugEnabled()) {
      logger.debug("Added intercept node listener of class: {}", listener.getClass().getName());
    }
  }

  /**
   * Unregisters all intercept requests associated with a specific peer.
   *
   * @param peerUuid the UUID of the peer
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void unregisterPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    if (logger.isDebugEnabled()) {
      logger.debug("Unregistering all intercept requests for peer w/uuid: {}", peerUuid);
    }
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn("No intercept requests found for peer w/uuid: {}", peerUuid);
    } else {
      logger.info(
          "Unregistered {} intercept request(s) for peer w/uuid: {}",
          deleteResponse.getDeleted(),
          peerUuid);
    }
  }

  /**
   * Unregisters a specific intercept request associated with a peer.
   *
   * @param peerUuid the UUID of the peer
   * @param interceptRequestUuid the UUID of the intercept request to unregister
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void unregisterPeerInterceptRequest(UUID peerUuid, UUID interceptRequestUuid)
      throws ExecutionException, InterruptedException {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", peerInterceptsPath, interceptRequestUuid.toString())
                        .getBytes(getEncodingCharset())))
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn(
          "No intercept request w/uuid: {} found for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    } else {
      logger.info(
          "Unregistered intercept request w/uuid: {} for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    }
  }

  // </editor-fold>

  // <editor-fold desc="Log methods">

  /**
   * Registers a new log in the directory. If the log already exists, registration is skipped.
   *
   * @param logInfo the information of the log to register
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void registerLog(LogInfo logInfo) throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logInfo, logInfo.getBootstrapServers());
    GetResponse getResponse =
        kvClient
            .get(ByteSequence.from(getLogPath(logInfo.getName()).getBytes(getEncodingCharset())))
            .get();
    if (getResponse.getCount() != 0) {
      logger.info("Skipping registration of existing log with name: {}", logInfo.getName());
      return;
    }
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
    final ByteSequence logKey =
        ByteSequence.from(getLogPath(logInfo.getName()).getBytes(getEncodingCharset()));
    final ByteSequence logData = ByteSequence.from(logInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(logKey, logData).get();
    logger.info(
        "Registered given log node: {} with uuid: {}", logInfo.getName(), logInfo.getUuid());
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
  public LogInfo newLog(String logNamePrefix, String logServers)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logNamePrefix, logServers);
    final LogInfo lastLogWithPrefix = getLastLogWithPrefix(logNamePrefix);

    // generate name of new log with given prefix and monotonically increasing counter
    final String newLogName;
    if (lastLogWithPrefix == null) {
      newLogName = format("%s%010d", logNamePrefix, 1);
    } else {
      String lastLogIdxStr = Strings.stringAfter(lastLogWithPrefix.getName(), logNamePrefix);
      newLogName = format("%s%010d", logNamePrefix, Long.parseLong(lastLogIdxStr) + 1);
    }
    // create and save new LogInfo
    final LogInfo newLogInfo = new LogInfo(newLogName);
    newLogInfo.setUuid(UUID.randomUUID());
    newLogInfo.setBootstrapServers(logServers);
    final Instant now = Instant.now();
    newLogInfo.setCtime(now.toEpochMilli());
    newLogInfo.setMtime(now.toEpochMilli());
    final ByteSequence newLogKey =
        ByteSequence.from(getLogPath(newLogName).getBytes(getEncodingCharset()));
    final ByteSequence newLogData =
        ByteSequence.from(newLogInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(newLogKey, newLogData).get();
    logger.info("Created new log: {} with uuid: {}", newLogName, newLogInfo.getUuid());
    return newLogInfo;
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
        kvClient.get(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset()))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for log w/name: {} does not exist", logName);
      return null;
    }
    return LogInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(getEncodingCharset()));
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
    return kvClient
            .get(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get()
            .getCount()
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
  public Set<LogInfo> getAllLogsWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient
            .get(
                ByteSequence.from(
                    format("%s/%s", getLogsPath(), logNamePrefix).getBytes(getEncodingCharset())),
                GetOption.builder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      logs.add(LogInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogsWithPrefix: {}", logs);
    }
    return logs;
  }

  /**
   * Retrieves all logs registered in the directory.
   *
   * @return a {@link Set} of all {@link LogInfo} instances
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public Set<LogInfo> getAllLogs() throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(getLogsPathKey(), GetOption.builder().isPrefix(true).build()).get();
    final Set<LogInfo> allLogs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      // skip root logs path
      if (kv.getKey().equals(getLogsPathKey())) {
        continue;
      }
      allLogs.add(LogInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogs: {}", allLogs);
    }
    return allLogs;
  }

  /**
   * Retrieves the last log with the specified name prefix based on creation time.
   *
   * @param logNamePrefix the prefix of the log name
   * @return the last {@link LogInfo} with the specified prefix, or {@code null} if none exists
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public LogInfo getLastLogWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final List<String> logNames =
        getAllLogsWithPrefix(logNamePrefix).stream().map(LogInfo::getName).toList();
    long maxLogIndex = -1;
    String lastLog = null;
    for (String log : logNames) {
      // parse index in log names and set max
      String logIdxStr = Strings.stringAfter(log, logNamePrefix);
      long logIdx = Long.parseLong(logIdxStr);
      if (logIdx > maxLogIndex) {
        maxLogIndex = logIdx;
        lastLog = log;
      }
    }
    logger.info("With prefix '{}' got last log = {}", logNamePrefix, lastLog);
    return getLogInfo(lastLog);
  }

  /**
   * Retrieves the count of logs that have names starting with the specified prefix.
   *
   * @param logNamePrefix the prefix of the log names to count
   * @return the number of logs matching the prefix
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public int getLogCount(String logNamePrefix) throws ExecutionException, InterruptedException {
    return getAllLogsWithPrefix(logNamePrefix).size();
  }

  /**
   * Unregisters a specific log identified by its name. Ensures that no peer is using the log before
   * unregistration.
   *
   * @param logName the name of the log to unregister
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws IllegalArgumentException if a peer is currently using the log
   */
  public void unregisterLog(String logName) throws ExecutionException, InterruptedException {
    LogInfo logToUnregister = getLogInfo(logName);
    if (logToUnregister == null) {
      logger.warn("Cannot unregister log with name: {}, log does not exist.", logName);
      return;
    }
    // verify that no peer is using the log
    for (PeerInfo peer : getAllPeers()) {
      boolean isLogUsed =
          Stream.of(getPeerInLog(peer.getUuid()), getPeerOutLog(peer.getUuid()))
              .anyMatch(logUuid -> logToUnregister.getUuid().equals(logUuid));
      if (isLogUsed) {
        throw new IllegalArgumentException(
            format(
                "Cannot unregister log with name: %s, peer w/uuid: %s is using it",
                logName, peer.getUuid()));
      }
    }

    DeleteResponse deleteResponse =
        kvClient
            .delete(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get();
    if (deleteResponse.getDeleted() == 1) {
      logger.info("Unregistered (i.e. deleted) log: {}", logName);
    } else {
      logger.warn("Could not unregister log with name: {}, log does not exist.", logName);
    }
  }

  /**
   * Unregisters all logs that have names starting with the specified prefix.
   *
   * @param logNamePrefix the prefix of the log names to unregister
   * @return the number of logs unregistered
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  long unregisterLogs(String logNamePrefix) throws ExecutionException, InterruptedException {
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", getLogsPath(), logNamePrefix).getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();

    long deleted = deleteResponse.getDeleted();
    if (deleted == 0) {
      logger.warn("No logs found to unregister with prefix: {}", logNamePrefix);
    } else {
      logger.info("Unregistered {} logs with prefix: {}", deleted, logNamePrefix);
    }
    return deleted;
  }

  /**
   * Unregisters all logs except those specified in the exclusion set.
   *
   * @param excludeLogs a {@link Set} of log UUIDs to exclude from unregistration; may be {@code
   *     null}
   * @return the number of logs unregistered
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  long unregisterAllLogsWithExcludes(@Nullable Set<UUID> excludeLogs)
      throws ExecutionException, InterruptedException {
    long deleted = 0;
    if (excludeLogs != null && !excludeLogs.isEmpty()) {
      for (LogInfo log : getAllLogs()) {
        if (!excludeLogs.contains(log.getUuid())) {
          unregisterLog(log.getName());
          deleted++;
        }
      }
    } else {
      DeleteResponse deleteResponse =
          kvClient.delete(getLogsPathKey(), DeleteOption.builder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();
    }
    if (deleted == 0) {
      logger.warn("No logs found to unregister");
    } else {
      logger.info("Unregistered {} logs", deleted);
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
    kvClient.close();
    client.close();
    logger.info("Closed directory {}", directoryUrl);
  }

  // </editor-fold>

  // <editor-fold desc="private helpers">

  /**
   * Retrieves the character set used for encoding and decoding strings. Initialized to UTF-8 if not
   * already set.
   *
   * @return the {@link Charset} used for encoding/decoding
   */
  private static Charset getEncodingCharset() {
    if (loadedCharset == null) {
      loadedCharset = StandardCharsets.UTF_8;
    }
    return loadedCharset;
  }

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
   * Retrieves the etcd path for a peer's incoming logs.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer's incoming logs
   */
  private String getPeerLogsInPath(UUID peerUuid) {
    return format("%s/logs/in", getPeerPath(peerUuid));
  }

  /**
   * Retrieves the etcd path for a peer's outgoing logs.
   *
   * @param peerUuid the UUID of the peer
   * @return the etcd path for the peer's outgoing logs
   */
  private String getPeerLogsOutPath(UUID peerUuid) {
    return format("%s/logs/out", getPeerPath(peerUuid));
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
    return ByteSequence.from(getPeersPath().getBytes(getEncodingCharset()));
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
    return ByteSequence.from(getLogsPath().getBytes(getEncodingCharset()));
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
    return ByteSequence.from(getInterceptsPath().getBytes(getEncodingCharset()));
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
  // </editor-fold>
}
