/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for end-to-end RPC policy enforcement.
 *
 * <p>These tests verify that the RPC policy system works from CLI flag through dispatch to
 * response, including metadata filtering. Each test writes a temporary YAML policy file, starts a
 * peer with the {@code --rpc-policy} flag pointing to it, performs RPC calls, and verifies the
 * policy is enforced.
 *
 * <p><b>Infrastructure requirements:</b> etcd (Docker), Kafka (Docker), test application JARs from
 * itt-apps module.
 */
public class RpcPolicyIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(RpcPolicyIT.class);

  /** FQN of the RPC access denied exception returned in throwable responses. */
  private static final String ACCESS_DENIED_EX =
      "io.quasient.pal.core.rpc.policy.RpcAccessDeniedException";

  /** Test application class used for RPC calls. */
  private static final String METHODS_CLASS = "io.quasient.pal.apps.quantized.rpc.Methods";

  /** Builder for creating binary (Colfer) messages. */
  private final MessageBuilder messageBuilder = new MessageBuilder();

  /** Client UUID for ThinPeer identification. */
  private final UUID clientId = UUID.randomUUID();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Verifies that a peer with {@code --rpc-default-action DENY} and no rules denies all RPC calls.
   */
  @Test
  public void shouldDenyAllByDefaultWithNoRules() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      peer =
          launchPolicyPeer(
              peerId, "--rpc-default-action", "DENY", "--zmq-rpc", "auto", "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createZmqThinPeer(peerInfo);

      // Call a static method — should be denied
      ExecMessage response =
          callStaticMethod(
              thinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});

      assertAccessDenied(response);
      logger.info("shouldDenyAllByDefaultWithNoRules: RPC correctly denied");
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that a method explicitly allowed in the policy file can be called successfully via
   * RPC.
   */
  @Test
  public void shouldAllowExplicitlyAllowedMethods() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      File policyFile = writePolicyFile("allow-method", allowMethodPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--zmq-rpc",
              "auto",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createZmqThinPeer(peerInfo);

      // Call testNonVoidStatic — should be allowed
      ExecMessage response =
          callStaticMethod(
              thinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"UPPER"});

      assertThat(
          "Response should have return value", response.getReturnValue(), is(not(nullValue())));
      assertThat(
          "Response should not have throwable", response.getRaisedThrowable(), is(nullValue()));

      Object result = Unwrapper.unwrapObject(response.getReturnValue().getObject());
      assertEquals("upper", ((String) result).toLowerCase(Locale.getDefault()));
      logger.info("shouldAllowExplicitlyAllowedMethods: RPC correctly allowed");
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /** Verifies that methods not in the allowlist are denied when the default action is DENY. */
  @Test
  public void shouldDenyMethodsNotInAllowlist() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      File policyFile = writePolicyFile("deny-unlisted", allowMethodPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--zmq-rpc",
              "auto",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createZmqThinPeer(peerInfo);

      // Call giveMeNull — NOT in allowlist, should be denied
      ExecMessage response =
          callStaticMethod(thinPeer, METHODS_CLASS, "giveMeNull", new String[0], new Object[0]);

      assertAccessDenied(response);
      logger.info("shouldDenyMethodsNotInAllowlist: Unlisted method correctly denied");
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /** Verifies that the {@code deny-unsafe} preset blocks dangerous operations like System.exit. */
  @Test
  public void shouldEnforceDenyUnsafePreset() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy-preset",
              "deny-unsafe",
              "--rpc-default-action",
              "ALLOW",
              "--zmq-rpc",
              "auto",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createZmqThinPeer(peerInfo);

      // Call System.exit(0) — should be blocked by deny-unsafe preset
      ExecMessage response =
          callStaticMethod(
              thinPeer, "java.lang.System", "exit", new String[] {"int"}, new Object[] {0});

      assertAccessDenied(response);

      // Verify a normal method is still allowed (not blocked by preset)
      ExecMessage allowedResponse =
          callStaticMethod(
              thinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});

      assertThat(
          "Normal method should be allowed", allowedResponse.getRaisedThrowable(), is(nullValue()));
      logger.info("shouldEnforceDenyUnsafePreset: Unsafe operations correctly blocked");
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that channel-scoped rules allow or deny based on the RPC channel type.
   *
   * <p>Tests with two peers: one whose policy allows Methods.** only for ZMQ_SOCKET_RPC (so ZMQ
   * calls succeed), and one whose policy allows Methods.** only for WEBSOCKET_RPC (so ZMQ calls are
   * denied). This validates that the channel constraint in rules is enforced.
   */
  @Test
  public void shouldEnforceChannelScopedRules() throws Exception {
    UUID allowedPeerId = UUID.randomUUID();
    UUID deniedPeerId = UUID.randomUUID();
    PeerProcess allowedPeer = null;
    PeerProcess deniedPeer = null;
    ThinPeer allowedThinPeer = null;
    ThinPeer deniedThinPeer = null;
    PalDirectory directory = null;

    try {
      // Peer 1: allows Methods.** only for ZMQ_SOCKET_RPC → ZMQ call should succeed
      File allowedPolicyFile = writePolicyFile("channel-zmq-allowed", channelScopedPolicy());
      allowedPeer =
          launchPolicyPeer(
              allowedPeerId,
              "--rpc-policy",
              allowedPolicyFile.getAbsolutePath(),
              "--zmq-rpc",
              "auto",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo allowedPeerInfo = waitForPeerInDirectory(directory, allowedPeerId);
      allowedThinPeer = createZmqThinPeer(allowedPeerInfo);

      ExecMessage zmqAllowedResponse =
          callStaticMethod(
              allowedThinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});

      assertThat(
          "ZMQ call should be allowed when channel matches rule",
          zmqAllowedResponse.getRaisedThrowable(),
          is(nullValue()));

      // Peer 2: allows Methods.** only for WEBSOCKET_RPC → ZMQ call should be denied
      File deniedPolicyFile =
          writePolicyFile("channel-ws-only", channelScopedWebsocketOnlyPolicy());
      deniedPeer =
          launchPolicyPeer(
              deniedPeerId,
              "--rpc-policy",
              deniedPolicyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--zmq-rpc",
              "auto",
              "--as-service");

      PeerInfo deniedPeerInfo = waitForPeerInDirectory(directory, deniedPeerId);
      deniedThinPeer = createZmqThinPeer(deniedPeerInfo);

      ExecMessage zmqDeniedResponse =
          callStaticMethod(
              deniedThinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});

      assertAccessDenied(zmqDeniedResponse);
      logger.info("shouldEnforceChannelScopedRules: Channel-scoped rules correctly enforced");
    } finally {
      closeThinPeer(allowedThinPeer);
      closeThinPeer(deniedThinPeer);
      if (allowedPeer != null) {
        stopPeer(allowedPeer);
      }
      if (deniedPeer != null) {
        stopPeer(deniedPeer);
      }
      if (directory != null) {
        try {
          directory.deletePeer(allowedPeerId);
          directory.deletePeer(deniedPeerId);
        } catch (Exception e) {
          logger.warn("Cleanup: failed to delete peers from directory", e);
        }
        directory.close();
      }
    }
  }

  /**
   * Verifies that the metadata endpoint (META FETCH_CLASSES_INFO) filters classes based on the RPC
   * policy. With a DENY-all policy, the metadata output should contain no classes (since all
   * members are denied). With an ALLOW policy for specific classes, those classes should appear.
   *
   * <p>Note: classes in the {@code io.quasient.pal.*} namespace are always excluded from metadata
   * by the serializer's hardcoded prefix list, so this test uses JDK classes to verify filtering.
   */
  @Test
  public void shouldFilterMetadataToMatchPolicy() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      // Policy: DENY all by default, ALLOW only java.util.ArrayList.**
      File policyFile = writePolicyFile("metadata-filter", metadataFilterPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--zmq-rpc",
              "auto",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createZmqThinPeer(peerInfo);

      // Request a GC first (metadata serialization is memory-intensive)
      sendGcCommand(thinPeer);
      Thread.sleep(500);

      // Request metadata (uncompressed so we can read the JSON file directly)
      Map<String, Object> params = new HashMap<>();
      params.put("compress_encode", false);
      params.put("merge_ancestry", false);
      MetaMessage metaRequest =
          messageBuilder.buildMetaMessageRequest(
              clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO, params);

      MetaMessage metaResponse = thinPeer.sendToPeer(metaRequest);
      assertNotNull("Meta response should not be null", metaResponse);
      assertEquals(
          "Meta response status should be OK", MetaStatusType.OK.getId(), metaResponse.getStatus());

      String body = metaResponse.getBody();
      assertFalse("Meta response body should not be empty", body.isEmpty());

      // The body is a JSON wrapper: {"service":"...","response":"<file-path>"}
      // Since peer and test run on the same machine, we can read the temp file.
      ObjectMapper om = new ObjectMapper();
      JsonNode wrapper = om.readTree(body);
      String responsePath = wrapper.get("response").asText();
      Path metadataFile = Path.of(responsePath);
      assertTrue("Metadata file should exist on local filesystem", Files.exists(metadataFile));
      String plainBody = Files.readString(metadataFile);

      // Policy allows java.util.ArrayList.** — should appear in metadata
      assertTrue(
          "Metadata should contain the allowed java.util.ArrayList class",
          plainBody.contains("java.util.ArrayList"));

      // java.lang.System is NOT in the allow list — should be filtered out
      assertFalse(
          "Metadata should not contain denied java.lang.System class",
          plainBody.contains("\"className\":\"java.lang.System\""));

      // Count total classes — should be very small (just ArrayList and maybe inner classes)
      int classCount = countOccurrences("className", plainBody);
      logger.info("Metadata returned {} classes (filtered by policy)", classCount);
      assertThat("Should have at least 1 class", classCount, greaterThan(0));

      // Clean up metadata memory
      sendGcCommand(thinPeer);
      logger.info("shouldFilterMetadataToMatchPolicy: Metadata correctly filtered");
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that replay injection is exempt from RPC policy checks, since replay messages
   * represent previously executed operations rather than new external RPC requests.
   *
   * <p>This test records operations to a Chronicle WAL by running a peer with {@code main()}, then
   * replays the WAL on a new peer with a restrictive DENY policy. The replay should succeed because
   * the {@link io.quasient.pal.core.transport.MessageChannelType#REPLAY_INJECTION} channel is
   * exempt from policy checks.
   */
  @Test
  public void shouldAllowReplayEvenWhenPolicyDenies() throws Exception {
    Path chronicleDir = Files.createTempDirectory("rpc-policy-replay-test-");

    try {
      String palHome = System.getenv("PAL_HOME");
      String ittAppsClasspath = buildIttAppsClasspath(palHome);

      // Step 1: Record operations by running a peer with a Chronicle WAL
      ProcessResult recordResult =
          runPeerWithEnv(
              getPalDirectoryUrl(),
              "--wal",
              "file:" + chronicleDir,
              "-cp",
              ittAppsClasspath,
              METHODS_CLASS);

      assertEquals("Recording peer should exit successfully", 0, recordResult.exitCode());

      // Step 2: Create policy that allows ONLY main() (denies everything else)
      File policyFile = writePolicyFile("replay-restrict", replayRestrictivePolicy());

      // Step 3: Replay with restrictive policy — should succeed because
      // REPLAY_INJECTION channel is exempt from policy checks
      ProcessResult replayResult =
          runPeerWithEnv(
              null,
              "--replay-wal",
              "file:" + chronicleDir,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "-cp",
              ittAppsClasspath,
              METHODS_CLASS);

      assertEquals(
          "Replay should succeed (exit code 0) because REPLAY_INJECTION is exempt from policy",
          0,
          replayResult.exitCode());

      logger.info(
          "shouldAllowReplayEvenWhenPolicyDenies: Replay succeeded despite restrictive policy");
    } finally {
      deleteDirectoryRecursively(chronicleDir);
    }
  }

  // ===========================================================================================
  // Policy YAML helpers
  // ===========================================================================================

  /**
   * Policy that allows only {@code Methods.testNonVoidStatic}, denying everything else.
   *
   * @return YAML policy content
   */
  private String allowMethodPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "testNonVoidStatic"
            action: ALLOW
        """;
  }

  /**
   * Channel-scoped policy: allows Methods.** for ZMQ only, denies for WebSocket.
   *
   * @return YAML policy content
   */
  private String channelScopedPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "**"
            channel: ZMQ_SOCKET_RPC
            action: ALLOW
        """;
  }

  /**
   * Channel-scoped policy: allows Methods.** ONLY for WEBSOCKET_RPC, denies for ZMQ.
   *
   * @return YAML policy content
   */
  private String channelScopedWebsocketOnlyPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "**"
            channel: WEBSOCKET_RPC
            action: ALLOW
        """;
  }

  /**
   * Policy that allows only java.util.ArrayList for metadata filtering test.
   *
   * <p>PAL classes (io.quasient.pal.*) are always excluded from metadata by the serializer's
   * hardcoded prefix list, so this policy targets a JDK class instead.
   *
   * @return YAML policy content
   */
  private String metadataFilterPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "java.util.ArrayList"
            method: "**"
            action: ALLOW
        """;
  }

  /**
   * Restrictive policy that allows only main() for replay test.
   *
   * @return YAML policy content
   */
  private String replayRestrictivePolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "main"
            action: ALLOW
        """;
  }

  // ===========================================================================================
  // Infrastructure helpers
  // ===========================================================================================

  /**
   * Writes a YAML policy to a temporary file.
   *
   * @param prefix file name prefix
   * @param yamlContent the YAML content
   * @return the created file
   * @throws IOException if writing fails
   */
  private File writePolicyFile(String prefix, String yamlContent) throws IOException {
    File file = tempFolder.newFile(prefix + "-rpc-policy.yaml");
    Files.writeString(file.toPath(), yamlContent);
    return file;
  }

  /**
   * Launches a peer with the given policy-related arguments plus standard infrastructure flags.
   *
   * <p>Automatically adds {@code -d} (directory), {@code -k} (Kafka), and {@code -cp} (classpath)
   * flags.
   *
   * @param peerId the UUID to assign to the peer
   * @param extraArgs additional command-line arguments (policy flags, RPC flags, etc.)
   * @return the launched peer process
   * @throws IOException if process launch fails
   * @throws InterruptedException if waiting is interrupted
   */
  private PeerProcess launchPolicyPeer(UUID peerId, String... extraArgs)
      throws IOException, InterruptedException {
    String palHome = System.getenv("PAL_HOME");
    String ittAppsClasspath = buildIttAppsClasspath(palHome);

    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("-cp");
    args.add(ittAppsClasspath);

    for (String arg : extraArgs) {
      args.add(arg);
    }

    return launchPeer(peerId, args.toArray(new String[0]));
  }

  /**
   * Builds the classpath string for the itt-apps test applications.
   *
   * @param palHome the PAL_HOME directory path
   * @return the classpath string
   */
  private static String buildIttAppsClasspath(String palHome) {
    return String.format(
        "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes", palHome, palHome);
  }

  /**
   * Waits for a peer to appear in the etcd directory with retries.
   *
   * @param directory the PAL directory client
   * @param peerId the UUID of the peer to find
   * @return the peer info
   * @throws Exception if peer is not found after retries
   */
  private PeerInfo waitForPeerInDirectory(PalDirectory directory, UUID peerId) throws Exception {
    PeerInfo peerInfo = null;
    for (int i = 0; i < 20; i++) {
      peerInfo = directory.getPeer(peerId);
      if (peerInfo != null) {
        return peerInfo;
      }
      Thread.sleep(500);
    }
    throw new RuntimeException("Peer " + peerId + " not found in directory after 10 seconds");
  }

  /**
   * Creates a ThinPeer connected to the given peer via ZMQ.
   *
   * @param peerInfo the peer to connect to
   * @return an initialized ThinPeer
   */
  private ThinPeer createZmqThinPeer(PeerInfo peerInfo) throws Exception {
    return new ThinPeer()
        .withUuid(clientId)
        .withInitialPeer(peerInfo)
        .withOutboundRpcType(RpcType.ZMQ_RPC)
        .init();
  }

  /**
   * Calls a static method via ZMQ RPC and returns the response.
   *
   * @param tp the ThinPeer to send through
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes parameter type names
   * @param params parameter values
   * @return the response ExecMessage
   */
  private ExecMessage callStaticMethod(
      ThinPeer tp, String className, String methodName, String[] paramTypes, Object[] params) {
    ExecMessage request =
        messageBuilder.buildClassMethod(
            clientId,
            className,
            methodName,
            paramTypes,
            this,
            null,
            params,
            new ObjectRef[params.length]);
    return tp.sendToPeer(request);
  }

  /**
   * Sends a GC command to the peer via the ThinPeer.
   *
   * @param tp the ThinPeer to send through
   */
  private void sendGcCommand(ThinPeer tp) {
    ControlMessage gcMsg = messageBuilder.buildGcCommandMessage(clientId);
    tp.sendToPeer(gcMsg);
  }

  // ===========================================================================================
  // Assertion helpers
  // ===========================================================================================

  /**
   * Asserts that the response indicates an RPC access denied error.
   *
   * @param response the ExecMessage response to check
   */
  private void assertAccessDenied(ExecMessage response) {
    assertThat(
        "Response should have raised throwable for access denial",
        response.getRaisedThrowable(),
        is(not(nullValue())));
    assertEquals(
        "Throwable type should be RpcAccessDeniedException",
        ACCESS_DENIED_EX,
        response.getRaisedThrowable().getThrowable().getType());
  }

  /**
   * Counts occurrences of a substring in a string.
   *
   * @param substring the substring to find
   * @param content the string to search in
   * @return number of occurrences
   */
  private static int countOccurrences(String substring, String content) {
    int count = 0;
    int index = content.indexOf(substring);
    while (index != -1) {
      count++;
      index = content.indexOf(substring, index + substring.length());
    }
    return count;
  }

  // ===========================================================================================
  // Cleanup helpers
  // ===========================================================================================

  /**
   * Closes a ThinPeer if not null.
   *
   * @param tp the ThinPeer to close
   */
  private void closeThinPeer(ThinPeer tp) {
    if (tp != null) {
      try {
        tp.close();
      } catch (Exception e) {
        logger.warn("Error closing ThinPeer", e);
      }
    }
  }

  /**
   * Cleans up peer process and directory registration.
   *
   * @param peer the peer process to stop
   * @param peerId the UUID to unregister
   * @param directory the directory to unregister from
   * @throws InterruptedException if stopping is interrupted
   */
  private void cleanupPeerAndDirectory(PeerProcess peer, UUID peerId, PalDirectory directory)
      throws InterruptedException {
    if (peer != null) {
      stopPeer(peer);
    }
    if (directory != null) {
      try {
        directory.deletePeer(peerId);
      } catch (Exception e) {
        logger.warn("Cleanup: failed to delete peer from directory", e);
      }
      directory.close();
    }
  }

  /**
   * Full cleanup: closes ThinPeer, stops peer, unregisters from directory.
   *
   * @param tp the ThinPeer
   * @param peer the peer process
   * @param peerId the peer UUID
   * @param directory the directory
   * @throws InterruptedException if stopping is interrupted
   */
  private void cleanup(ThinPeer tp, PeerProcess peer, UUID peerId, PalDirectory directory)
      throws InterruptedException {
    closeThinPeer(tp);
    cleanupPeerAndDirectory(peer, peerId, directory);
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param dir the directory to delete
   */
  private void deleteDirectoryRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  logger.warn("Failed to delete {}", path, e);
                }
              });
    } catch (IOException e) {
      logger.warn("Failed to delete directory recursively: {}", dir, e);
    }
  }
}
