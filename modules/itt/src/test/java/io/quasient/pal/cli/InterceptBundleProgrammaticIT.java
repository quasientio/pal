/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.dsl.intercept.ApplyResult;
import io.quasient.pal.dsl.intercept.BundleMetadata;
import io.quasient.pal.dsl.intercept.BundleStatus;
import io.quasient.pal.dsl.intercept.InterceptBundleDefaults;
import io.quasient.pal.dsl.intercept.InterceptBundleSpec;
import io.quasient.pal.dsl.intercept.InterceptDiff;
import io.quasient.pal.dsl.intercept.InterceptManager;
import io.quasient.pal.dsl.intercept.InterceptSpec;
import io.quasient.pal.dsl.intercept.InterceptableKind;
import io.quasient.pal.dsl.intercept.RemoveResult;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the programmatic intercept bundle API.
 *
 * <p>These tests exercise the {@link InterceptBundleSpec} builder, {@link InterceptSpec} builder,
 * and {@link InterceptManager} directly from Java code (no YAML involved). This verifies the
 * programmatic workflow documented in the interception concepts page.
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class InterceptBundleProgrammaticIT extends AbstractCliIT {

  private PeerProcess peerProcess;
  private PalDirectory palDirectory;

  @Before
  public void setUp() {
    peerProcess = null;
    palDirectory = null;
  }

  @After
  public void tearDown() throws Exception {
    if (palDirectory != null) {
      palDirectory.close();
      palDirectory = null;
    }
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Tests the full programmatic lifecycle: build a bundle with the builder API, apply it, verify
   * intercepts are created, check status, then remove and verify cleanup.
   */
  @Test
  public void testProgrammaticApplyAndRemove() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "prog-peer-" + generateId();
    String bundleName = "prog-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    // Build a bundle programmatically using the builder API
    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults(peerName, null, null, null, null, null, null);

    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder(bundleName)
            .defaults(defaults)
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("placeOrder")
                    .type(InterceptType.BEFORE)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("verify")
                    .build())
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("refund")
                    .type(InterceptType.AROUND)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("wrapRefund")
                    .build())
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("status")
                    .kind(InterceptableKind.FIELD)
                    .fieldOpType(FieldOpType.GET)
                    .type(InterceptType.AFTER)
                    .callbackClass("com.acme.FieldAuditor")
                    .callbackMethod("onFieldRead")
                    .build())
            .build();

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    InterceptManager manager = new InterceptManager(palDirectory);

    // Apply
    ApplyResult applyResult = manager.apply(bundle);
    assertThat(applyResult.getCreatedCount(), is(3L));
    assertThat(applyResult.getSkippedCount(), is(0L));
    assertThat(applyResult.getFailedCount(), is(0L));

    // Verify intercepts exist
    int interceptCount = palDirectory.listInterceptsForPeer(peerId).size();
    assertThat(interceptCount, is(3));

    // Verify bundle metadata
    BundleMetadata metadata = palDirectory.getBundleMetadata(bundleName);
    assertThat(metadata, is(notNullValue()));
    assertThat(metadata.getPeerUuid(), is(peerId));
    assertThat(metadata.getInterceptUuids().size(), is(3));

    // Remove
    RemoveResult removeResult = manager.remove(bundle);
    assertThat(removeResult.getRemovedCount(), is(3L));
    assertThat(removeResult.getNotFoundCount(), is(0L));

    // Verify cleanup
    assertTrue(palDirectory.listInterceptsForPeer(peerId).isEmpty());
    assertThat(palDirectory.getBundleMetadata(bundleName), is(nullValue()));
  }

  /**
   * Tests that applying the same programmatic bundle twice is idempotent: the second apply skips
   * all intercepts.
   */
  @Test
  public void testProgrammaticIdempotentApply() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "prog-idemp-" + generateId();
    String bundleName = "prog-idemp-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder(bundleName)
            .defaults(new InterceptBundleDefaults(peerName, null, null, null, null, null, null))
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("placeOrder")
                    .type(InterceptType.BEFORE)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("verify")
                    .build())
            .build();

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    InterceptManager manager = new InterceptManager(palDirectory);

    // First apply
    ApplyResult first = manager.apply(bundle);
    assertThat(first.getCreatedCount(), is(1L));

    // Second apply — idempotent
    ApplyResult second = manager.apply(bundle);
    assertThat(second.getCreatedCount(), is(0L));
    assertThat(second.getSkippedCount(), is(1L));

    // Still only one intercept
    assertThat(palDirectory.listInterceptsForPeer(peerId).size(), is(1));
  }

  /**
   * Tests diff and status using the programmatic API: apply a bundle, then verify diff shows
   * unchanged and status shows all active.
   */
  @Test
  public void testProgrammaticDiffAndStatus() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "prog-diff-" + generateId();
    String bundleName = "prog-diff-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder(bundleName)
            .defaults(new InterceptBundleDefaults(peerName, null, null, null, null, null, null))
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("placeOrder")
                    .type(InterceptType.BEFORE)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("verify")
                    .build())
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("refund")
                    .type(InterceptType.AROUND)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("wrapRefund")
                    .build())
            .build();

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    InterceptManager manager = new InterceptManager(palDirectory);

    // Apply
    ApplyResult applyResult = manager.apply(bundle);
    assertThat(applyResult.getCreatedCount(), is(2L));

    // Diff shows all unchanged
    List<InterceptDiff> diffs = manager.diff(bundle);
    assertThat(diffs.size(), is(2));
    for (InterceptDiff diff : diffs) {
      assertThat(diff.getDiffType(), is(InterceptDiff.DiffType.UNCHANGED));
    }

    // Status shows all active
    BundleStatus status = manager.status(bundle);
    assertThat(status.getActiveCount(), is(2L));
    assertThat(status.getTotalCount(), is(2));

    // Status by bundle name also works
    BundleStatus statusByName = manager.statusByBundle(bundleName);
    assertThat(statusByName.getActiveCount(), is(2L));
    assertThat(statusByName.getTotalCount(), is(2));
  }

  /**
   * Tests removeByBundle: apply a bundle programmatically, then remove it by name without needing
   * the original bundle spec.
   */
  @Test
  public void testProgrammaticRemoveByBundleName() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "prog-rm-" + generateId();
    String bundleName = "prog-rm-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder(bundleName)
            .defaults(new InterceptBundleDefaults(peerName, null, null, null, null, null, null))
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("placeOrder")
                    .type(InterceptType.BEFORE)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("verify")
                    .build())
            .addIntercept(
                InterceptSpec.builder()
                    .targetClass("com.acme.OrderService")
                    .targetName("refund")
                    .type(InterceptType.AROUND)
                    .callbackClass("com.acme.FraudChecker")
                    .callbackMethod("wrapRefund")
                    .build())
            .build();

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    InterceptManager manager = new InterceptManager(palDirectory);

    // Apply
    manager.apply(bundle);
    assertThat(palDirectory.listInterceptsForPeer(peerId).size(), is(2));

    // Remove by bundle name (without the original spec)
    RemoveResult removeResult = manager.removeByBundle(bundleName);
    assertThat(removeResult.getRemovedCount(), is(2L));

    // Verify cleanup
    assertTrue(palDirectory.listInterceptsForPeer(peerId).isEmpty());
    assertThat(palDirectory.getBundleMetadata(bundleName), is(nullValue()));
  }
}
