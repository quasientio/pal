/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.colfer.scratches;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Tests for the {@link TlScratchHolder#icbr()} accessor that provides a reusable, thread-local
 * {@link InterceptCallbackRequestMessage}.
 *
 * <p>These tests verify the core contract of thread-local scratch objects: reset-on-access, same
 * instance reuse within a thread, isolation across threads, and the known nested dispatch
 * corruption hazard.
 *
 * <p>Depends on intercept scratch objects implementation in TlScratchHolder/TlMsgScratch.
 *
 * @see TlScratchHolder
 * @see TlMsgScratch
 * @see TlScratchHolderInterceptNestedDispatchTest
 */
public class TlScratchHolderInterceptTest {

  /**
   * Verifies that {@link TlScratchHolder#icbr()} returns a non-null {@link
   * InterceptCallbackRequestMessage} with all fields reset to their default values.
   *
   * <p>The reset state means: all String fields are empty ({@code ""}), all byte/int fields are 0,
   * boolean fields are false, and all object references (exec, returnValue, thrownException) are
   * null.
   */
  @Test
  public void shouldProvideReusableInterceptCallbackRequestMessage() {
    // Given: TlScratchHolder on current thread (no prior calls)

    // When: TlScratchHolder.icbr() called
    InterceptCallbackRequestMessage icbr = TlScratchHolder.icbr();

    // Then: Returns non-null InterceptCallbackRequestMessage with all fields reset
    assertThat(icbr, is(notNullValue()));
    assertThat(icbr.getCallbackId(), is(""));
    assertThat(icbr.getPhase(), is((byte) 0));
    assertThat(icbr.getInterceptType(), is((byte) 0));
    assertThat(icbr.getInterceptedPeer().length, is(0));
    assertThat(icbr.getRegisteredCallbackId(), is(""));
    assertThat(icbr.getCallbackClass(), is(""));
    assertThat(icbr.getCallbackMethod(), is(""));
    assertThat(icbr.getExec(), is(nullValue()));
    assertThat(icbr.getReturnValue(), is(nullValue()));
    assertThat(icbr.getReturnValueRef(), is(0));
    assertThat(icbr.getIsVoid(), is(false));
    assertThat(icbr.getThrownException(), is(nullValue()));
    assertThat(icbr.getProceedTimeoutMs(), is(0));
  }

  /**
   * Verifies that repeated calls to {@link TlScratchHolder#icbr()} on the same thread return the
   * exact same object reference, confirming thread-local reuse (no new allocations).
   */
  @Test
  public void shouldReturnSameInstanceOnRepeatedCalls() {
    // Given: TlScratchHolder on current thread

    // When: TlScratchHolder.icbr() called twice
    InterceptCallbackRequestMessage first = TlScratchHolder.icbr();
    InterceptCallbackRequestMessage second = TlScratchHolder.icbr();

    // Then: Same object reference returned (assertSame)
    assertThat(second, is(sameInstance(first)));
  }

  /**
   * Verifies that after setting fields on an {@link InterceptCallbackRequestMessage} obtained from
   * {@link TlScratchHolder#icbr()}, a subsequent call to {@code icbr()} returns the same instance
   * with all fields reset to defaults.
   *
   * <p>This confirms the reset-on-access contract: each call to the accessor resets the scratch
   * object before returning it.
   */
  @Test
  public void shouldResetFieldsBetweenCalls() {
    // Given: InterceptCallbackRequestMessage obtained via TlScratchHolder.icbr()
    //        with fields set to non-default values
    InterceptCallbackRequestMessage icbr = TlScratchHolder.icbr();
    icbr.setCallbackId("test-callback-123");
    icbr.setPhase((byte) 1);
    icbr.setInterceptType((byte) 3);
    icbr.setInterceptedPeer(
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("peer-uuid-abc".getBytes(StandardCharsets.UTF_8))));
    icbr.setRegisteredCallbackId("reg-456");
    icbr.setCallbackClass("com.example.Handler");
    icbr.setCallbackMethod("onBefore");
    icbr.setExec(new ExecMessage());
    icbr.setReturnValue(new Obj());
    icbr.setReturnValueRef(42);
    icbr.setIsVoid(true);
    icbr.setThrownException(new RaisedThrowable());
    icbr.setProceedTimeoutMs(5000);

    // When: TlScratchHolder.icbr() called again
    InterceptCallbackRequestMessage resetIcbr = TlScratchHolder.icbr();

    // Then: All fields are reset to defaults
    assertThat(resetIcbr, is(sameInstance(icbr)));
    assertThat(resetIcbr.getCallbackId(), is(""));
    assertThat(resetIcbr.getPhase(), is((byte) 0));
    assertThat(resetIcbr.getInterceptType(), is((byte) 0));
    assertThat(resetIcbr.getInterceptedPeer().length, is(0));
    assertThat(resetIcbr.getRegisteredCallbackId(), is(""));
    assertThat(resetIcbr.getCallbackClass(), is(""));
    assertThat(resetIcbr.getCallbackMethod(), is(""));
    assertThat(resetIcbr.getExec(), is(nullValue()));
    assertThat(resetIcbr.getReturnValue(), is(nullValue()));
    assertThat(resetIcbr.getReturnValueRef(), is(0));
    assertThat(resetIcbr.getIsVoid(), is(false));
    assertThat(resetIcbr.getThrownException(), is(nullValue()));
    assertThat(resetIcbr.getProceedTimeoutMs(), is(0));
  }

  /**
   * Verifies that {@link TlScratchHolder#icbr()} returns different object instances on different
   * threads, confirming thread-local isolation.
   *
   * <p>Each thread has its own {@link TlMsgScratch}, so the {@link InterceptCallbackRequestMessage}
   * instances must be distinct across threads.
   */
  @Test
  public void shouldProvideIsolatedInstancesAcrossThreads() throws Exception {
    // Given: Two threads (main thread and a spawned thread)
    InterceptCallbackRequestMessage mainInstance = TlScratchHolder.icbr();

    // When: Both threads call TlScratchHolder.icbr()
    AtomicReference<InterceptCallbackRequestMessage> otherInstance = new AtomicReference<>();
    Thread thread = new Thread(() -> otherInstance.set(TlScratchHolder.icbr()));
    thread.start();
    thread.join();

    // Then: Different object references returned
    assertThat(otherInstance.get(), is(notNullValue()));
    assertThat(otherInstance.get(), is(not(sameInstance(mainInstance))));
  }

  /**
   * Documents the KNOWN hazard: a nested call to {@link TlScratchHolder#icbr()} on the same thread
   * resets/corrupts the outer InterceptCallbackRequestMessage because both calls return the same
   * underlying instance.
   *
   * <p>Scenario: An outer dispatch obtains an InterceptCallbackRequestMessage via {@code
   * TlScratchHolder.icbr()} and populates it. A nested dispatch (e.g., triggered by an intercept
   * callback resolving a method that itself is intercepted) also calls {@code
   * TlScratchHolder.icbr()}, resetting the same object. The first reference now sees reset fields.
   *
   * <p>This test formally documents this corruption hazard so that callers know they must clone or
   * serialize the scratch object before any operation that could trigger a nested dispatch.
   */
  @Test
  public void shouldBeCorruptedByNestedDispatch() {
    // Given: InterceptCallbackRequestMessage obtained via TlScratchHolder.icbr()
    //        with fields set
    InterceptCallbackRequestMessage outer = TlScratchHolder.icbr();
    outer.setCallbackId("outer-callback");
    outer.setPhase((byte) 2);
    outer.setInterceptType((byte) 1);
    outer.setInterceptedPeer(
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("outer-peer-uuid".getBytes(StandardCharsets.UTF_8))));
    outer.setCallbackClass("com.example.OuterHandler");
    outer.setCallbackMethod("onAfter");
    outer.setProceedTimeoutMs(3000);

    // When: Another TlScratchHolder.icbr() call occurs on the same thread
    //       (simulating a nested dispatch that needs its own
    //       InterceptCallbackRequestMessage)
    InterceptCallbackRequestMessage inner = TlScratchHolder.icbr();

    // Then: First reference now has reset fields (documents the hazard)
    assertThat(inner, is(sameInstance(outer)));
    assertThat(outer.getCallbackId(), is(""));
    assertThat(outer.getPhase(), is((byte) 0));
    assertThat(outer.getInterceptType(), is((byte) 0));
    assertThat(outer.getInterceptedPeer().length, is(0));
    assertThat(outer.getCallbackClass(), is(""));
    assertThat(outer.getCallbackMethod(), is(""));
    assertThat(outer.getProceedTimeoutMs(), is(0));
  }
}
