/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.cxn.directory.InterceptLease;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@code InterceptManager} orchestration logic.
 *
 * <p>These tests verify the high-level operations: apply, remove, removeByBundle, removeByUuid,
 * diff, status, and deterministic UUID generation. {@code PalDirectory} is mocked via Mockito to
 * isolate orchestration logic from etcd.
 *
 * @see ApplyResult
 * @see RemoveResult
 * @see BundleStatus
 * @see InterceptDiff
 */
public class InterceptManagerTest {

  /** Mock directory. */
  private PalDirectory directory;

  /** Manager under test. */
  private InterceptManager manager;

  /** A fixed peer UUID used across tests. */
  private static final UUID PEER_UUID = UUID.randomUUID();

  /** A fixed peer name used across tests. */
  private static final String PEER_NAME = "my-peer";

  /** Sets up mock directory and manager before each test. */
  @Before
  public void setUp() {
    directory = mock(PalDirectory.class);
    manager = new InterceptManager(directory);
  }

  /**
   * Creates a PeerInfo with the given UUID and name.
   *
   * @param uuid the peer UUID
   * @param name the peer name
   * @return a configured PeerInfo
   */
  private static PeerInfo peerInfo(UUID uuid, String name) {
    return new PeerInfo(uuid, name);
  }

  /**
   * Creates a simple method intercept spec.
   *
   * @param targetClass the target class
   * @param targetName the target method name
   * @param callbackMethod the callback method name
   * @return a configured InterceptSpec
   */
  private static InterceptSpec methodSpec(
      String targetClass, String targetName, String callbackMethod) {
    return InterceptSpec.builder()
        .targetClass(targetClass)
        .targetName(targetName)
        .type(InterceptType.BEFORE)
        .callbackClass("com.acme.Callback")
        .callbackMethod(callbackMethod)
        .build();
  }

  /**
   * Creates a bundle with the given name, peer defaults, and intercept specs.
   *
   * @param bundleName the bundle name
   * @param peerName the default peer name
   * @param specs the intercept specs
   * @return a configured InterceptBundleSpec
   */
  private static InterceptBundleSpec bundle(
      String bundleName, String peerName, InterceptSpec... specs) {
    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(peerName, null, null, null, null, null, null);
    InterceptBundleSpec.Builder builder =
        InterceptBundleSpec.builder(bundleName).defaults(defaults);
    for (InterceptSpec spec : specs) {
      builder.addIntercept(spec);
    }
    return builder.build();
  }

  // ===== Apply tests =====

