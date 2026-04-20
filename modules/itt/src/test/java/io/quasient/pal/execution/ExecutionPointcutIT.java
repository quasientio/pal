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
   * <p>Order matches the paths exercised in {@code main}: direct call, reflected instance,
   * method-reference, reflected static, lambda-captured, reflected constructor marker, recursion
   * count (3 from {@code RECURSION_DEPTH}), virtual dispatch (override wins), interface dispatch.
   */
  private static final String EXPECTED_MARKER =
      "results: n:hello|r:world|mr:ref|s:static|l:lam|ctor-marker|3|sub:vd|iface:id";

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

    int virtualCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualSub", "virtualMethod");
    logger.info("VirtualSub.virtualMethod OPERATION entries: {}", virtualCount);

    assertEquals(
        "virtualMethod should appear exactly once under virtual dispatch (no double-dispatch)",
        1,
        virtualCount);
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

    int ifaceCount =
        countOperationEntries(indexResult.stdout(), APP_CLASS + "$VirtualIfaceImpl", "ifaceMethod");
    logger.info("VirtualIfaceImpl.ifaceMethod OPERATION entries: {}", ifaceCount);

    assertEquals(
        "ifaceMethod should appear exactly once under interface dispatch (no double-dispatch)",
        1,
        ifaceCount);
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
