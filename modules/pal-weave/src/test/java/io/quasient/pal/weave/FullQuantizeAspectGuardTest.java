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
package io.quasient.pal.weave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Specification tests for the {@code TL_CURRENT_CALL_SIG} thread-local signature guard in {@link
 * FullQuantizeAspect}.
 *
 * <p>The guard holds the join-point signature of the innermost active call-site advice on the
 * current thread. Execution-site advice uses it to distinguish the "woven-to-woven direct call"
 * case (signatures match — skip dispatch) from "unwoven caller" cases such as reflection and method
 * references (no match — execution-site must dispatch itself).
 *
 * <p>These tests verify the guard's invariants under nested, concurrent, and exceptional scenarios
 * using the save-prev / restore-prev pattern employed by the call-site advice.
 */
public class FullQuantizeAspectGuardTest {

  /** Ensures the thread-local starts fresh on the test thread. */
  @Before
  public void resetBefore() {
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.remove();
  }

  /** Removes any state so the signature does not leak into other tests on the same thread. */
  @After
  public void resetAfter() {
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.remove();
  }

  /**
   * Verifies the guard slot is initialized to {@code null} on a fresh thread.
   *
   * <p>This is the baseline invariant: any thread that has never touched the slot must observe
   * {@code null}, representing the "no call-site advice active" state.
   */
  @Test
  public void shouldInitializeSignatureToNull() {
    assertNull(
        "Fresh thread-local must initialize to null", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies a single save / set / restore sequence returns the slot to {@code null}.
   *
   * <p>Models the simplest call-site advice lifecycle: enter advice (save prev, set own sig), exit
   * advice (restore prev in finally).
   */
  @Test
  public void shouldSetAndRestoreSignature() {
    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    String prev = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("sigA");
    assertEquals("sigA", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(prev);

    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies nested save / set / restore sequences preserve the outer signature.
   *
   * <p>Models woven→woven→woven call chains: each nested call-site advice saves the current slot
   * value, writes its own signature, and on exit restores what it found. After all exits, the slot
   * must be back to its starting value.
   */
  @Test
  public void shouldRestorePreviousSignatureOnNestedCalls() {
    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    String prev1 = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("outer");
    assertEquals("outer", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    String prev2 = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("middle");
    assertEquals("middle", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    String prev3 = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("inner");
    assertEquals("inner", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(prev3);
    assertEquals("middle", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(prev2);
    assertEquals("outer", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(prev1);
    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the slot is thread-local and changes on one thread do not leak to others.
   *
   * <p>Concurrent peer threads must not interfere with each other's guard state, otherwise
   * execution-site advice on one thread could be incorrectly suppressed by call-site activity on
   * another.
   */
  @Test
  public void shouldIsolateSignaturesPerThread() throws InterruptedException {
    final CountDownLatch mainSet = new CountDownLatch(1);
    final CountDownLatch workerChecked = new CountDownLatch(1);
    final AtomicReference<String> workerObserved = new AtomicReference<>("sentinel");

    Thread worker =
        new Thread(
            () -> {
              try {
                mainSet.await(5, TimeUnit.SECONDS);
                workerObserved.set(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                FullQuantizeAspect.TL_CURRENT_CALL_SIG.remove();
                workerChecked.countDown();
              }
            });
    worker.start();

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("mainSig");
    mainSet.countDown();

    workerChecked.await(5, TimeUnit.SECONDS);
    worker.join(5_000);

    assertNull(
        "Worker thread must observe its own fresh slot, not the main thread's value",
        workerObserved.get());
    assertEquals(
        "Main thread's slot must remain unchanged by worker thread",
        "mainSig",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the slot is correctly restored when the guarded body throws.
   *
   * <p>The advice wraps dispatch in a try/finally; an exception thrown from within the guarded
   * region must still result in the slot being restored to its pre-advice value, so subsequent
   * advice on the same thread observes the correct signature.
   */
  @Test
  public void shouldRestoreSignatureOnExceptionPath() {
    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("outer");
    assertEquals("outer", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    assertThrows(
        RuntimeException.class,
        () -> {
          String prev = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
          FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("inner");
          try {
            throw new RuntimeException("simulated dispatch failure");
          } finally {
            FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(prev);
          }
        });

    assertEquals(
        "Slot must be restored to the outer value on the exceptional return path",
        "outer",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies {@link FullQuantizeAspect#beginRuntimeInvoke()} installs the sentinel by reference and
   * returns the previous value (here {@code null}), and {@link
   * FullQuantizeAspect#endRuntimeInvoke(String)} restores it.
   *
   * <p>The sentinel identity (checked with {@code ==} by the exec-site guard) is load-bearing: it
   * is what lets the aspect distinguish "PAL runtime owns this invocation" from any real join-point
   * signature string.
   */
  @Test
  public void shouldInstallSentinelAndRestorePreviousValue() {
    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    String prev = FullQuantizeAspect.beginRuntimeInvoke();
    try {
      assertNull("beginRuntimeInvoke must return the prior value (null on a fresh slot)", prev);
      assertSame(
          "Slot must hold the sentinel by reference after beginRuntimeInvoke",
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
          FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
    } finally {
      FullQuantizeAspect.endRuntimeInvoke(prev);
    }

    assertNull(
        "Slot must be restored to its pre-invoke value after endRuntimeInvoke",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies nested runtime-invoke scopes preserve state: the inner {@code
   * beginRuntimeInvoke}/{@code endRuntimeInvoke} pair leaves the outer sentinel in place.
   *
   * <p>This models the runtime reentering its own reflective-invoke path (e.g., an incoming RPC
   * whose body triggers another incoming RPC on the same thread).
   */
  @Test
  public void shouldPreserveOuterSentinelAcrossNestedRuntimeInvokes() {
    String outerPrev = FullQuantizeAspect.beginRuntimeInvoke();
    try {
      assertSame(
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL, FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

      String innerPrev = FullQuantizeAspect.beginRuntimeInvoke();
      try {
        assertSame(
            "Nested begin must save the sentinel it found",
            FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
            innerPrev);
        assertSame(
            "Slot must still hold the sentinel during the nested scope",
            FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
            FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
      } finally {
        FullQuantizeAspect.endRuntimeInvoke(innerPrev);
      }

      assertSame(
          "Outer sentinel must remain in place after the nested scope exits",
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
          FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
    } finally {
      FullQuantizeAspect.endRuntimeInvoke(outerPrev);
    }

    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies that when the slot already holds a call-site signature, {@code beginRuntimeInvoke}
   * saves it and {@code endRuntimeInvoke} restores it.
   *
   * <p>This path corresponds to the runtime performing a reflective invoke from within a woven
   * caller on the same thread (exotic but possible): the call-site's signature must not be
   * clobbered.
   */
  @Test
  public void shouldSaveAndRestoreCallSignatureAcrossRuntimeInvoke() {
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("sig-of-outer-call");

    String prev = FullQuantizeAspect.beginRuntimeInvoke();
    try {
      assertEquals(
          "beginRuntimeInvoke must save the call-site signature", "sig-of-outer-call", prev);
      assertSame(
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL, FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
    } finally {
      FullQuantizeAspect.endRuntimeInvoke(prev);
    }

    assertEquals(
        "Call-site signature must be restored after endRuntimeInvoke",
        "sig-of-outer-call",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the sentinel survives a nested call-site save/restore cycle.
   *
   * <p>Inside a reflectively-invoked target body, woven-to-woven calls trigger call-site advice,
   * which uses the save-prev/restore-prev pattern. This test simulates that: begin runtime-invoke
   * (sentinel installed), perform a save/set/restore as call-site advice would, and verify the
   * sentinel is still in place afterwards so subsequent exec-site checks still skip dispatch.
   */
  @Test
  public void shouldKeepSentinelVisibleAcrossNestedCallSiteSaveRestore() {
    String runtimePrev = FullQuantizeAspect.beginRuntimeInvoke();
    try {
      assertSame(
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL, FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

      String callSitePrev = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
      FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("sig-of-nested-call");
      try {
        assertEquals(
            "Nested call-site advice writes its own signature",
            "sig-of-nested-call",
            FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
      } finally {
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(callSitePrev);
      }

      assertSame(
          "Sentinel must be restored by the call-site advice's finally block",
          FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
          FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
    } finally {
      FullQuantizeAspect.endRuntimeInvoke(runtimePrev);
    }

    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the slot is correctly restored when the runtime-guarded body throws.
   *
   * <p>{@code MethodDispatcher.invokeIncoming} wraps {@code method.invoke} in a try/finally; a
   * thrown exception (e.g., {@link java.lang.reflect.InvocationTargetException}) must still result
   * in the sentinel being replaced by the previously-held value.
   */
  @Test
  public void shouldRestorePreviousValueOnExceptionInGuardedBody() {
    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("pre-existing-sig");

    assertThrows(
        RuntimeException.class,
        () -> {
          String prev = FullQuantizeAspect.beginRuntimeInvoke();
          try {
            assertSame(
                FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
                FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
            throw new RuntimeException("simulated reflective-invoke failure");
          } finally {
            FullQuantizeAspect.endRuntimeInvoke(prev);
          }
        });

    assertEquals(
        "Pre-existing slot value must be restored on the exceptional return path",
        "pre-existing-sig",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the sentinel is recognized by reference identity, not string equality.
   *
   * <p>The exec-site guard uses {@code ==} to distinguish the runtime-installed sentinel from any
   * real join-point signature. A user-created string whose value happens to equal the sentinel's
   * characters must therefore be a different object, guaranteeing it cannot masquerade as the
   * sentinel.
   */
  @Test
  public void shouldDistinguishSentinelByReferenceIdentity() {
    String lookalike = new String(FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL);
    assertEquals(
        "Lookalike must be .equals() to sentinel",
        FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
        lookalike);
    assertNotSame(
        "Lookalike must be a distinct object, so == sentinel check cannot collide",
        FullQuantizeAspect.RUNTIME_INVOKE_SENTINEL,
        lookalike);
  }

  /**
   * Verifies that {@link FullQuantizeAspect#proceedWithClearedSlot(ProceedingJoinPoint)} clears the
   * slot for the duration of {@code pjp.proceed()} and restores the previous value afterwards.
   *
   * <p>This is the load-bearing property for the recursion-via-unwoven-layer case. When an outer
   * call-site has set the slot to a method's key and the matching execution-site takes the skip
   * path, the body must run with the slot cleared. Otherwise, an unwoven re-entry into the same
   * method from inside the body — for example, {@code Method.invoke} called on the same method —
   * would observe the still-set key and have its own execution-site advice incorrectly suppress the
   * dispatch.
   */
  @Test
  public void shouldClearSlotDuringProceedAndRestoreAfter() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    AtomicReference<String> observedDuringProceed = new AtomicReference<>("UNSET");
    when(pjp.proceed())
        .thenAnswer(
            invocation -> {
              observedDuringProceed.set(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
              return null;
            });

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("outer-call-sig");

    FullQuantizeAspect.proceedWithClearedSlot(pjp);

    assertNull(
        "Slot must be cleared for the duration of pjp.proceed()", observedDuringProceed.get());
    assertEquals(
        "Slot must be restored to its prior value after proceed returns",
        "outer-call-sig",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the helper restores the previous slot value when {@code pjp.proceed()} throws.
   *
   * <p>Without this guarantee, a body that fails (e.g., {@link
   * java.lang.reflect.InvocationTargetException} percolating up from a reflective inner call) would
   * leave the slot at {@code null}, breaking the call-site advice's expectation that its {@code
   * finally} restores from the value seen at advice entry — a subsequent execution-site advice on
   * the same thread could then mis-decide skip vs. dispatch.
   */
  @Test
  public void shouldRestoreSlotOnExceptionFromProceedWithClearedSlot() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    RuntimeException thrown = new RuntimeException("simulated body failure");
    when(pjp.proceed()).thenThrow(thrown);

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("outer-call-sig");

    RuntimeException caught =
        assertThrows(RuntimeException.class, () -> FullQuantizeAspect.proceedWithClearedSlot(pjp));
    assertSame("Original throwable must propagate unchanged", thrown, caught);
    assertEquals(
        "Slot must be restored to its prior value even when proceed throws",
        "outer-call-sig",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies the helper is a no-op on observable slot state when the slot was already {@code null}
   * (the common "exec-site advice fired with no in-flight outer dispatch" case).
   */
  @Test
  public void shouldLeaveSlotNullWhenAlreadyNull() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    AtomicReference<String> observedDuringProceed = new AtomicReference<>("UNSET");
    when(pjp.proceed())
        .thenAnswer(
            invocation -> {
              observedDuringProceed.set(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
              return null;
            });

    assertNull(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());

    FullQuantizeAspect.proceedWithClearedSlot(pjp);

    assertNull("Slot remains null during proceed", observedDuringProceed.get());
    assertNull(
        "Slot remains null after proceed returns", FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }

  /**
   * Verifies that a nested call-site save/set/restore cycle inside the helper's proceed composes
   * correctly with the outer clear: the inner cycle sees a {@code null} pre-value, installs and
   * restores its own key, and the outer slot is restored to its pre-skip value at the end.
   *
   * <p>This mirrors the in-production flow where the body of an exec-site skip-path contains
   * woven-to-woven calls that go through their own call-site advice.
   */
  @Test
  public void shouldComposeWithNestedCallSiteSaveRestoreInsideProceed() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    AtomicReference<String> observedInnerPrev = new AtomicReference<>("UNSET");
    AtomicReference<String> observedAtInnerSet = new AtomicReference<>("UNSET");
    AtomicReference<String> observedAfterInnerRestore = new AtomicReference<>("UNSET");

    when(pjp.proceed())
        .thenAnswer(
            invocation -> {
              String innerPrev = FullQuantizeAspect.TL_CURRENT_CALL_SIG.get();
              observedInnerPrev.set(innerPrev);
              FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("inner-call-sig");
              try {
                observedAtInnerSet.set(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
              } finally {
                FullQuantizeAspect.TL_CURRENT_CALL_SIG.set(innerPrev);
              }
              observedAfterInnerRestore.set(FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
              return null;
            });

    FullQuantizeAspect.TL_CURRENT_CALL_SIG.set("outer-call-sig");

    FullQuantizeAspect.proceedWithClearedSlot(pjp);

    assertNull(
        "Inner call-site's saved prev must be the cleared (null) slot, not the outer key",
        observedInnerPrev.get());
    assertEquals(
        "Inner call-site observes its own key after setting it",
        "inner-call-sig",
        observedAtInnerSet.get());
    assertNull(
        "After the inner call-site's restore, slot is back to the cleared value (null)",
        observedAfterInnerRestore.get());
    assertEquals(
        "Outer slot is restored after proceedWithClearedSlot returns",
        "outer-call-sig",
        FullQuantizeAspect.TL_CURRENT_CALL_SIG.get());
  }
}