  @Test
  public void apply_createsInterceptsAndStoresMetadata() throws Exception {
    // Given: A bundle with 2 intercepts and a mock PalDirectory with a matching peer
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1, spec2);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.createIntercept(any(), anyLong())).thenReturn(InterceptLease.NONE);

    // When
    ApplyResult result = manager.apply(bundle);

    // Then: createIntercept() is called twice, createBundleMetadata() is called once,
    //       and ApplyResult has 2 created entries
    assertThat(result.getCreatedCount(), is(2L));
    assertThat(result.getEntries().size(), is(2));
    verify(directory, times(2)).createIntercept(any(), anyLong());
    verify(directory, times(1)).createBundleMetadata(eq("test-bundle"), any(BundleMetadata.class));
  }

  @Test
  public void apply_skipsExistingIntercepts() throws Exception {
    // Given: A bundle with 2 intercepts, mock createIntercept() to throw
    //        IllegalArgumentException ("already exists") for the first intercept
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1, spec2);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.createIntercept(any(), anyLong()))
        .thenThrow(new IllegalArgumentException("already exists"))
        .thenReturn(InterceptLease.NONE);

    // When
    ApplyResult result = manager.apply(bundle);

    // Then: ApplyResult has 1 created and 1 skipped
    assertThat(result.getSkippedCount(), is(1L));
    assertThat(result.getCreatedCount(), is(1L));
  }

  @Test
  public void apply_isIdempotent_deterministicUuids() throws Exception {
    // Given: The same bundle spec applied twice
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.createIntercept(any(), anyLong())).thenReturn(InterceptLease.NONE);

    // When: apply(bundle) is called twice
    ArgumentCaptor<InterceptRequest<?>> captor1 = ArgumentCaptor.forClass(InterceptRequest.class);
    manager.apply(bundle);
    verify(directory).createIntercept(captor1.capture(), anyLong());
    UUID firstUuid = captor1.getValue().getUuid();

    // Reset and apply again
    when(directory.createIntercept(any(), anyLong())).thenReturn(InterceptLease.NONE);
    ArgumentCaptor<InterceptRequest<?>> captor2 = ArgumentCaptor.forClass(InterceptRequest.class);
    manager.apply(bundle);
    verify(directory, times(2)).createIntercept(captor2.capture(), anyLong());
    UUID secondUuid = captor2.getAllValues().get(1).getUuid();

    // Then: The same UUIDs are generated both times
    assertThat(firstUuid, is(secondUuid));
  }

  @Test
  public void apply_resolvesPeerByName() throws Exception {
    // Given: A bundle with defaults containing peer: "my-peer"
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.createIntercept(any(), anyLong())).thenReturn(InterceptLease.NONE);

    // When
    manager.apply(bundle);

    // Then: getPeerByName("my-peer") is called on the mock PalDirectory
    verify(directory, atLeastOnce()).getPeerByName(PEER_NAME);
  }

  @Test
  public void apply_throwsOnUnknownPeer() throws Exception {
    // Given: A bundle with defaults containing peer: "nonexistent" and a mock PalDirectory
    //        where getPeerByName("nonexistent") returns null
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptBundleSpec bundle = bundle("test-bundle", "nonexistent", spec1);

    when(directory.getPeerByName("nonexistent")).thenReturn(null);

    // When/Then: A descriptive exception is thrown indicating the peer was not found
    try {
      manager.apply(bundle);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("nonexistent"));
    }
  }

  @Test
  public void apply_perInterceptPeerOverride() throws Exception {
    // Given: A bundle with default peer "default-peer" and one intercept overriding
    //        peer to "override-peer"
    UUID defaultPeerUuid = UUID.randomUUID();
    UUID overridePeerUuid = UUID.randomUUID();

    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("refund")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Callback")
            .callbackMethod("checkRefund")
            .peerOverride("override-peer")
            .build();

    InterceptBundleSpec bundle = bundle("test-bundle", "default-peer", spec1, spec2);

    when(directory.getPeerByName("default-peer"))
        .thenReturn(peerInfo(defaultPeerUuid, "default-peer"));
    when(directory.getPeerByName("override-peer"))
        .thenReturn(peerInfo(overridePeerUuid, "override-peer"));
    when(directory.createIntercept(any(), anyLong())).thenReturn(InterceptLease.NONE);

    // When
    manager.apply(bundle);

    // Then: both peer names are resolved
    verify(directory, atLeastOnce()).getPeerByName("default-peer");
    verify(directory, atLeastOnce()).getPeerByName("override-peer");
  }

  @Test
  public void apply_setsCorrectTtlSeconds() throws Exception {
    // Given: A bundle with an intercept that has ttl: 5m (300 seconds)
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("placeOrder")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Callback")
            .callbackMethod("verify")
            .ttlOverride(Duration.ofMinutes(5))
            .build();
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.createIntercept(any(), eq(300L))).thenReturn(InterceptLease.NONE);

    // When
    manager.apply(bundle);

    // Then: createIntercept(request, 300) is called with ttlSeconds=300
    verify(directory).createIntercept(any(), eq(300L));
  }

  @Test
  public void apply_fieldIntercept() throws Exception {
    // Given: A bundle with a field intercept spec (kind=FIELD, fieldOp=GET)
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("status")
            .type(InterceptType.AFTER)
            .callbackClass("com.acme.Audit")
            .callbackMethod("onFieldRead")
            .kind(InterceptableKind.FIELD)
            .fieldOpType(FieldOpType.GET)
            .build();
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    ArgumentCaptor<InterceptRequest<?>> captor = ArgumentCaptor.forClass(InterceptRequest.class);
    when(directory.createIntercept(captor.capture(), anyLong())).thenReturn(InterceptLease.NONE);

    // When
    manager.apply(bundle);

    // Then: The InterceptRequest passed to createIntercept contains an InterceptableFieldOp
    InterceptRequest<?> request = captor.getValue();
    assertThat(request.getInterceptable() instanceof InterceptableFieldOp, is(true));
  }

  // ===== Remove tests =====

  @Test
  public void remove_deletesInterceptsBySpec() throws Exception {
    // Given: A bundle with 2 intercepts, mock PalDirectory with a matching peer
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1, spec2);

    UUID uuid1 = InterceptManager.generateInterceptUuid("test-bundle", spec1);
    UUID uuid2 = InterceptManager.generateInterceptUuid("test-bundle", spec2);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));

    // Existing intercepts matching the UUIDs
    InterceptRequest<?> existing1 =
        new InterceptRequest<>(
            uuid1,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("placeOrder", Collections.emptyList()));
    InterceptRequest<?> existing2 =
        new InterceptRequest<>(
            uuid2,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "checkRefund",
            new InterceptableMethodCall("refund", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Arrays.asList(existing1, existing2));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    RemoveResult result = manager.remove(bundle);

    // Then: deleteIntercept() is called twice with the correct deterministic UUIDs,
    //       and RemoveResult has 2 removed entries
    assertThat(result.getRemovedCount(), is(2L));
    verify(directory).deleteIntercept(PEER_UUID, uuid1);
    verify(directory).deleteIntercept(PEER_UUID, uuid2);
    verify(directory).deleteBundleMetadata("test-bundle");
  }

  @Test
  public void remove_handlesNotFoundGracefully() throws Exception {
    // Given: A bundle with 2 intercepts, but only one exists in directory
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1, spec2);

    UUID uuid1 = InterceptManager.generateInterceptUuid("test-bundle", spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));

    // Only first intercept exists
    InterceptRequest<?> existing1 =
        new InterceptRequest<>(
            uuid1,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("placeOrder", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Collections.singletonList(existing1));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    RemoveResult result = manager.remove(bundle);

    // Then: RemoveResult tracks the not-found intercept without throwing
    assertThat(result.getRemovedCount(), is(1L));
    assertThat(result.getNotFoundCount(), is(1L));
  }

  @Test
  public void removeByBundle_readsMetadataAndDeletes() throws Exception {
    // Given: Bundle metadata stored in directory with bundleName="test-bundle" and 3 intercept
    // UUIDs
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    UUID uuid3 = UUID.randomUUID();
    BundleMetadata metadata =
        new BundleMetadata(
            "test-bundle", PEER_UUID, Arrays.asList(uuid1, uuid2, uuid3), Instant.now(), 1);

    when(directory.getBundleMetadata("test-bundle")).thenReturn(metadata);

    // All three exist in directory
    InterceptRequest<?> existing1 =
        new InterceptRequest<>(
            uuid1,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.Foo",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("bar", Collections.emptyList()));
    InterceptRequest<?> existing2 =
        new InterceptRequest<>(
            uuid2,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.Foo",
            "com.acme.Callback",
            "check",
            new InterceptableMethodCall("baz", Collections.emptyList()));
    InterceptRequest<?> existing3 =
        new InterceptRequest<>(
            uuid3,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.Foo",
            "com.acme.Callback",
            "audit",
            new InterceptableMethodCall("qux", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet =
        new HashSet<>(Arrays.asList(existing1, existing2, existing3));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    RemoveResult result = manager.removeByBundle("test-bundle");

    // Then: deleteIntercept() is called 3 times, and deleteBundleMetadata is called
    assertThat(result.getRemovedCount(), is(3L));
    verify(directory).deleteIntercept(PEER_UUID, uuid1);
    verify(directory).deleteIntercept(PEER_UUID, uuid2);
    verify(directory).deleteIntercept(PEER_UUID, uuid3);
    verify(directory).deleteBundleMetadata("test-bundle");
  }

  @Test
  public void removeByBundle_throwsWhenBundleNotFound() throws Exception {
    // Given: Mock getBundleMetadata("unknown") returns null
    when(directory.getBundleMetadata("unknown")).thenReturn(null);

    // When/Then: An exception is thrown indicating the bundle was not found
    try {
      manager.removeByBundle("unknown");
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("unknown"));
    }
  }

  @Test
  public void removeByUuid_deletesSingleIntercept() throws Exception {
    // Given: A specific peerUuid and interceptUuid
    UUID interceptUuid = UUID.randomUUID();

    // When
    RemoveResult result = manager.removeByUuid(PEER_UUID, interceptUuid);

    // Then: A single deleteIntercept call is made
    verify(directory).deleteIntercept(PEER_UUID, interceptUuid);
    assertThat(result.getRemovedCount(), is(1L));
    assertThat(result.getEntries().size(), is(1));
    assertThat(result.getEntries().get(0).getUuid(), is(interceptUuid));
  }

  // ===== Diff tests =====

  @Test
  public void diff_showsToCreate() throws Exception {
    // Given: A bundle with 1 intercept and an empty directory
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(Collections.emptySet());

    // When
    List<InterceptDiff> diffs = manager.diff(bundle);

    // Then: 1 entry with DiffType.CREATE
    assertThat(diffs.size(), is(1));
    assertThat(diffs.get(0).getDiffType(), is(InterceptDiff.DiffType.CREATE));
  }

  @Test
  public void diff_showsUnchanged() throws Exception {
    // Given: A bundle with 1 intercept and a directory containing a matching intercept
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1);

    UUID interceptUuid = InterceptManager.generateInterceptUuid("test-bundle", spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));

    // Existing intercept with identical configuration
    InterceptRequest<?> existing =
        new InterceptRequest<>(
            interceptUuid,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("placeOrder", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Collections.singletonList(existing));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    List<InterceptDiff> diffs = manager.diff(bundle);

    // Then: 1 entry with DiffType.UNCHANGED
    assertThat(diffs.size(), is(1));
    assertThat(diffs.get(0).getDiffType(), is(InterceptDiff.DiffType.UNCHANGED));
  }

  @Test
  public void diff_showsModified() throws Exception {
    // Given: A bundle with 1 intercept with priority=10 and a directory containing the same
    //        intercept but with priority=0
    InterceptSpec spec1 =
        InterceptSpec.builder()
            .targetClass("com.acme.OrderService")
            .targetName("placeOrder")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Callback")
            .callbackMethod("verify")
            .priorityOverride(10)
            .build();
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1);

    UUID interceptUuid = InterceptManager.generateInterceptUuid("test-bundle", spec1);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));

    // Existing intercept with different priority (0)
    InterceptRequest<?> existing =
        new InterceptRequest<>(
            interceptUuid,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("placeOrder", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Collections.singletonList(existing));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    List<InterceptDiff> diffs = manager.diff(bundle);

    // Then: 1 entry with DiffType.MODIFIED and details about priority
    assertThat(diffs.size(), is(1));
    assertThat(diffs.get(0).getDiffType(), is(InterceptDiff.DiffType.MODIFIED));
    assertThat(diffs.get(0).getDetails(), containsString("priority"));
  }

  // ===== Status tests =====

  @Test
  public void status_showsActiveAndMissing() throws Exception {
    // Given: A bundle with 3 intercepts, where only 2 exist in the directory
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");
    InterceptSpec spec3 = methodSpec("com.acme.OrderService", "cancel", "onCancel");
    InterceptBundleSpec bundle = bundle("test-bundle", PEER_NAME, spec1, spec2, spec3);

    UUID uuid1 = InterceptManager.generateInterceptUuid("test-bundle", spec1);
    UUID uuid2 = InterceptManager.generateInterceptUuid("test-bundle", spec2);

    when(directory.getPeerByName(PEER_NAME)).thenReturn(peerInfo(PEER_UUID, PEER_NAME));

    // Only 2 of 3 intercepts exist
    InterceptRequest<?> existing1 =
        new InterceptRequest<>(
            uuid1,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("placeOrder", Collections.emptyList()));
    InterceptRequest<?> existing2 =
        new InterceptRequest<>(
            uuid2,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.Callback",
            "checkRefund",
            new InterceptableMethodCall("refund", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Arrays.asList(existing1, existing2));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    BundleStatus status = manager.status(bundle);

    // Then: 2 active entries, 3 total entries
    assertThat(status.getActiveCount(), is(2L));
    assertThat(status.getTotalCount(), is(3));
  }

  @Test
  public void statusByBundle_readsMetadata() throws Exception {
    // Given: Bundle metadata stored in directory
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    BundleMetadata metadata =
        new BundleMetadata("test-bundle", PEER_UUID, Arrays.asList(uuid1, uuid2), Instant.now(), 1);

    when(directory.getBundleMetadata("test-bundle")).thenReturn(metadata);

    // Only uuid1 exists in directory
    InterceptRequest<?> existing1 =
        new InterceptRequest<>(
            uuid1,
            PEER_UUID,
            InterceptType.BEFORE,
            "com.acme.Foo",
            "com.acme.Callback",
            "verify",
            new InterceptableMethodCall("bar", Collections.emptyList()));

    Set<InterceptRequest<?>> existingSet = new HashSet<>(Collections.singletonList(existing1));
    when(directory.listInterceptsForPeer(PEER_UUID)).thenReturn(existingSet);

    // When
    BundleStatus status = manager.statusByBundle("test-bundle");

    // Then: getBundleMetadata is called and status reflects 1 active, 2 total
    verify(directory).getBundleMetadata("test-bundle");
    assertThat(status.getActiveCount(), is(1L));
    assertThat(status.getTotalCount(), is(2));
    assertThat(status.getBundleName(), is("test-bundle"));
  }

  // ===== UUID determinism tests =====

  @Test
  public void generateInterceptUuid_isDeterministic() {
    // Given: The same bundle name and InterceptSpec content
    InterceptSpec spec = methodSpec("com.acme.OrderService", "placeOrder", "verify");

    // When: The UUID generation logic is invoked twice with identical inputs
    UUID first = InterceptManager.generateInterceptUuid("test-bundle", spec);
    UUID second = InterceptManager.generateInterceptUuid("test-bundle", spec);

    // Then: The same UUID is produced both times
    assertThat(first, is(second));
  }

  @Test
  public void generateInterceptUuid_differsForDifferentInputs() {
    // Given: Two different InterceptSpec definitions (different target methods)
    InterceptSpec spec1 = methodSpec("com.acme.OrderService", "placeOrder", "verify");
    InterceptSpec spec2 = methodSpec("com.acme.OrderService", "refund", "checkRefund");

    // When: The UUID generation logic is invoked for each
    UUID uuid1 = InterceptManager.generateInterceptUuid("test-bundle", spec1);
    UUID uuid2 = InterceptManager.generateInterceptUuid("test-bundle", spec2);

    // Then: The two UUIDs are different
    assertThat(uuid1, is(not(uuid2)));
  }
}
