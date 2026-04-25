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
package io.quasient.pal.execution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests verifying end-to-end behavior of the supplementary {@code execution()}
 * pointcuts added to {@code FullQuantizeAspect}.
 *
 * <p>Each test records a WAL produced by {@code ExecutionPointcutApp} and then inspects the WAL
 * using {@code pal log index --verbose}. The verbose output prints one line per entry in the format
 * {@code [offset] kind [ENTRY_POINT?] threadName className.executableName(paramTypes)}, which lets
 * tests assert the presence (or exact count) of specific method or constructor executions.
 *
 * <p>Uses the Chronicle backend exclusively; the test app is deterministic and single-threaded, so
 * the recorded WAL is stable across runs on both backends.
 */
public class ExecutionPointcutIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(ExecutionPointcutIT.class);

  /** Fully qualified main class of the test application. */
  private static final String MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.execution.ExecutionPointcutApp";

  /** Unqualified class name used to match WAL entries belonging to the test app. */
  private static final String APP_CLASS = "ExecutionPointcutApp";

  /**
   * Expected output marker produced by {@code ExecutionPointcutApp#main}.
   *
   * <p>Order matches the paths exercised in {@code main}: direct call, reflected instance, bound
   * method reference, reflected static, lambda-captured, unbound method reference, static method
   * reference, reflected constructor marker, then six recursion variants each returning {@code
   * RECURSION_DEPTH} (direct, reflective, lambda, method-handle, static reflective, mutual
   * reflective starting from A), then virtual dispatch (override wins), interface dispatch, and
   * framework-style callback via {@code Thread.start()}.
   */
  private static final String EXPECTED_MARKER =
      "results: n:hello|r:world|mr:ref|s:static|l:lam"
          + "|ur:unb|sr:sref|ctor-marker|3|3|3|3|3|3|sub:vd|iface:id|fc:fw";

  /** Recursion depth used by the app; total number of {@code recursiveCount} frames is this + 1. */
  private static final int RECURSION_DEPTH = 3;

  /**
   * Creates a unique Chronicle WAL spec and registers the path for cleanup.
   *
   * @param prefix a descriptive prefix for the WAL directory name
   * @return the WAL spec (e.g., {@code file:/tmp/pal-exec-<id>})
   */
  private String createWalSpec(String prefix) {
    String path = "/tmp/pal-" + prefix + "-" + generateId();
    trackChronicleLog(path);
    return "file:" + path;
  }

  /**
   * Records a WAL by running the test application with the given arguments.
   *
   * <p>CLI bootstrap writes are disabled via {@code --no-wal-incoming-cli} so the WAL contains only
   * operations initiated by the application itself.
   *
   * @param walSpec the Chronicle WAL file spec
   * @param appArgs arguments passed to {@link
   *     io.quasient.foobar.apps.quantized.execution.ExecutionPointcutApp}
   * @return the peer process result
   * @throws Exception if the peer fails to start or exit
   */
  private ProcessResult recordWal(String walSpec, String... appArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("--wal");
    args.add(walSpec);
    args.add("--no-wal-incoming-cli");
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs {@code pal log index --verbose} against the given WAL.
   *
   * @param walSpec the Chronicle WAL file spec
   * @return the CLI process result
   * @throws Exception if the CLI invocation fails
   */
  private CliProcessResult doVerboseIndex(String walSpec) throws Exception {
    return runLogIndex("--verbose", walSpec);
  }

  /**
   * Counts the number of verbose {@code OPERATION} lines whose signature mentions the given simple
   * method or constructor name on {@link #APP_CLASS}.
   *
   * <p>The verbose format is {@code [offset] OPERATION [ENTRY_POINT?] threadName
   * ClassName.executableName(paramTypes)}. The match is done with {@code contains} on the marker
   * {@code APP_CLASS + "." + executableName + "("} so partial names (e.g., {@code "normal"}) do not
   * accidentally match a longer method.
   *
   * @param output the stdout from {@code pal log index --verbose}
   * @param executableName the simple method or constructor name (e.g., {@code "normalMethod"} or
   *     {@code "init>"} — constructors appear as {@code "<init>"})
   * @return the number of OPERATION entries matching
   */
  private int countOperationEntries(String output, String executableName) {
    return countOperationEntries(output, APP_CLASS, executableName);
  }

  /**
   * Counts the number of verbose {@code OPERATION} lines whose signature matches {@code className +
   * "." + executableName + "("}. Use this overload when the target class is a nested class of
   * {@link #APP_CLASS} (rendered as {@code ExecutionPointcutApp$NestedName} in the WAL verbose
   * output) or any other class whose short name is not {@link #APP_CLASS}.
   *
   * @param output the stdout from {@code pal log index --verbose}
   * @param className the short class name as it appears in verbose output (e.g., {@code
   *     "ExecutionPointcutApp$VirtualSub"})
   * @param executableName the simple method or constructor name
   * @return the number of OPERATION entries matching
   */
  private int countOperationEntries(String output, String className, String executableName) {
    String marker = className + "." + executableName + "(";
    long count =
        output.lines().filter(line -> line.contains(" OPERATION") && line.contains(marker)).count();
    return (int) count;
  }

  /**
   * Records a WAL with the full test application and asserts that the direct woven-to-woven call to
   * {@code normalMethod} produces exactly one OPERATION entry — i.e., the thread-local guard
   * prevents the execution-site advice from dispatching a duplicate.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldNotDoubleDispatchOnNormalWovenCall() throws Exception {
    String walSpec = createWalSpec("exec-no-dup");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int normalMethodCount = countOperationEntries(indexResult.stdout(), "normalMethod");
    logger.info("normalMethod OPERATION entries: {}", normalMethodCount);

    assertEquals(
        "normalMethod should appear exactly once (no double-dispatch)", 1, normalMethodCount);
  }

  /**
   * Records a WAL with the test application and asserts that {@code Method.invoke} on the instance
   * method {@code reflectedInstanceMethod} produces exactly one OPERATION entry for the target
   * method.
   *
   * <p>This verifies both dimensions of the fix in a single assertion:
   *
   * <ul>
   *   <li><strong>Captured:</strong> At least one entry exists — the execution-site advice fires
   *       inside the target body even though the call site is inside {@code java.lang.reflect}
   *       (unwoven).
   *   <li><strong>Not double-dispatched:</strong> Exactly one entry exists — the guard does not
   *       allow a second dispatch from any other path for this invocation.
   * </ul>
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureReflectionInvocationWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-reflect-inst");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "reflectedInstanceMethod");
    logger.info("reflectedInstanceMethod OPERATION entries: {}", count);

    assertEquals(
        "reflectedInstanceMethod must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts that {@code Method.invoke(null, ...)} on a
   * static method produces exactly one OPERATION entry for the target method.
   *
   * <p>Verifies both capture (entry exists — execution-site advice fires even though the call site
   * is inside {@code java.lang.reflect}) and no double-dispatch (entry count is exactly one).
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureStaticMethodReflectionWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-reflect-static");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "reflectedStaticMethod");
    logger.info("reflectedStaticMethod OPERATION entries: {}", count);

    assertEquals(
        "reflectedStaticMethod must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts that a <strong>bound</strong> method
   * reference ({@code obj::methodReferenceTarget}) invoked through a {@link
   * java.util.function.Function} produces exactly one OPERATION entry for the target method.
   *
   * <p>The {@code invokedynamic} / {@link java.lang.invoke.LambdaMetafactory} call site is resolved
   * into a runtime-generated class that is not woven by the compile-time aspect; only
   * execution-site advice on the target body captures the invocation. The stricter assertion
   * ({@code == 1}) additionally guards against a regression where a second dispatch path ever fires
   * for the same invocation.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureMethodReferenceWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-method-ref");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "methodReferenceTarget");
    logger.info("methodReferenceTarget OPERATION entries: {}", count);

    assertEquals(
        "methodReferenceTarget must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts that an <strong>unbound</strong> method
   * reference ({@code ExecutionPointcutApp::unboundRefTarget}) invoked through a {@link
   * java.util.function.BiFunction} produces exactly one OPERATION entry for the target method.
   *
   * <p>Unbound method references compile to a different {@code invokedynamic} shape than bound
   * ones: the receiver is passed as the first argument of the functional interface's abstract
   * method rather than captured at construction time. The runtime-generated {@code BiFunction}
   * implementation is not woven, so only execution-site advice on the callee can capture the
   * invocation — the assertion verifies both capture and the no-double-dispatch guarantee.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureUnboundMethodReferenceWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-unbound-ref");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "unboundRefTarget");
    logger.info("unboundRefTarget OPERATION entries: {}", count);

    assertEquals(
        "unboundRefTarget must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts that a <strong>static</strong> method
   * reference ({@code ExecutionPointcutApp::staticRefTarget}) invoked through a {@link
   * java.util.function.Function} produces exactly one OPERATION entry for the target method.
   *
   * <p>Static method references compile to yet another {@code invokedynamic} shape. The
   * runtime-generated {@code Function} implementation is not woven, and the target has no receiver
   * — so only execution-site advice on the static body can capture the invocation.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureStaticMethodReferenceWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-static-ref");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "staticRefTarget");
    logger.info("staticRefTarget OPERATION entries: {}", count);

    assertEquals(
        "staticRefTarget must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts that a woven method invoked from within a
   * lambda body ({@code x -> app.lambdaTarget(x)}) produces exactly one OPERATION entry.
   *
   * <p>Regardless of how the JVM's lambda metafactory shapes the caller's bytecode, execution-site
   * advice on the callee body guarantees capture, and the thread-local guard ensures no second
   * dispatch for the same invocation.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureLambdaCapturedMethodCallWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-lambda");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "lambdaTarget");
    logger.info("lambdaTarget OPERATION entries: {}", count);

    assertEquals(
        "lambdaTarget must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL with the test application and asserts the reflective {@code
   * Constructor.newInstance} path is observable in the WAL as a call-site {@code
   * Constructor.newInstance} entry, and that the reflected constructor actually ran (the output
   * marker contains {@code ctor-marker} returned via {@link
   * io.quasient.foobar.apps.quantized.execution.ExecutionPointcutApp#getMarker()}).
   *
   * <p>The reflective constructor body itself is <em>not</em> captured by execution-site advice:
   * AspectJ's {@code @Around execution(new(..))} must wrap the constructor body in a synthetic
   * method, which breaks {@code final} field assignment semantics for any woven class. The aspect
   * therefore omits constructor execution-site advice. Reflective construction is observable
   * indirectly through the {@code Constructor.newInstance} method call-site.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureReflectiveConstructorPath() throws Exception {
    String walSpec = createWalSpec("exec-ctor-reflect");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Reflective constructor must have actually run (ctor-marker in output)",
        recordResult.stdout(),
        containsString("ctor-marker"));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    long newInstanceCount =
        indexResult
            .stdout()
            .lines()
            .filter(line -> line.contains(" OPERATION") && line.contains("Constructor.newInstance"))
            .count();
    logger.info("Constructor.newInstance OPERATION entries: {}", newInstanceCount);

    assertThat(
        "Constructor.newInstance call-site must appear in WAL",
        (int) newInstanceCount,
        greaterThanOrEqualTo(1));
  }

  /**
   * Records a WAL with the test application and asserts that a directly recursive method produces
   * exactly {@code RECURSION_DEPTH + 1} OPERATION entries — one per recursion frame. A
   * double-dispatch regression (for example, an execution-site key that did not match its call-site
   * key at the same frame) would inflate this count to {@code 2 * (RECURSION_DEPTH + 1)}.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldNotDoubleDispatchOnRecursiveCall() throws Exception {
    String walSpec = createWalSpec("exec-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int recursiveCount = countOperationEntries(indexResult.stdout(), "recursiveCount");
    logger.info("recursiveCount OPERATION entries: {}", recursiveCount);

    assertEquals(
        "recursiveCount should appear exactly " + (RECURSION_DEPTH + 1) + " times (one per frame)",
        RECURSION_DEPTH + 1,
        recursiveCount);
  }

  /**
   * Records a WAL with the test application and asserts that {@code reflectiveRecursiveCount}
   * (whose nested call goes through {@code Method.invoke} rather than a direct woven call) produces
   * exactly {@code RECURSION_DEPTH + 1} OPERATION entries — one per recursion frame.
   *
   * <p>This is the regression test for the "recursion via an unwoven layer" bug: before the fix,
   * the outer call-site advice set the thread-local guard to this method's key for the entire
   * dispatch chain, and the inner reflective re-entry (for which no call-site advice fires because
   * {@code java.lang.reflect} is unwoven) was suppressed by the matching execution-site guard. Only
   * the outermost invocation made it into the WAL, so the count was {@code 1} instead of {@code
   * RECURSION_DEPTH + 1}.
   *
   * <p>The fix routes the execution-site advice's skip path through {@code proceedWithClearedSlot},
   * which clears the slot for the duration of the body's proceed and restores it afterwards —
   * letting the inner exec-site observe a fresh slot and dispatch.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureReflectiveRecursionWithoutSuppression() throws Exception {
    String walSpec = createWalSpec("exec-reflect-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int reflectiveRecursiveCount =
        countOperationEntries(indexResult.stdout(), "reflectiveRecursiveCount");
    logger.info("reflectiveRecursiveCount OPERATION entries: {}", reflectiveRecursiveCount);

    assertEquals(
        "reflectiveRecursiveCount should appear exactly "
            + (RECURSION_DEPTH + 1)
            + " times (one per frame; reflective inner frames must not be suppressed by the"
            + " outer dispatch's still-set guard)",
        RECURSION_DEPTH + 1,
        reflectiveRecursiveCount);
  }

  /**
   * Records a WAL with the test application and asserts that a recursive method whose nested call
   * goes through a bound {@link java.util.function.Function} method reference (held as a field)
   * produces exactly {@code RECURSION_DEPTH + 1} OPERATION entries — one per recursion frame.
   *
   * <p>Exercises the most common real-world shape of "unwoven re-entry": a delegate field captured
   * in the constructor and invoked from inside the same method's body. The synthetic {@code
   * Function} implementation generated by {@code LambdaMetafactory} is unwoven, so the recursive
   * frame is captured only by execution-site advice on the target. With the slot cleared during the
   * outer body's proceed, the inner exec-site fires correctly.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureLambdaRecursionWithoutSuppression() throws Exception {
    String walSpec = createWalSpec("exec-lambda-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int lambdaRecursiveCount = countOperationEntries(indexResult.stdout(), "lambdaRecursiveCount");
    logger.info("lambdaRecursiveCount OPERATION entries: {}", lambdaRecursiveCount);

    assertEquals(
        "lambdaRecursiveCount should appear exactly "
            + (RECURSION_DEPTH + 1)
            + " times (one per frame; the synthetic Function bridge is unwoven so each frame"
            + " must be captured by execution-site advice)",
        RECURSION_DEPTH + 1,
        lambdaRecursiveCount);
  }

  /**
   * Records a WAL with the test application and asserts that a recursive method whose nested call
   * goes through {@link java.lang.invoke.MethodHandle#invokeExact} produces exactly {@code
   * RECURSION_DEPTH + 1} OPERATION entries — one per recursion frame.
   *
   * <p>Exercises the {@code java.lang.invoke} re-entry path independently of {@code
   * java.lang.reflect}. The two have distinct bootstrap and JIT shapes; verifying both
   * independently catches future changes that special-case one but not the other.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureMethodHandleRecursionWithoutSuppression() throws Exception {
    String walSpec = createWalSpec("exec-mh-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int methodHandleRecursiveCount =
        countOperationEntries(indexResult.stdout(), "methodHandleRecursiveCount");
    logger.info("methodHandleRecursiveCount OPERATION entries: {}", methodHandleRecursiveCount);

    assertEquals(
        "methodHandleRecursiveCount should appear exactly "
            + (RECURSION_DEPTH + 1)
            + " times (one per frame; MethodHandle.invokeExact is unwoven so each frame must"
            + " be captured by execution-site advice)",
        RECURSION_DEPTH + 1,
        methodHandleRecursiveCount);
  }

  /**
   * Records a WAL with the test application and asserts that a recursive <em>static</em> method
   * whose nested call goes through {@link java.lang.reflect.Method#invoke} (with a {@code null}
   * receiver) produces exactly {@code RECURSION_DEPTH + 1} OPERATION entries — one per recursion
   * frame.
   *
   * <p>Exercises the static-method execution-site advice ({@code
   * aroundVoid/NonVoidClassMethodsExec}) on the recursion path. The instance variants exercise the
   * instance advice; this variant pins down that the static advice clears the slot during proceed
   * in the same way.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureStaticReflectiveRecursionWithoutSuppression() throws Exception {
    String walSpec = createWalSpec("exec-static-reflect-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int staticReflectiveRecursiveCount =
        countOperationEntries(indexResult.stdout(), "staticReflectiveRecursiveCount");
    logger.info(
        "staticReflectiveRecursiveCount OPERATION entries: {}", staticReflectiveRecursiveCount);

    assertEquals(
        "staticReflectiveRecursiveCount should appear exactly "
            + (RECURSION_DEPTH + 1)
            + " times (one per frame; static-method exec-site advice must clear the slot during"
            + " proceed in the same way as the instance variant)",
        RECURSION_DEPTH + 1,
        staticReflectiveRecursiveCount);
  }

  /**
   * Records a WAL with the test application and asserts that mutual recursion through an unwoven
   * layer (A → reflective B → reflective A → reflective B → …) produces exactly {@code
   * RECURSION_DEPTH + 1} OPERATION entries summed across both methods, with each method appearing
   * the expected number of times.
   *
   * <p>This is the load-bearing case: while inside A's outer dispatch, A's key sits in the
   * thread-local. A's body invokes B reflectively; B's exec-site sees a non-matching key and
   * dispatches. Then B's body invokes A reflectively, and the inner A's exec-site fires with the
   * outer A's key still in the slot (in the buggy code path) — silently suppressing the inner A's
   * dispatch. With the slot cleared during the outer body's proceed, the inner A is captured.
   *
   * <p>For {@code RECURSION_DEPTH = 3} the chain is A(3) → B(2) → A(1) → B(0), which is two A
   * frames and two B frames; total {@code RECURSION_DEPTH + 1 = 4}. A reversion of the fix would
   * leave the inner A(1) uncaptured, so {@code A} would count 1 instead of 2.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureMutualReflectiveRecursionWithoutSuppression() throws Exception {
    String walSpec = createWalSpec("exec-mutual-reflect-recursive");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int aCount = countOperationEntries(indexResult.stdout(), "mutualReflectiveCountA");
    int bCount = countOperationEntries(indexResult.stdout(), "mutualReflectiveCountB");
    logger.info("mutualReflectiveCountA OPERATION entries: {}", aCount);
    logger.info("mutualReflectiveCountB OPERATION entries: {}", bCount);

    int expectedA = (RECURSION_DEPTH / 2) + 1;
    int expectedB = (RECURSION_DEPTH + 1) / 2;
    assertEquals(
        "mutualReflectiveCountA should appear exactly "
            + expectedA
            + " times (chain starts at A and alternates; a reversion of the slot-clear fix would"
            + " drop the inner A frame, leaving the count short by one)",
        expectedA,
        aCount);
    assertEquals(
        "mutualReflectiveCountB should appear exactly " + expectedB + " times", expectedB, bCount);
    assertEquals(
        "Total mutual frames (A + B) should equal RECURSION_DEPTH + 1",
        RECURSION_DEPTH + 1,
        aCount + bCount);
  }

  /**
   * Records a WAL with the test application and asserts that a virtual-dispatch call to an
   * overridden method (made through a base-typed reference on a subclass instance) produces exactly
   * one OPERATION entry.
   *
   * <p>This is the regression test for the call-site/execution-site key mismatch fixed in this
   * change: before the fix, the call-site keyed on the static base type while the execution-site
   * keyed on the runtime subclass, so the guard failed to suppress exec-site dispatch and the same
   * invocation was recorded twice. With the runtime-class-keyed guard, both sides agree and the WAL
   * contains exactly one entry.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldNotDoubleDispatchOnVirtualDispatch() throws Exception {
    String walSpec = createWalSpec("exec-virtual");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker (override wins on virtual dispatch)",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Count entries at both the declared call-site type and the runtime receiver type.
    // The guard suppresses exec-site dispatch, so only the call-site records an entry — and the
    // call-site uses the declared type. A regression of the guard would produce a second entry at
    // the runtime receiver type.
    int baseCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualBase", "virtualMethod");
    int subCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualSub", "virtualMethod");
    logger.info(
        "virtualMethod OPERATION entries: VirtualBase={}, VirtualSub={}", baseCount, subCount);

    assertEquals(
        "virtualMethod should appear exactly once under virtual dispatch (no double-dispatch)",
        1,
        baseCount + subCount);
  }

  /**
   * Records a WAL with the test application and asserts that an interface-dispatch call (made
   * through an interface-typed reference on a concrete implementation) produces exactly one
   * OPERATION entry.
   *
   * <p>Analogous to {@link #shouldNotDoubleDispatchOnVirtualDispatch()} but with an interface
   * declaration rather than an overridden class method. The call-site's declaring type is the
   * interface while the execution-site runs on the concrete implementation; the runtime-class-keyed
   * guard must align both sides.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldNotDoubleDispatchOnInterfaceDispatch() throws Exception {
    String walSpec = createWalSpec("exec-iface");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker (impl runs on interface dispatch)",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Count entries at both the declared interface type and the runtime implementation type.
    // See shouldNotDoubleDispatchOnVirtualDispatch for rationale.
    int ifaceCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualIface", "ifaceMethod");
    int implCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualIfaceImpl", "ifaceMethod");
    logger.info(
        "ifaceMethod OPERATION entries: VirtualIface={}, VirtualIfaceImpl={}",
        ifaceCount,
        implCount);

    assertEquals(
        "ifaceMethod should appear exactly once under interface dispatch (no double-dispatch)",
        1,
        ifaceCount + implCount);
  }

  /**
   * Records a WAL with the test application and asserts that the framework-style callback
   * dispatched via {@link Thread#start()} produces exactly one OPERATION entry for {@code
   * frameworkCallbackTarget}.
   *
   * <p>This is the closest in-process stand-in for genuinely unwoven framework callbacks (Quarkus
   * scheduler, JavaFX event-dispatch thread, Spring MVC handler): the JVM's native {@code
   * Thread.run()} bridge invokes the {@code Runnable} body, and the runtime-generated lambda class
   * that implements {@code Runnable} is not woven. The target method on the application class is
   * therefore reachable only through execution-site advice. Asserting exactly one entry covers both
   * capture (the entry exists) and the no-double-dispatch guarantee (no other path adds a second
   * entry for the same invocation).
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldCaptureFrameworkStyleCallbackWithoutDoubleDispatch() throws Exception {
    String walSpec = createWalSpec("exec-framework");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker (framework callback ran)",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    CliProcessResult indexResult = doVerboseIndex(walSpec);
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    int count = countOperationEntries(indexResult.stdout(), "frameworkCallbackTarget");
    logger.info("frameworkCallbackTarget OPERATION entries: {}", count);

    assertEquals(
        "frameworkCallbackTarget must appear in WAL exactly once"
            + " (captured by execution advice, not double-dispatched)",
        1,
        count);
  }

  /**
   * Records a WAL produced by the test application (which exercises direct calls, reflection,
   * method references, lambdas, and reflective constructor invocation) and replays it, asserting
   * zero divergences. Verifies that execution-site PJPs produce WAL entries compatible with the
   * replay oracle.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldReplaySuccessfullyWithExecutionPointcuts() throws Exception {
    String walSpec = createWalSpec("exec-replay");

    ProcessResult recordResult = recordWal(walSpec);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output marker",
        recordResult.stdout(),
        containsString(EXPECTED_MARKER));

    List<String> replayArgs = new ArrayList<>();
    replayArgs.add("--wal");
    replayArgs.add(walSpec);
    replayArgs.add("-cp");
    replayArgs.add(getIttAppsClasspath());
    replayArgs.add(MAIN_CLASS);
    CliProcessResult replayResult = runReplay(replayArgs.toArray(new String[0]));

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay should reproduce expected output marker",
        replayResult.stdout(),
        containsString(EXPECTED_MARKER));
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Regression check: records and replays {@code MinimalReceiptCalculator} (a pre-existing replay
   * test app that predates execution-site pointcuts) with the updated aspect. Verifies the guard
   * mechanism preserves call-site semantics for the normal path — recording and replay must still
   * complete with zero divergences and the expected output marker.
   *
   * @throws Exception if any step fails
   */
  @Test
  public void shouldNotRegressExistingReplayScenarios() throws Exception {
    String walSpec = createWalSpec("exec-regress");
    String legacyMainClass = "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";
    String legacyArg = "milk:2,bread:1,apple:5";
    String legacyMarker = "Run 1:";

    List<String> recordArgs = new ArrayList<>();
    recordArgs.add("-d");
    recordArgs.add(getPalDirectoryUrl());
    recordArgs.add("--wal");
    recordArgs.add(walSpec);
    recordArgs.add("--no-wal-incoming-cli");
    recordArgs.add("-cp");
    recordArgs.add(getIttAppsClasspath());
    recordArgs.add(legacyMainClass);
    recordArgs.add(legacyArg);
    ProcessResult recordResult = runPeer(recordArgs.toArray(new String[0]));
    assertEquals("Legacy recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Legacy recording should produce expected output marker",
        recordResult.stdout(),
        containsString(legacyMarker));

    List<String> replayArgs = new ArrayList<>();
    replayArgs.add("--wal");
    replayArgs.add(walSpec);
    replayArgs.add("-cp");
    replayArgs.add(getIttAppsClasspath());
    replayArgs.add(legacyMainClass);
    replayArgs.add(legacyArg);
    CliProcessResult replayResult = runReplay(replayArgs.toArray(new String[0]));

    assertEquals("Legacy replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Legacy replay should reproduce expected output marker",
        replayResult.stdout(),
        containsString(legacyMarker));
    assertThat(
        "Legacy replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Legacy replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }
}
