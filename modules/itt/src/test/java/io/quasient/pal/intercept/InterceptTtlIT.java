/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.directory.InterceptLease;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end integration tests for intercept TTL lifecycle.
 *
 * <p>Verifies that intercept TTL interacts correctly with the InterceptMatcher and
 * InterceptInformer: intercepts with TTL are registered, match operations, and are unregistered
 * when the lease expires.
 *
 * <p>Full flow under test: PalDirectory creates TTL intercept → etcd lease → InterceptInformer
 * picks up event → InterceptMatcher registers it → operations are intercepted → lease expires →
 * etcd delete → InterceptInformer relays removal → InterceptMatcher unregisters.
 */
public class InterceptTtlIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS = "io.quasient.pal.intercept.FakeCallbackClass";
  private static final String CALLBACK_METHOD = "aFakeMethod";

  /** Separate PalDirectory for TTL operations (createIntercept with ttlSeconds). */
  private PalDirectory ttlDirectory;

  /** Lease handle for the current test; closed in tearDown if non-null. */
  private InterceptLease currentLease;

  /**
   * Sets up a dedicated PalDirectory for TTL intercept operations.
   *
   * <p>Also drains any stale callback messages that may have been queued by the interceptable peer
   * from previous tests in the suite. The ThinPeer receives all messages broadcast by the
   * interceptable peer, so callbacks from prior tests' intercepts may arrive before this test
   * registers its own intercept.
   */
  @Before
  public void setUpTtlDirectory() throws Exception {
    ttlDirectory =
        directoryConnectionProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for TTL PalDirectory"));

    // Drain stale callbacks from previous tests
    Thread.sleep(200);
    List<Message> stale = getCallbacksNonBlocking();
    if (!stale.isEmpty()) {
      logger.info("Drained {} stale callback(s) from previous tests", stale.size());
    }
  }

  /**
   * Cleans up lease after each test.
   *
   * <p>Note: ttlDirectory is intentionally NOT closed here because it shares the underlying etcd
   * client with the superclass's PalDirectory. Closing it here would terminate the shared client
   * before the superclass tearDown can use it for intercept cleanup.
   */
  @After
  public void tearDownTtlDirectory() throws Exception {
    if (currentLease != null && !currentLease.isClosed()) {
      currentLease.close();
    }
  }

  /**
   * Creates an InterceptableApp instance on the interceptable peer.
   *
   * @return an ObjectRef pointing to the created instance
   */
  private ObjectRef createInterceptableAppInstance() {
    ExecMessage response =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    return ObjectRef.from(response.getReturnValue().getObject().getRef());
  }

  /**
   * Invokes multiplyBy on the given InterceptableApp instance.
   *
   * @param appInstance the target object reference
   * @param multiplier the multiplier argument
   * @return the response ExecMessage
   */
  private ExecMessage invokeMultiplyBy(ObjectRef appInstance, int multiplier) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyBy",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {multiplier}));
  }

  /**
   * Creates a BEFORE intercept request for multiplyBy on InterceptableApp.
   *
   * @param interceptUuid the UUID for the intercept request
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createMultiplyByInterceptRequest(
      UUID interceptUuid) {
    return new InterceptRequest<>(
        interceptUuid,
        myPeerUuid,
        InterceptType.BEFORE,
        InterceptableApp.class.getName(),
        CALLBACK_CLASS,
        CALLBACK_METHOD,
        new InterceptableMethodCall("multiplyBy", Collections.singletonList("java.lang.Integer")));
  }

  /** Verifies that a TTL intercept is active and callbacks are invoked while the lease is alive. */
  @Test
  public void interceptTTL_registeredAndActiveWhileLeaseAlive() throws Exception {
    logger.info("===== interceptTTL_registeredAndActiveWhileLeaseAlive: TEST STARTED =====");

    // Given: register a TTL intercept with 10-second TTL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> req = createMultiplyByInterceptRequest(interceptUuid);
    currentLease = ttlDirectory.createIntercept(req, 10);

    assertThat("Lease should not be NONE", currentLease, is(not(InterceptLease.NONE)));
    assertThat("Lease should have valid ID", currentLease.getLeaseId() > 0, is(true));

    // Wait for intercept registration to propagate through etcd watch
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // When: invoke method matching the intercept pattern within TTL window
    ObjectRef appInstance = createInterceptableAppInstance();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // Then: callback is received
    assertThat("Invocation should succeed", response.getRaisedThrowable(), is(nullValue()));
    List<Message> callbacks = getCallbacks(1, 5000);
    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));
    assertThat("Callback should not be null", callbacks.get(0), is(notNullValue()));

    logger.info("===== interceptTTL_registeredAndActiveWhileLeaseAlive: TEST COMPLETED =====");
  }

  /** Verifies that no callback is invoked after the TTL expires. */
  @Test
  public void interceptTTL_noCallbackAfterExpiry() throws Exception {
    logger.info("===== interceptTTL_noCallbackAfterExpiry: TEST STARTED =====");

    // Given: register a TTL intercept with short 5-second TTL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> req = createMultiplyByInterceptRequest(interceptUuid);
    currentLease = ttlDirectory.createIntercept(req, 5);

    assertThat("Lease should not be NONE", currentLease, is(not(InterceptLease.NONE)));

    // Wait for intercept registration to propagate
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Verify the intercept is active first
    ObjectRef appInstance = createInterceptableAppInstance();
    ExecMessage earlyResponse = invokeMultiplyBy(appInstance, 2);
    assertThat(
        "Early invocation should succeed", earlyResponse.getRaisedThrowable(), is(nullValue()));
    List<Message> earlyCallbacks = getCallbacks(1, 5000);
    assertThat("Should receive callback while TTL active", earlyCallbacks.size(), is(1));

    // When: wait for TTL to expire (5s TTL + buffer for etcd propagation)
    logger.info("Waiting for TTL to expire...");
    TimeUnit.SECONDS.sleep(7);

    // Then: invoke method again — no callback should arrive
    ExecMessage lateResponse = invokeMultiplyBy(appInstance, 2);
    assertThat(
        "Late invocation should succeed", lateResponse.getRaisedThrowable(), is(nullValue()));

    // Wait briefly to ensure no callback arrives
    Thread.sleep(1000);
    List<Message> lateCallbacks = getCallbacksNonBlocking();
    assertThat("Should receive no callbacks after TTL expiry", lateCallbacks, is(empty()));

    logger.info("===== interceptTTL_noCallbackAfterExpiry: TEST COMPLETED =====");
  }

  /**
   * Verifies that refreshing the lease extends the intercept's lifetime beyond the original TTL.
   */
  @Test
  public void interceptTTL_callbackResumesAfterManualRefresh() throws Exception {
    logger.info("===== interceptTTL_callbackResumesAfterManualRefresh: TEST STARTED =====");

    // Given: register a TTL intercept with 5-second TTL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> req = createMultiplyByInterceptRequest(interceptUuid);
    currentLease = ttlDirectory.createIntercept(req, 5);

    assertThat("Lease should not be NONE", currentLease, is(not(InterceptLease.NONE)));

    // Wait for registration propagation
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef appInstance = createInterceptableAppInstance();

    // Refresh the lease at 3 seconds (before expiry)
    logger.info("Waiting 3 seconds before refreshing lease...");
    TimeUnit.SECONDS.sleep(3);
    currentLease.keepAlive();
    logger.info("Lease refreshed via keepAlive()");

    // When: invoke method at 7 seconds total (beyond original 5-second TTL)
    TimeUnit.SECONDS.sleep(4);
    logger.info("Invoking method at ~7s (beyond original 5s TTL)...");
    ExecMessage response = invokeMultiplyBy(appInstance, 5);

    // Then: callback is still received because lease was extended
    assertThat("Invocation should succeed", response.getRaisedThrowable(), is(nullValue()));
    List<Message> callbacks = getCallbacks(1, 5000);
    assertThat("Should receive callback after lease refresh", callbacks.size(), is(1));

    logger.info("===== interceptTTL_callbackResumesAfterManualRefresh: TEST COMPLETED =====");
  }

  /** Verifies that the InterceptMatcher no longer matches the intercept after TTL expiry. */
  @Test
  public void interceptTTL_removedFromMatcherOnExpiry() throws Exception {
    logger.info("===== interceptTTL_removedFromMatcherOnExpiry: TEST STARTED =====");

    // Given: register a TTL intercept with 5-second TTL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> req = createMultiplyByInterceptRequest(interceptUuid);
    currentLease = ttlDirectory.createIntercept(req, 5);

    assertThat("Lease should not be NONE", currentLease, is(not(InterceptLease.NONE)));

    // Wait for registration propagation
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Verify intercept is registered in directory
    Set<InterceptRequest<?>> activeIntercepts = ttlDirectory.listInterceptsForPeer(myPeerUuid);
    assertThat(
        "Intercept should be listed in directory while active", activeIntercepts, is(not(empty())));

    // When: wait for TTL to expire (5s TTL + buffer for etcd watch propagation)
    logger.info("Waiting for TTL to expire...");
    TimeUnit.SECONDS.sleep(7);

    // Then: intercept should be removed from directory
    Set<InterceptRequest<?>> expiredIntercepts = ttlDirectory.listInterceptsForPeer(myPeerUuid);
    assertThat(
        "Intercept should be removed from directory after TTL expiry",
        expiredIntercepts,
        is(empty()));

    // Also verify no callback is received when invoking the method
    ObjectRef appInstance = createInterceptableAppInstance();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);
    assertThat("Invocation should succeed", response.getRaisedThrowable(), is(nullValue()));
    Thread.sleep(1000);
    List<Message> callbacks = getCallbacksNonBlocking();
    assertThat("Should receive no callbacks after matcher removal", callbacks, is(empty()));

    logger.info("===== interceptTTL_removedFromMatcherOnExpiry: TEST COMPLETED =====");
  }
}
