/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer.scratches;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.colfer.ReturnValue;
import org.junit.Test;

/**
 * Tests that formally document TlScratchHolder mutation semantics when nested dispatches occur
 * during intercept processing.
 *
 * <p>The TlScratchHolder provides thread-local reusable scratch objects to avoid allocations on the
 * hot path. However, when a dispatch triggers an intercept callback (e.g., BEFORE) that itself
 * triggers another intercepted method, the nested dispatch reuses the same thread-local scratch
 * objects, corrupting the outer dispatch's state.
 *
 * <p>These tests serve as guardrails for the intercept scratch objects (task #690), ensuring new
 * ephemeral methods follow the clone-before-nested-dispatch pattern.
 *
 * @see TlScratchHolder
 * @see TlMsgScratch
 */
public class TlScratchHolderInterceptNestedDispatchTest {

  /**
   * Documents the KNOWN hazard: a nested call to {@link TlScratchHolder#exec()} on the same thread
   * resets/corrupts the outer ExecMessage because both calls return the same underlying instance.
   *
   * <p>Scenario: An outer dispatch obtains an ExecMessage via {@code TlScratchHolder.exec()} and
   * populates it. A BEFORE intercept callback fires, triggering another intercepted method that
   * also calls {@code TlScratchHolder.exec()}. The second call resets the same object, corrupting
   * the outer dispatch's fields.
   */
  @Test
  public void shouldNotCorruptExecScratchDuringNestedInterceptCallback() {
    // Given: Thread-local ExecMessage obtained via TlScratchHolder.exec()
    //        with fields populated (peerUuid, messageId, instanceMethodCall, etc.)
    ExecMessage outer = TlScratchHolder.exec();
    outer.setPeerUuid("outer-peer-uuid");
    outer.setMessageId("outer-msg-001");

    InstanceMethodCall outerImc = new InstanceMethodCall();
    outerImc.setName("outerMethod");
    outer.setInstanceMethodCall(outerImc);

    // When: A simulated nested dispatch occurs — another call to TlScratchHolder.exec()
    //       on the same thread, simulating a BEFORE callback that triggers another
    //       intercepted method
    ExecMessage inner = TlScratchHolder.exec();

    // Then: The outer ExecMessage fields are reset/corrupted because both calls
    //       return the SAME underlying instance (this is the KNOWN hazard).
    assertThat(inner, is(sameInstance(outer)));
    assertThat(outer.getPeerUuid(), is(""));
    assertThat(outer.getMessageId(), is(""));
    assertThat(outer.getInstanceMethodCall(), is(nullValue()));
  }

  /**
   * Verifies that cloning an ExecMessage via marshal/unmarshal (the established pattern) protects
   * it from corruption by subsequent nested dispatches.
   *
   * <p>This is the SAFE pattern: clone the scratch object before any operation that could trigger a
   * nested dispatch (e.g., before invoking an intercept callback).
   */
  @Test
  public void shouldPreserveClonedExecMessageDuringNestedDispatch() {
    // Given: ExecMessage obtained via TlScratchHolder.exec(), populated with fields,
    //        then cloned via marshal/unmarshal deep copy (cloneExecMessage pattern)
    ExecMessage scratch = TlScratchHolder.exec();
    scratch.setPeerUuid("original-peer-uuid");
    scratch.setMessageId("original-msg-001");

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("originalMethod");
    Class clazz = new Class();
    clazz.setName("com.example.Original");
    imc.setClazz(clazz);
    scratch.setInstanceMethodCall(imc);

    byte[] buf = new byte[scratch.marshalFit()];
    int len = scratch.marshal(buf, 0);
    ExecMessage clone = new ExecMessage();
    clone.unmarshal(buf, 0, len);

    // When: Nested dispatch occurs — another TlScratchHolder.exec() call that resets
    //       and repopulates the thread-local scratch
    ExecMessage nested = TlScratchHolder.exec();
    nested.setPeerUuid("nested-peer-uuid");
    nested.setMessageId("nested-msg-002");

    // Then: The cloned ExecMessage retains its original values
    assertThat(clone, is(not(sameInstance(scratch))));
    assertThat(clone.getPeerUuid(), is("original-peer-uuid"));
    assertThat(clone.getMessageId(), is("original-msg-001"));
    assertThat(clone.getInstanceMethodCall(), is(notNullValue()));
    assertThat(clone.getInstanceMethodCall().getName(), is("originalMethod"));
    assertThat(clone.getInstanceMethodCall().getClazz().getName(), is("com.example.Original"));

    // The scratch (now corrupted) has the inner dispatch's values
    assertThat(scratch.getPeerUuid(), is("nested-peer-uuid"));
    assertThat(scratch.getMessageId(), is("nested-msg-002"));
  }

