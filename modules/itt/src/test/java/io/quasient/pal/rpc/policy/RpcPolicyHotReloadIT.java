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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for end-to-end RPC policy hot-reload behavior with real peer processes.
 *
 * <p>These tests verify that modifying the YAML policy file at runtime changes RPC access decisions
 * without restarting the peer. Each test starts a peer with {@code --rpc-policy} and {@code
 * --rpc-policy-watch-interval 500} for fast polling, then modifies the policy file and verifies the
 * new policy takes effect.
 *
 * <p>Tests are parameterized to run against both ZMQ-RPC and JSON-RPC transports, matching the
 * pattern established by {@link RpcPolicyIT}.
 *
 * <p><b>Infrastructure requirements:</b> etcd (Docker), Kafka (Docker), test application JARs from
 * itt-apps module.
 */
@RunWith(Parameterized.class)
public class RpcPolicyHotReloadIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(RpcPolicyHotReloadIT.class);

  /** FQN of the RPC access denied exception returned in throwable responses. */
  private static final String ACCESS_DENIED_EX =
      "io.quasient.pal.core.rpc.policy.RpcAccessDeniedException";

  /** Test application class used for RPC calls. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Wait time in milliseconds after modifying the policy file (4x the 500ms poll interval). */
  private static final long RELOAD_WAIT_MS = 2000;

  /** Temporary folder for writing policy YAML files, shared across all test instances. */
  @ClassRule public static TemporaryFolder tempFolder = new TemporaryFolder();

  /** Builder for creating binary (Colfer) messages. */
  private final MessageBuilder messageBuilder = new MessageBuilder();

  /** Client UUID for ThinPeer identification. */
  private final UUID clientId = UUID.randomUUID();

  /** The RPC transport type under test, injected by the parameterized runner. */
  private final RpcType rpcType;

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
  public RpcPolicyHotReloadIT(RpcType rpcType) {
    this.rpcType = rpcType;
  }

  /**
   * Verifies that a policy change from deny to allow takes effect without restarting the peer.
   *
   * <p>Starts a peer with a policy that denies {@code io.quasient.foobar.apps.**} and a default
   * DENY action, verifies an RPC call is denied, then modifies the YAML to allow the class and
   * verifies the same RPC call succeeds after the file watcher picks up the change.
   */
  @Test
  public void shouldDenyThenAllowAfterPolicyFileChange() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      // Given: Peer started with policy YAML that denies all, poll interval 500ms
      File policyFile = writePolicyFile("deny-then-allow", denyAllPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-policy-watch-interval",
              "500",
              "--rpc-default-action",
              "DENY",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // When: RPC call to a denied class method is made
      // Then: The call is denied
      callStaticMethodAndAssertDenied(
          thinPeer,
          METHODS_CLASS,
          "staticStringWithStringArg",
          new String[] {"java.lang.String"},
          new Object[] {"hello"});

      logger.info("[{}] Initial RPC call correctly denied", rpcType);

      // When: YAML file is modified to allow the class, wait for reload
      Files.writeString(policyFile.toPath(), allowMethodsClassPolicy());
      Thread.sleep(RELOAD_WAIT_MS);

      // Then: Same RPC call now succeeds (policy hot-reloaded)
      Object result =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});
      assertNotNull("Result should not be null after policy change", result);

      logger.info(
          "shouldDenyThenAllowAfterPolicyFileChange [{}]: Policy hot-reload verified", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that a policy change from allow to deny takes effect without restarting the peer.
   *
   * <p>Starts a peer with a policy that allows {@code io.quasient.foobar.apps.**} and a default
   * ALLOW action, verifies an RPC call succeeds, then modifies the YAML to deny the class and
   * verifies the same RPC call is denied after the file watcher picks up the change.
   */
  @Test
  public void shouldAllowThenDenyAfterPolicyFileChange() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      // Given: Peer started with policy YAML that allows the Methods class, default ALLOW
      File policyFile = writePolicyFile("allow-then-deny", allowMethodsClassPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-policy-watch-interval",
              "500",
              "--rpc-default-action",
              "ALLOW",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // When: RPC call to an allowed class method is made
      // Then: The call succeeds
      Object result =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});
      assertNotNull("Result should not be null when allowed", result);

      logger.info("[{}] Initial RPC call correctly allowed", rpcType);

      // When: YAML file is modified to deny the class, wait for reload
      Files.writeString(policyFile.toPath(), denyMethodsClassPolicy());
      Thread.sleep(RELOAD_WAIT_MS);

      // Then: Same RPC call is now denied (policy hot-reloaded)
      callStaticMethodAndAssertDenied(
          thinPeer,
          METHODS_CLASS,
          "staticStringWithStringArg",
          new String[] {"java.lang.String"},
          new Object[] {"hello"});

      logger.info(
          "shouldAllowThenDenyAfterPolicyFileChange [{}]: Policy hot-reload verified", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that the current policy is retained when an invalid YAML edit is written to the policy
   * file.
   *
   * <p>Starts a peer with a policy that allows a class, verifies an RPC call succeeds, then
   * overwrites the YAML with invalid content. After the watcher attempts to reload, the same RPC
   * call should still succeed because the current policy is retained on parse errors. The peer log
   * should contain an ERROR about the failed reload.
   */
  @Test
  public void shouldKeepCurrentPolicyOnInvalidYamlEdit() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer thinPeer = null;
    PalDirectory directory = null;

    try {
      // Given: Peer started with policy YAML that allows the Methods class
      File policyFile = writePolicyFile("invalid-yaml", allowMethodsClassPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-policy-watch-interval",
              "500",
              "--rpc-default-action",
              "ALLOW",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      thinPeer = createThinPeer(peerInfo);

      // When: RPC call to an allowed class method is made
      // Then: The call succeeds
      Object result =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});
      assertNotNull("Result should not be null when allowed", result);

      logger.info("[{}] Initial RPC call correctly allowed", rpcType);

      // When: YAML file is overwritten with invalid content, wait for reload attempt
      Files.writeString(policyFile.toPath(), "{{not valid yaml: [broken");
      Thread.sleep(RELOAD_WAIT_MS);

      // Then: Same RPC call still succeeds (current policy retained)
      Object resultAfterInvalid =
          callStaticMethodAndAssertAllowed(
              thinPeer,
              METHODS_CLASS,
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              new Object[] {"hello"});
      assertNotNull("Result should not be null when policy retained", resultAfterInvalid);

      // Verify: Peer log contains ERROR about failed reload
      assertTrue(
          "Peer log should contain error about failed reload",
          peer.waitForLogLine("Failed to reload RPC policy", 5000));

      logger.info(
          "shouldKeepCurrentPolicyOnInvalidYamlEdit [{}]: Policy retained on parse error", rpcType);
    } finally {
      cleanup(thinPeer, peer, peerId, directory);
    }
  }

  /**
   * Verifies that class metadata reflects a reloaded policy after a policy file change.
   *
   * <p>Starts a peer with a policy that denies {@code java.util.ArrayList}, requests metadata and
   * verifies the class is not included, then modifies the YAML to allow the class. After the file
   * watcher reloads the policy, a metadata request should now include the class. This verifies that
   * {@code ClassMetadataSerializer} also picks up the reloaded policy via {@code RpcPolicyHolder}.
   *
   * <p>Metadata queries use the binary (Colfer) protocol via ZMQ regardless of the parameterized
   * transport, because {@code sendToPeer(MetaMessage)} is only available over ZMQ. The test still
   * runs for both parameters to verify metadata filtering is independent of the active transport.
   */
  @Test
  public void shouldReloadMetadataAfterPolicyChange() throws Exception {
    UUID peerId = UUID.randomUUID();
    PeerProcess peer = null;
    ThinPeer zmqThinPeer = null;
    PalDirectory directory = null;

    try {
      // Given: Peer started with policy YAML that denies java.util.ArrayList
      File policyFile = writePolicyFile("metadata-reload", denyAllPolicy());
      peer =
          launchPolicyPeer(
              peerId,
              "--rpc-policy",
              policyFile.getAbsolutePath(),
              "--rpc-policy-watch-interval",
              "500",
              "--rpc-default-action",
              "DENY",
              "--as-service");

      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = waitForPeerInDirectory(directory, peerId);
      // Always use ZMQ for metadata queries (MetaMessage is a binary/Colfer protocol)
      zmqThinPeer = createZmqThinPeer(peerInfo);

      // Request a GC first (metadata serialization is memory-intensive)
      sendGcCommand(zmqThinPeer);
      Thread.sleep(500);

      // When: Metadata request is made with deny-all policy
      String metadataContent = fetchMetadataContent(zmqThinPeer);

      // Then: java.util.ArrayList should NOT appear (denied by policy)
      assertFalse(
          "Metadata should not contain ArrayList when denied",
          metadataContent.contains("java.util.ArrayList"));

      logger.info("[{}] Metadata correctly excludes ArrayList when denied", rpcType);

      // When: YAML file is modified to allow java.util.ArrayList, wait for reload
      Files.writeString(policyFile.toPath(), allowArrayListPolicy());
      Thread.sleep(RELOAD_WAIT_MS);

      // Request another GC and fresh metadata
      sendGcCommand(zmqThinPeer);
      Thread.sleep(500);
      String reloadedMetadata = fetchMetadataContent(zmqThinPeer);

      // Then: java.util.ArrayList should now appear in metadata
      assertTrue(
          "Metadata should contain ArrayList after policy reload",
          reloadedMetadata.contains("java.util.ArrayList"));

      logger.info(
          "shouldReloadMetadataAfterPolicyChange [{}]: Metadata reflects reloaded policy", rpcType);
    } finally {
      cleanup(zmqThinPeer, peer, peerId, directory);
    }
  }

  // ===========================================================================================
  // Policy YAML definitions
  // ===========================================================================================

  /**
   * Policy that denies all RPC access with no allow rules.
   *
   * @return YAML policy content
   */
  private String denyAllPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules: []
        """;
  }

  /**
   * Policy that allows all methods on the {@code Methods} test class.
   *
   * @return YAML policy content
   */
  private String allowMethodsClassPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "io.quasient.foobar.apps.quantized.rpc.Methods"
            method: "**"
            action: ALLOW
        """;
  }

  /**
   * Policy that explicitly denies the {@code Methods} test class.
   *
   * @return YAML policy content
   */
  private String denyMethodsClassPolicy() {
    return """
        version: 1
        defaultAction: ALLOW
        rules:
          - class: "io.quasient.foobar.apps.quantized.rpc.Methods"
            method: "**"
            action: DENY
        """;
  }

  /**
   * Policy that allows only {@code java.util.ArrayList} for metadata testing.
   *
   * @return YAML policy content
   */
  private String allowArrayListPolicy() {
    return """
        version: 1
        defaultAction: DENY
        rules:
          - class: "java.util.ArrayList"
            method: "**"
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
    File file = tempFolder.newFile(prefix + "-" + UUID.randomUUID() + "-rpc-policy.yaml");
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
   * @throws Exception if initialization fails
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
   * @throws Exception if initialization fails
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
   * Fetches metadata content from the peer via a FETCH_CLASSES_INFO meta request.
   *
   * @param tp the ZMQ ThinPeer to send through
   * @return the metadata JSON content as a string
   * @throws Exception if the request fails or the metadata file cannot be read
   */
  private String fetchMetadataContent(ThinPeer tp) throws Exception {
    Map<String, Object> params = new HashMap<>();
    params.put("compress_encode", false);
    params.put("merge_ancestry", false);
    MetaMessage metaRequest =
        messageBuilder.buildMetaMessageRequest(
            clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO, params);

    MetaMessage metaResponse = tp.sendToPeer(metaRequest);
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
    return Files.readString(metadataFile);
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
}
