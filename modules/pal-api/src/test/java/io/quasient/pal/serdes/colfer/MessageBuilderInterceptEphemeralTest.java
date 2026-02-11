/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.scratches.TlScratchHolder;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test specs for the ephemeral (TlScratchHolder-backed) version of {@link
 * MessageBuilder#buildInterceptCallbackRequestEphemeral}. These tests verify that the ephemeral
 * variant produces identical output to the regular version while reusing thread-local scratch
 * objects instead of allocating new instances.
 *
 * <p>Part of Phase 2.2: Ephemeral MessageBuilder method optimization. The ephemeral method replaces
 * the multi-allocation {@code buildInterceptCallbackRequest()} with a version that reuses
 * thread-local scratch objects from {@link
 * io.quasient.pal.serdes.colfer.scratches.TlScratchHolder}.
 *
 * <p>Depends on task #693 (implement buildInterceptCallbackRequestEphemeral).
 *
 * @see MessageBuilder#buildInterceptCallbackRequestEphemeral
 * @see io.quasient.pal.serdes.colfer.scratches.TlScratchHolder
 */
public class MessageBuilderInterceptEphemeralTest {

  private final UUID peerUuid = UUID.randomUUID();
  private MessageBuilder builder;
  private InterceptMessage interceptMessage;
  private ExecMessage execMessage;

  /** Sets up shared test fixtures. */
  @Before
  public void setUp() {
    builder = new MessageBuilder(peerUuid);

    interceptMessage =
        new InterceptMessage()
            .withPeerUuid(peerUuid.toString())
            .withInterceptType(InterceptType.BEFORE.toByte())
            .withCallbackClass("com.example.MyCallback")
            .withCallbackMethod("onBefore");

    execMessage = new ExecMessage();
    execMessage.setPeerUuid(peerUuid.toString());
    execMessage.setMessageId("test-msg-001");
  }

  /**
   * Verifies that the ephemeral version of {@code buildInterceptCallbackRequest()} produces a
   * message with identical field values to the regular (allocating) version.
   *
   * <p>This is the fundamental correctness test: both code paths must produce semantically
   * equivalent output for the same inputs.
   */
  @Test
  public void shouldBuildEphemeralInterceptCallbackRequestWithSameFields() {
    InterceptCallbackRequestMessage result =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

    assertThat(result.getPhase(), is(InterceptPhase.BEFORE.toByte()));
    assertThat(result.getInterceptType(), is(interceptMessage.getInterceptType()));
    assertThat(result.getInterceptedPeer(), is(peerUuid.toString()));
    assertThat(result.getCallbackClass(), is("com.example.MyCallback"));
    assertThat(result.getCallbackMethod(), is("onBefore"));
    assertThat(result.getExec(), is(notNullValue()));
    assertThat(result.getCallbackId(), is(notNullValue()));
    assertFalse(result.getCallbackId().isEmpty());
  }

  /**
   * Verifies that the ephemeral method uses counter-based callback IDs instead of {@code
   * UUID.randomUUID()}, and that consecutive calls produce sequential, unique IDs containing the
   * peer UUID prefix.
   *
   * <p>Counter-based IDs avoid the allocation overhead of {@code UUID.randomUUID().toString()}
   * while maintaining uniqueness within a single peer.
   */
  @Test
  public void shouldUseCounterBasedCallbackId() {
    InterceptCallbackRequestMessage result1 =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);
    String id1 = result1.getCallbackId();

    InterceptCallbackRequestMessage result2 =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);
    String id2 = result2.getCallbackId();

    // Both IDs are non-null and non-empty
    assertNotNull(id1);
    assertNotNull(id2);
    assertFalse(id1.isEmpty());
    assertFalse(id2.isEmpty());

    // IDs are different
    assertThat(id1, is(not(id2)));

    // Both contain the peer UUID as prefix
    String prefix = peerUuid.toString();
    assertThat(id1, containsString(prefix));
    assertThat(id2, containsString(prefix));

    // Counter portion is sequential: extract counter from format "{peerUuid}-{counter}"
    long counter1 = Long.parseLong(id1.substring(prefix.length() + 1));
    long counter2 = Long.parseLong(id2.substring(prefix.length() + 1));
    assertThat(counter2, is(greaterThan(counter1)));
  }

  /**
   * Verifies that the ephemeral method returns the same {@link
   * io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage} object reference from {@link
   * io.quasient.pal.serdes.colfer.scratches.TlScratchHolder} on repeated calls, confirming scratch
   * object reuse rather than new allocation.
   *
   * <p>This is the key performance property: the ephemeral method must use {@code
   * TlScratchHolder.icbr()} to avoid heap allocation on every call.
   */
  @Test
  public void shouldReturnScratchObjectNotNewAllocation() {
    InterceptCallbackRequestMessage result1 =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

    InterceptCallbackRequestMessage result2 =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

    // Same object reference (both come from TlScratchHolder.icbr())
    assertSame(result1, result2);

    // Also verify it's the same object that TlScratchHolder.icbr() returns
    InterceptCallbackRequestMessage scratch = TlScratchHolder.icbr();
    assertSame(result1, scratch);
  }

  /**
   * Verifies that an ephemeral {@link
   * io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage} can be serialized to bytes via
   * Colfer and deserialized back to an equivalent message, matching what the regular (allocating)
   * version would produce.
   *
   * <p>This ensures the scratch-object-based message is wire-compatible with the regular version.
   */
  @Test
  public void shouldSerializeCorrectlyToBytes() {
    InterceptCallbackRequestMessage result =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

    // Serialize via Colfer marshal
    byte[] buf = new byte[result.marshalFit()];
    int len = result.marshal(buf, 0);
    assertTrue(len > 0);

    // Deserialize into a fresh instance
    InterceptCallbackRequestMessage deserialized = new InterceptCallbackRequestMessage();
    deserialized.unmarshal(buf, 0, len);

    // Verify field equivalence
    assertThat(deserialized.getCallbackId(), is(result.getCallbackId()));
    assertThat(deserialized.getPhase(), is(result.getPhase()));
    assertThat(deserialized.getInterceptType(), is(result.getInterceptType()));
    assertThat(deserialized.getInterceptedPeer(), is(result.getInterceptedPeer()));
    assertThat(deserialized.getCallbackClass(), is(result.getCallbackClass()));
    assertThat(deserialized.getCallbackMethod(), is(result.getCallbackMethod()));
    assertThat(deserialized.getExec(), is(notNullValue()));
    assertThat(deserialized.getExec().getPeerUuid(), is(peerUuid.toString()));
  }

  /**
   * Verifies that the ephemeral method properly clones the input {@link
   * io.quasient.pal.messages.colfer.ExecMessage} rather than storing a direct reference.
   *
   * <p>This is CRITICAL for safety: the input ExecMessage may itself be a thread-local scratch
   * object from {@code TlScratchHolder.exec()}. If it is not cloned, a subsequent nested dispatch
   * could reset the scratch and corrupt the callback request's exec data. The regular version uses
   * {@code cloneExecMessage()} (marshal/unmarshal deep copy), and the ephemeral version must
   * maintain this safety guarantee.
   *
   * @see <a href="message-passing-flow.md">TlScratchHolder mutation warning</a>
   */
  @Test
  public void shouldHandleExecMessageCloneCorrectly() {
    // Use a scratch ExecMessage (simulating hot-path usage)
    ExecMessage scratchExec = TlScratchHolder.exec();
    scratchExec.setPeerUuid(peerUuid.toString());
    scratchExec.setMessageId("scratch-msg-001");

    InterceptCallbackRequestMessage result =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid, interceptMessage, scratchExec, InterceptPhase.BEFORE, null, false, null);

    ExecMessage clonedExec = result.getExec();

    // Must be a different reference (clone, not same object)
    assertNotSame(scratchExec, clonedExec);

    // Must have the same data
    assertThat(clonedExec.getPeerUuid(), is(peerUuid.toString()));
    assertThat(clonedExec.getMessageId(), is("scratch-msg-001"));

    // Mutating the original scratch should NOT affect the clone
    scratchExec.setPeerUuid("mutated-peer");
    scratchExec.setMessageId("mutated-msg");
    assertThat(clonedExec.getPeerUuid(), is(peerUuid.toString()));
    assertThat(clonedExec.getMessageId(), is("scratch-msg-001"));
  }

  /**
   * Verifies that the ephemeral method uses {@link
   * io.quasient.pal.serdes.colfer.scratches.TlScratchHolder#rv()} for the {@link
   * io.quasient.pal.messages.colfer.ReturnValue} object in AFTER phase callbacks, instead of
   * allocating a new {@code ReturnValue} via {@code new ReturnValue()}.
   *
   * <p>In the regular version, {@code new ReturnValue()} is allocated for AROUND AFTER callbacks
   * when the cloned exec message lacks a return value. The ephemeral version should use the scratch
   * object instead.
   */
  @Test
  public void shouldUseReusableReturnValue() {
    // Configure an AFTER-phase intercept message
    InterceptMessage afterIntercept =
        new InterceptMessage()
            .withPeerUuid(peerUuid.toString())
            .withInterceptType(InterceptType.AFTER.toByte())
            .withCallbackClass("com.example.MyCallback")
            .withCallbackMethod("onAfter");

    // ExecMessage without a ReturnValue set (simulating AROUND AFTER where exec was from BEFORE)
    ExecMessage afterExec = new ExecMessage();
    afterExec.setPeerUuid(peerUuid.toString());
    afterExec.setMessageId("after-msg-001");
    // Importantly: afterExec.getReturnValue() is null

    InterceptCallbackRequestMessage result =
        builder.buildInterceptCallbackRequestEphemeral(
            peerUuid,
            afterIntercept,
            afterExec,
            InterceptPhase.AFTER,
            Integer.valueOf(42),
            false,
            null);

    // The cloned exec should now have a ReturnValue set
    ReturnValue rv = result.getExec().getReturnValue();
    assertNotNull(rv);
    assertFalse(rv.isVoid);
    assertNotNull(rv.object);

    // Verify it's the TlScratchHolder.rv() instance (same thread-local reference)
    // Note: we call TlScratchHolder.rv() AFTER the method, which resets the same instance.
    // To test identity, we need to check that the rv inside the result IS the scratch object.
    // Since TlScratchHolder.rv() returns and resets the same object, we verify by comparing
    // the reference to the thread-local's underlying field.
    ReturnValue scratch = TlScratchHolder.rv();
    assertThat(rv, is(sameInstance(scratch)));
  }
}
