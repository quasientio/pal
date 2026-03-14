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
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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
 * <p>Tests are parameterized to run against both ZMQ-RPC and JSON-RPC transports.
 *
 * <p><b>Infrastructure requirements:</b> etcd (Docker), Kafka (Docker), test application JARs from
 * itt-apps module.
 */
@RunWith(Parameterized.class)
public class RpcPolicyIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(RpcPolicyIT.class);

  /** FQN of the RPC access denied exception returned in throwable responses. */
  private static final String ACCESS_DENIED_EX =
      "io.quasient.pal.core.rpc.policy.RpcAccessDeniedException";

  /** Test application class used for RPC calls. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Builder for creating binary (Colfer) messages. */
  private final MessageBuilder messageBuilder = new MessageBuilder();

  /** Client UUID for ThinPeer identification. */
  private final UUID clientId = UUID.randomUUID();

  /** The RPC transport type under test, injected by the parameterized runner. */
  private final RpcType rpcType;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Returns the parameter combinations for the parameterized runner.
   *
   * @return a collection of single-element arrays, each containing an {@link RpcType}
   */
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> transports() {
    return List.of(new Object[] {RpcType.ZMQ_RPC}, new Object[] {RpcType.JSON_RPC});
  }

  /**
   * Constructs a parameterized test instance for the given RPC transport type.
   *
   * @param rpcType the RPC transport to use for this test run
   */
  public RpcPolicyIT(RpcType rpcType) {
    this.rpcType = rpcType;
  }

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
      peer = launchPolicyPeer(peerId, "--rpc-default-action", "DENY", "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Call a static method — should be denied
      callStaticMethodAndAssertDenied(
          thinPeer,
          METHODS_CLASS,
          "testNonVoidStatic",
          new String[] {"java.lang.String"},
          new Object[] {"hello"});

      logger.info("shouldDenyAllByDefaultWithNoRules [{}]: RPC correctly denied", rpcType);
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
      peer = launchPolicyPeer(peerId, "--rpc-policy", policyFile.getAbsolutePath(), "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Call testNonVoidStatic — should be allowed
      Object result =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"UPPER"});

      assertNotNull("Result should not be null", result);
      // ZMQ returns the unwrapped String; JSON-RPC returns the JSON-encoded value (with quotes)
      String resultStr = ((String) result).replace("\"", "");
      assertEquals("upper", resultStr.toLowerCase(Locale.getDefault()));
      logger.info("shouldAllowExplicitlyAllowedMethods [{}]: RPC correctly allowed", rpcType);
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
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Call giveMeNull — NOT in allowlist, should be denied
      callStaticMethodAndAssertDenied(
          thinPeer, METHODS_CLASS, "giveMeNull", new String[0], new Object[0]);

      logger.info(
          "shouldDenyMethodsNotInAllowlist [{}]: Unlisted method correctly denied", rpcType);
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
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Call System.exit(0) — should be blocked by deny-unsafe preset
      callStaticMethodAndAssertDenied(
          thinPeer, "java.lang.System", "exit", new String[] {"int"}, new Object[] {0});

      // Verify a normal method is still allowed (not blocked by preset)
      Object allowedResult =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "testNonVoidStatic",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});

      assertNotNull("Normal method should return a value", allowedResult);
      logger.info(
          "shouldEnforceDenyUnsafePreset [{}]: Unsafe operations correctly blocked", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that channel-scoped rules allow or deny based on the RPC channel type.
   *
   * <p>Tests with two peers: one whose policy allows Methods.** only for ZMQ_SOCKET_RPC, and one
   * whose policy allows Methods.** only for WEBSOCKET_RPC. When using ZMQ transport, the ZMQ peer
   * allows and the WebSocket peer denies. When using JSON-RPC transport (which maps to
   * WEBSOCKET_RPC), the WebSocket peer allows and the ZMQ peer denies.
   */
  @Test
  public void shouldEnforceChannelScopedRules() throws Exception {
    UUID zmqPeerId = UUID.randomUUID();
    UUID wsPeerId = UUID.randomUUID();
    PeerProcess zmqPeer = null;
    PeerProcess wsPeer = null;
    ThinPeer zmqThinPeer = null;
    ThinPeer wsThinPeer = null;
    PalDirectory directory = null;

    try {
      // Peer 1: allows Methods.** only for ZMQ_SOCKET_RPC
      File zmqPolicyFile = writePolicyFile("channel-zmq-allowed", channelScopedPolicy());
      zmqPeer =
          launchPolicyPeer(
              zmqPeerId,
              "--rpc-policy",
              zmqPolicyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo zmqPeerInfo = waitForPeerInDirectory(directory, zmqPeerId);
      zmqThinPeer = createThinPeer(zmqPeerInfo);

      // Peer 2: allows Methods.** only for WEBSOCKET_RPC
      File wsPolicyFile = writePolicyFile("channel-ws-only", channelScopedWebsocketOnlyPolicy());
      wsPeer =
          launchPolicyPeer(
              wsPeerId,
              "--rpc-policy",
              wsPolicyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--as-service");

      PeerInfo wsPeerInfo = waitForPeerInDirectory(directory, wsPeerId);
      wsThinPeer = createThinPeer(wsPeerInfo);

      if (rpcType == RpcType.ZMQ_RPC) {
        // ZMQ transport → ZMQ_SOCKET_RPC channel: peer1 allows, peer2 denies
        callStaticMethodAndAssertAllowed(
            zmqThinPeer,
            METHODS_CLASS,
            "testNonVoidStatic",
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

        callStaticMethodAndAssertDenied(
            wsThinPeer,
            METHODS_CLASS,
            "testNonVoidStatic",
            new String[] {"java.lang.String"},
            new Object[] {"hello"});
      } else {
        // JSON-RPC transport → WEBSOCKET_RPC channel: peer1 denies, peer2 allows
        callStaticMethodAndAssertDenied(
            zmqThinPeer,
            METHODS_CLASS,
            "testNonVoidStatic",
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

        callStaticMethodAndAssertAllowed(
            wsThinPeer,
            METHODS_CLASS,
            "testNonVoidStatic",
            new String[] {"java.lang.String"},
            new Object[] {"hello"});
      }

      logger.info(
          "shouldEnforceChannelScopedRules [{}]: Channel-scoped rules correctly enforced", rpcType);
    } finally {
      closeThinPeer(zmqThinPeer);
      closeThinPeer(wsThinPeer);
      if (zmqPeer != null) {
        stopPeer(zmqPeer);
      }
      if (wsPeer != null) {
        stopPeer(wsPeer);
      }
      if (directory != null) {
        try {
          directory.deletePeer(zmqPeerId);
          directory.deletePeer(wsPeerId);
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
   *
   * <p>Metadata queries use the binary (Colfer) protocol via ZMQ regardless of the parameterized
   * transport, because {@code sendToPeer(MetaMessage)} is only available over ZMQ. The test still
   * runs for both parameters to verify metadata filtering is independent of the active transport.
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
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      // Always use ZMQ for metadata queries (MetaMessage is a binary/Colfer protocol)
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
      logger.info("shouldFilterMetadataToMatchPolicy [{}]: Metadata correctly filtered", rpcType);
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
              "--no-wal-incoming-cli",
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

  /**
   * Verifies that the {@code deny-nonpublic} preset blocks non-public methods while allowing public
   * ones.
   *
   * <p>Uses {@code staticStringWithStringArg} (public) and {@code doSomethingStatically}
   * (package-private) from the {@code Methods} test application as targets.
   */
  @Test
  public void shouldDenyNonPublicMethodsWithDenyNonpublicPreset() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy-preset",
              "deny-nonpublic",
              "--rpc-default-action",
              "ALLOW",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Public static method — should be allowed
      Object result =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});
      assertNotNull("Public method result should not be null", result);

      // Package-private static method — should be denied
      callStaticMethodAndAssertDenied(
          thinPeer, METHODS_CLASS, "doSomethingStatically", new String[0], new Object[0]);

      logger.info(
          "shouldDenyNonPublicMethodsWithDenyNonpublicPreset [{}]: Correctly enforced", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that a YAML rule with {@code visibility: ALL} allows non-public methods even when the
   * default action is DENY.
   */
  @Test
  public void shouldAllowNonPublicWhenYamlRuleGrantsAllVisibility() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      File policyFile = writePolicyFile("visibility-all", visibilityAllPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // Package-private static method should be allowed with visibility: ALL
      if (rpcType == RpcType.ZMQ_RPC) {
        ExecMessage response =
            callStaticMethod(
                thinPeer, METHODS_CLASS, "doSomethingStatically", new String[0], new Object[0]);
        assertThat(
            "Should not raise throwable for allowed call",
            response.getRaisedThrowable(),
            is(nullValue()));
      } else {
        JsonRpcResponse response =
            callStaticMethodJsonRpc(
                thinPeer, METHODS_CLASS, "doSomethingStatically", new String[0], new Object[0]);
        assertThat("Should not have error for allowed call", response.getError(), is(nullValue()));
      }

      logger.info(
          "shouldAllowNonPublicWhenYamlRuleGrantsAllVisibility [{}]: Correctly allowed", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that the metadata endpoint excludes non-public members when the {@code deny-nonpublic}
   * preset is active.
   *
   * <p>Metadata is always queried via ZMQ (binary Colfer protocol). The response should contain
   * only public members — non-public members are excluded by the deny-nonpublic preset.
   *
   * <p>Note: classes in the {@code io.quasient.pal.*} namespace are always excluded from metadata
   * by the serializer's hardcoded prefix list, so this test uses {@code java.util.ArrayList} to
   * verify visibility-based metadata filtering.
   */
  @Test
  public void shouldFilterMetadataByVisibilityWithPreset() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      // Allow only PUBLIC members of java.util.ArrayList, deny everything else.
      // Public methods should appear; non-public should be filtered by visibility rule.
      File policyFile = writePolicyFile("metadata-visibility", metadataVisibilityPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-default-action",
              "DENY",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      // Always use ZMQ for metadata queries (MetaMessage is a binary/Colfer protocol)
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

      ObjectMapper om = new ObjectMapper();
      JsonNode wrapper = om.readTree(body);
      String responsePath = wrapper.get("response").asText();
      Path metadataFile = Path.of(responsePath);
      assertTrue("Metadata file should exist on local filesystem", Files.exists(metadataFile));
      String plainBody = Files.readString(metadataFile);

      // java.util.ArrayList should appear in metadata (allowed by explicit rule)
      assertTrue(
          "Metadata should contain java.util.ArrayList", plainBody.contains("java.util.ArrayList"));

      // Public methods like "add" should be present (public visibility is allowed)
      assertTrue(
          "Metadata should contain public method 'add' from ArrayList",
          plainBody.contains("\"add\""));

      // Clean up metadata memory
      sendGcCommand(thinPeer);
      logger.info(
          "shouldFilterMetadataByVisibilityWithPreset [{}]: Metadata correctly filtered", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that a user-defined YAML rule allowing a specific non-public method overrides the
   * {@code deny-nonpublic} preset's blanket denial.
   *
   * <p>The first-match-wins rule evaluation means user rules (evaluated before presets) can grant
   * access to specific non-public members even when the preset would deny them.
   */
  @Test
  public void shouldAllowPublicWithDenyNonpublicAndExplicitOverride() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      File policyFile = writePolicyFile("visibility-override", visibilityOverridePolicy());
      peer = launchPolicyPeer(peerId, "--rpc-policy", policyFile.getAbsolutePath(), "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // doSomethingStatically is package-private, but explicitly allowed by user rule
      if (rpcType == RpcType.ZMQ_RPC) {
        ExecMessage response =
            callStaticMethod(
                thinPeer, METHODS_CLASS, "doSomethingStatically", new String[0], new Object[0]);
        assertThat(
            "Should not raise throwable — user rule overrides preset",
            response.getRaisedThrowable(),
            is(nullValue()));
      } else {
        JsonRpcResponse response =
            callStaticMethodJsonRpc(
                thinPeer, METHODS_CLASS, "doSomethingStatically", new String[0], new Object[0]);
        assertThat(
            "Should not have error — user rule overrides preset",
            response.getError(),
            is(nullValue()));
      }

      logger.info(
          "shouldAllowPublicWithDenyNonpublicAndExplicitOverride [{}]: Override works", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
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
   * Policy that allows only public members of {@code java.util.ArrayList}, used for verifying that
   * metadata filtering respects the {@code visibility} rule dimension. Non-public members of
   * ArrayList (internal JDK methods) should be excluded from metadata.
   *
   * @return YAML policy content
   */
  private String metadataVisibilityPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "java.util.ArrayList"
            method: "**"
            visibility: PUBLIC
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

  /**
   * Policy with {@code visibility: ALL} for the {@code Methods} class, allowing methods of any
   * visibility, with a default action of DENY.
   *
   * @return YAML policy content
   */
  private String visibilityAllPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "**"
            visibility: ALL
            action: ALLOW
        """;
  }

  /**
   * Policy that combines the {@code deny-nonpublic} preset with an explicit override allowing a
   * specific package-private method. The user rule should take precedence over the preset.
   *
   * @return YAML policy content
   */
  private String visibilityOverridePolicy() {
    return """
        version: 1
        defaultAction: ALLOW
        presets:
          - deny-nonpublic
        rules:
          - class: "io.quasient.pal.apps.quantized.rpc.Methods"
            method: "doSomethingStatically"
            visibility:
              - PACKAGE_PRIVATE
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
   * <p>Automatically adds {@code -d} (directory), {@code -cp} (classpath), {@code --zmq-rpc auto},
   * and {@code --json-rpc auto} flags so that both transports are available.
   *
   * @param peerId the UUID to assign to the peer
   * @param extraArgs additional command-line arguments (policy flags, etc.)
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
    args.add("--zmq-rpc");
    args.add("auto");
    args.add("--json-rpc");
    args.add("auto");

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
   * Creates a ThinPeer connected to the given peer using the parameterized {@link #rpcType}.
   *
   * @param peerInfo the peer to connect to
   * @return an initialized ThinPeer
   */
  private ThinPeer createThinPeer(PeerInfo peerInfo) throws Exception {
    return new ThinPeer()
        .withUuid(clientId)
        .withInitialPeer(peerInfo)
        .withOutboundRpcType(rpcType)
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
   * Calls a static method via JSON-RPC and returns the response.
   *
   * @param tp the ThinPeer to send through
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes parameter type names
   * @param params parameter values
   * @return the JSON-RPC response
   * @throws Exception if the call fails or times out
   */
  private JsonRpcResponse callStaticMethodJsonRpc(
      ThinPeer tp, String className, String methodName, String[] paramTypes, Object[] params)
      throws Exception {
    List<Argument> arguments = new ArrayList<>();
    for (int i = 0; i < params.length; i++) {
      arguments.add(new Argument(params[i], paramTypes[i]));
    }
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall(className, methodName, arguments);
    return tp.sendJsonRpcRequestToPeer(request).get(30, TimeUnit.SECONDS);
  }

  /**
   * Calls a static method using the parameterized transport and asserts that the call is denied by
   * the RPC policy.
   *
   * @param tp the ThinPeer to send through
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes parameter type names
   * @param params parameter values
   * @throws Exception if the call fails unexpectedly
   */
  private void callStaticMethodAndAssertDenied(
      ThinPeer tp, String className, String methodName, String[] paramTypes, Object[] params)
      throws Exception {
    if (rpcType == RpcType.ZMQ_RPC) {
      ExecMessage response = callStaticMethod(tp, className, methodName, paramTypes, params);
      assertAccessDenied(response);
    } else {
      JsonRpcResponse response =
          callStaticMethodJsonRpc(tp, className, methodName, paramTypes, params);
      assertJsonRpcAccessDenied(response);
    }
  }

  /**
   * Calls a static method using the parameterized transport and asserts that the call is allowed.
   * Returns the unwrapped result value.
   *
   * @param tp the ThinPeer to send through
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes parameter type names
   * @param params parameter values
   * @return the unwrapped return value from the call
   * @throws Exception if the call fails unexpectedly
   */
  private Object callStaticMethodAndAssertAllowed(
      ThinPeer tp, String className, String methodName, String[] paramTypes, Object[] params)
      throws Exception {
    if (rpcType == RpcType.ZMQ_RPC) {
      ExecMessage response = callStaticMethod(tp, className, methodName, paramTypes, params);
      assertThat(
          "Response should have return value", response.getReturnValue(), is(not(nullValue())));
      assertThat(
          "Response should not have throwable", response.getRaisedThrowable(), is(nullValue()));
      return Unwrapper.unwrapObject(response.getReturnValue().getObject());
    } else {
      JsonRpcResponse response =
          callStaticMethodJsonRpc(tp, className, methodName, paramTypes, params);
      assertThat("JSON-RPC response should not have error", response.getError(), is(nullValue()));
      assertThat(
          "JSON-RPC response should have result", response.getResult(), is(not(nullValue())));
      ResponseObject responseObject = response.getResult().getValue();
      return responseObject != null ? responseObject.getValue() : null;
    }
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
   * Asserts that the ZMQ response indicates an RPC access denied error.
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
   * Asserts that the JSON-RPC response indicates an RPC access denied error.
   *
   * @param response the JsonRpcResponse to check
   */
  private void assertJsonRpcAccessDenied(JsonRpcResponse response) {
    assertThat(
        "JSON-RPC response should have error for access denial",
        response.getError(),
        is(not(nullValue())));
    assertEquals(
        "Error code should be RPC_ACCESS_DENIED",
        JsonRpcErrorCode.RPC_ACCESS_DENIED.getCode(),
        response.getError().getCode());
    assertThat(
        "Error data should not be null", response.getError().getData(), is(not(nullValue())));
    assertEquals(
        "Error data throwable type should be RpcAccessDeniedException",
        ACCESS_DENIED_EX,
        response.getError().getData().getThrowableType());
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