  /**
   * Documents the KNOWN hazard for ReturnValue: a nested call to {@link TlScratchHolder#rv()} on
   * the same thread corrupts the outer ReturnValue because both calls return the same instance.
   *
   * <p>Scenario: An outer dispatch obtains a ReturnValue via {@code TlScratchHolder.rv()} and sets
   * fields (isVoid, object, from). An AFTER intercept callback fires, and the callback's return
   * handling calls {@code TlScratchHolder.rv()} again, resetting the outer's state.
   */
  @Test
  public void shouldNotCorruptReturnValueScratchDuringNestedCallback() {
    // Given: ReturnValue obtained via TlScratchHolder.rv() with fields set
    ReturnValue outer = TlScratchHolder.rv();
    outer.setIsVoid(true);

    Obj obj = new Obj();
    obj.setValue("test-value");
    Class objClazz = new Class();
    objClazz.setName("java.lang.String");
    obj.setClazz(objClazz);
    outer.setObject(obj);

    Reflectable refl = new Reflectable();
    Method method = new Method();
    method.setName("getResult");
    refl.setMethod(method);
    outer.setFrom(refl);

    // When: Nested dispatch calls TlScratchHolder.rv() again on the same thread
    ReturnValue inner = TlScratchHolder.rv();

    // Then: The original ReturnValue reference is corrupted
    assertThat(inner, is(sameInstance(outer)));
    assertThat(outer.getIsVoid(), is(false));
    assertThat(outer.getObject(), is(nullValue()));
    assertThat(outer.getFrom(), is(nullValue()));
  }

  /**
   * Verifies that once a scratch object has been fully consumed (serialized to bytes), the
   * serialized bytes remain intact even after a nested dispatch reuses the scratch.
   *
   * <p>This documents the safe usage pattern: serialize/consume the ephemeral scratch object before
   * any operation that could trigger a nested dispatch.
   */
  @Test
  public void shouldSafelyReuseInterceptScratchesWhenConsumedBeforeNestedDispatch() {
    // Given: Ephemeral ExecMessage obtained via TlScratchHolder.exec(), populated,
    //        and fully consumed by marshaling to a byte[] via marshal()
    ExecMessage scratch = TlScratchHolder.exec();
    scratch.setPeerUuid("consumed-peer-uuid");
    scratch.setMessageId("consumed-msg-001");

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("consumedMethod");
    scratch.setInstanceMethodCall(imc);

    byte[] serialized = new byte[scratch.marshalFit()];
    int len = scratch.marshal(serialized, 0);

    // When: Nested dispatch occurs — TlScratchHolder.exec() is called again,
    //       resetting and repopulating the scratch with different values
    ExecMessage nested = TlScratchHolder.exec();
    nested.setPeerUuid("nested-peer-uuid");
    nested.setMessageId("nested-msg-002");

    InstanceMethodCall nestedImc = new InstanceMethodCall();
    nestedImc.setName("nestedMethod");
    nested.setInstanceMethodCall(nestedImc);

    // Then: The previously serialized bytes are intact and can be unmarshaled
    //       back to an ExecMessage with the original field values
    ExecMessage restored = new ExecMessage();
    restored.unmarshal(serialized, 0, len);

    assertThat(restored.getPeerUuid(), is("consumed-peer-uuid"));
    assertThat(restored.getMessageId(), is("consumed-msg-001"));
    assertThat(restored.getInstanceMethodCall(), is(notNullValue()));
    assertThat(restored.getInstanceMethodCall().getName(), is("consumedMethod"));

    // The scratch object itself now has the inner dispatch's values (expected)
    assertThat(scratch.getPeerUuid(), is("nested-peer-uuid"));
    assertThat(scratch.getMessageId(), is("nested-msg-002"));
  }

  /**
   * Documents behavior under triple-nested dispatch: outer -> BEFORE callback -> inner -> BEFORE
   * callback -> innermost.
   *
   * <p>At any given point, only the most recent {@code TlScratchHolder.exec()} result is valid.
   * Outer levels that need their ExecMessage preserved must clone before yielding control to a
   * nested dispatch.
   */
  @Test
  public void shouldHandleTripleNestedDispatchWithScratchReuse() {
    // Level 1 (outer): obtain scratch and populate
    ExecMessage level1Scratch = TlScratchHolder.exec();
    level1Scratch.setPeerUuid("outer-peer");
    level1Scratch.setMessageId("outer-msg");

    // Clone level 1 before yielding to nested dispatch
    byte[] buf1 = new byte[level1Scratch.marshalFit()];
    int len1 = level1Scratch.marshal(buf1, 0);
    ExecMessage level1Clone = new ExecMessage();
    level1Clone.unmarshal(buf1, 0, len1);

    // Level 2 (middle): obtain scratch and populate
    ExecMessage level2Scratch = TlScratchHolder.exec();
    level2Scratch.setPeerUuid("middle-peer");
    level2Scratch.setMessageId("middle-msg");

    // All exec() calls return the same underlying instance
    assertThat(level2Scratch, is(sameInstance(level1Scratch)));

    // Clone level 2 before yielding to innermost dispatch
    byte[] buf2 = new byte[level2Scratch.marshalFit()];
    int len2 = level2Scratch.marshal(buf2, 0);
    ExecMessage level2Clone = new ExecMessage();
    level2Clone.unmarshal(buf2, 0, len2);

    // Level 3 (innermost): obtain scratch and populate
    ExecMessage level3Scratch = TlScratchHolder.exec();
    level3Scratch.setPeerUuid("inner-peer");
    level3Scratch.setMessageId("inner-msg");

    assertThat(level3Scratch, is(sameInstance(level1Scratch)));

    // Each clone preserves its respective values
    assertThat(level1Clone.getPeerUuid(), is("outer-peer"));
    assertThat(level1Clone.getMessageId(), is("outer-msg"));

    assertThat(level2Clone.getPeerUuid(), is("middle-peer"));
    assertThat(level2Clone.getMessageId(), is("middle-msg"));

    // The scratch (live instance) has the innermost values
    assertThat(level1Scratch.getPeerUuid(), is("inner-peer"));
    assertThat(level1Scratch.getMessageId(), is("inner-msg"));
  }
}
