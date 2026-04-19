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
package io.quasient.foobar.apps.quantized.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Test application that exercises the supplementary {@code execution()} pointcuts woven into {@code
 * FullQuantizeAspect}.
 *
 * <p>Each path invokes a distinct target method so integration tests can assert the presence or
 * absence of specific method names in the recorded WAL:
 *
 * <ol>
 *   <li>{@link #normalMethod(String)} — invoked via a direct, woven-to-woven call. Used to verify
 *       that the thread-local guard suppresses double-dispatch (exactly one WAL entry per
 *       invocation).
 *   <li>{@link #reflectedInstanceMethod(String)} — invoked via {@link Method#invoke(Object,
 *       Object...)} on an instance. The call site is unwoven (inside {@code java.lang.reflect});
 *       the method body must be captured by execution-site advice.
 *   <li>{@link #methodReferenceTarget(String)} — invoked through a method reference bound to a
 *       {@link Function}. The {@code invokedynamic}/{@code LambdaMetafactory} call site is not a
 *       direct call; execution-site advice on the target body captures it.
 *   <li>{@link #reflectedStaticMethod(String)} — static method invoked via {@link
 *       Method#invoke(Object, Object...)} with a {@code null} receiver.
 *   <li>{@link #lambdaTarget(String)} — invoked from inside a lambda body. Verifies that regardless
 *       of lambda metafactory/JIT shape, execution-site advice on the callee captures the call.
 *   <li>{@link #ExecutionPointcutApp(String)} — invoked via {@link
 *       Constructor#newInstance(Object...)}. The reflective instantiation bypasses call-site
 *       constructor weaving; execution-site constructor advice on the body captures it.
 *   <li>{@link #recursiveCount(int)} — directly recursive instance method. Verifies that the
 *       call-site/execution-site guard keys still agree for each frame of a self-recursion, so the
 *       WAL contains exactly one OPERATION entry per recursion level, not two.
 *   <li>{@link VirtualBase#virtualMethod(String)} overridden by {@link
 *       VirtualSub#virtualMethod(String)}, invoked through the base-typed reference. The call-site
 *       sees {@code VirtualBase} statically but the runtime receiver is a {@code VirtualSub}. The
 *       guard key must use the runtime receiver class so call-site and execution-site agree —
 *       otherwise virtual dispatch would produce two WAL entries per call.
 *   <li>{@link VirtualIface#ifaceMethod(String)} implemented by {@link VirtualIfaceImpl}, invoked
 *       through the interface-typed reference. Same concern as virtual dispatch with an additional
 *       wrinkle: the call-site's declaring type is the interface while the execution-site runs on
 *       the concrete impl. The runtime-class-keyed guard must align both sides.
 * </ol>
 *
 * <p>The app prints a single-line {@code results: ...} marker that encodes each path's return
 * value, so recording / replay tests can assert deterministic output.
 */
public class ExecutionPointcutApp {

  /** Marker string assigned by a constructor; used to verify constructor reflection captured. */
  private String marker;

  /**
   * Default no-arg constructor used for the app instance created in {@link #main(String[])}.
   *
   * <p>This constructor is invoked via a normal {@code new} expression (call-site weaving). The
   * {@link #ExecutionPointcutApp(String)} overload is invoked via reflection in the constructor
   * reflection path.
   */
  public ExecutionPointcutApp() {
    this.marker = "default";
  }

  /**
   * Constructor invoked via {@link Constructor#newInstance(Object...)} in the reflective
   * construction path.
   *
   * @param marker an arbitrary string stored on the instance and returned via {@link #getMarker()}
   */
  public ExecutionPointcutApp(String marker) {
    this.marker = marker;
  }

  /**
   * Returns the marker string assigned by the invoked constructor.
   *
   * @return the marker string
   */
  public String getMarker() {
    return marker;
  }

  /**
   * Target for the direct, woven-to-woven call path. Used to verify no double-dispatch.
   *
   * @param input the input string
   * @return {@code "n:" + input}
   */
  public String normalMethod(String input) {
    return "n:" + input;
  }

  /**
   * Target for the instance-method reflection path. Invoked via {@link Method#invoke(Object,
   * Object...)}.
   *
   * @param input the input string
   * @return {@code "r:" + input}
   */
  public String reflectedInstanceMethod(String input) {
    return "r:" + input;
  }

  /**
   * Target for the method-reference path. Invoked via a {@link Function} constructed from a method
   * reference.
   *
   * @param input the input string
   * @return {@code "mr:" + input}
   */
  public String methodReferenceTarget(String input) {
    return "mr:" + input;
  }

  /**
   * Target for the lambda-captured-call path. Invoked from inside a lambda body.
   *
   * @param input the input string
   * @return {@code "l:" + input}
   */
  public String lambdaTarget(String input) {
    return "l:" + input;
  }

  /**
   * Target for the static-reflection path. Invoked via {@link Method#invoke(Object, Object...)}
   * with a {@code null} receiver.
   *
   * @param input the input string
   * @return {@code "s:" + input}
   */
  public static String reflectedStaticMethod(String input) {
    return "s:" + input;
  }

  /**
   * Directly recursive instance method used to verify that the thread-local guard correctly pairs
   * call-site and execution-site keys at every recursion depth.
   *
   * <p>For an input of {@code n}, the method is invoked {@code n + 1} times (depths {@code n}
   * through {@code 0}). The WAL should therefore contain exactly {@code n + 1} OPERATION entries
   * for this method — not {@code 2 * (n + 1)}, which would indicate the exec-site advice
   * double-dispatched at every frame.
   *
   * @param n the recursion depth (non-negative)
   * @return the number of nested calls made (equal to {@code n})
   */
  public int recursiveCount(int n) {
    if (n <= 0) {
      return 0;
    }
    return 1 + recursiveCount(n - 1);
  }

  /**
   * Base class for the virtual-dispatch scenario. Its {@link #virtualMethod(String)} is overridden
   * by {@link VirtualSub}.
   */
  public static class VirtualBase {
    /**
     * Base implementation of the virtual method. Deliberately not invoked in the happy-path of
     * {@link ExecutionPointcutApp#main(String[])} — a {@link VirtualSub} overrides this.
     *
     * @param input the input string
     * @return {@code "base:" + input}
     */
    public String virtualMethod(String input) {
      return "base:" + input;
    }
  }

  /**
   * Subclass that overrides {@link VirtualBase#virtualMethod(String)}. Used to verify that a call
   * made through a {@link VirtualBase}-typed reference on a {@link VirtualSub} instance produces
   * exactly one WAL entry — the runtime-class-keyed guard must align call-site and execution-site
   * keys under virtual dispatch.
   */
  public static class VirtualSub extends VirtualBase {
    @Override
    public String virtualMethod(String input) {
      return "sub:" + input;
    }
  }

  /**
   * Interface used for the interface-dispatch scenario. Implemented by {@link VirtualIfaceImpl}.
   */
  public interface VirtualIface {
    /**
     * Interface method to be implemented. Call sites that reference {@code VirtualIface} resolve
     * their static type to the interface, while the execution happens on the concrete impl — the
     * guard key must pick the runtime class so both sides agree.
     *
     * @param input the input string
     * @return an implementation-specific string
     */
    String ifaceMethod(String input);
  }

  /**
   * Concrete implementation of {@link VirtualIface}. Its method body runs at the execution-site
   * when the call is made through a {@link VirtualIface}-typed reference.
   */
  public static class VirtualIfaceImpl implements VirtualIface {
    @Override
    public String ifaceMethod(String input) {
      return "iface:" + input;
    }
  }

  /**
   * Fixed recursion depth exercised by {@code main}; {@code RECURSION_DEPTH + 1} calls are made.
   */
  public static final int RECURSION_DEPTH = 3;

  /**
   * Exercises all invocation paths (direct, reflective, method-reference, static reflection,
   * lambda, reflective constructor, recursion, virtual dispatch, interface dispatch) and prints a
   * single {@code results: ...} marker line.
   *
   * @param args command-line arguments (ignored)
   * @throws ReflectiveOperationException if reflective invocation fails
   */
  public static void main(String[] args) throws ReflectiveOperationException {
    ExecutionPointcutApp app = new ExecutionPointcutApp();

    String r1 = app.normalMethod("hello");

    Method instanceMethod =
        ExecutionPointcutApp.class.getMethod("reflectedInstanceMethod", String.class);
    String r2 = (String) instanceMethod.invoke(app, "world");

    Function<String, String> ref = app::methodReferenceTarget;
    String r3 = ref.apply("ref");

    Method staticMethod =
        ExecutionPointcutApp.class.getMethod("reflectedStaticMethod", String.class);
    String r4 = (String) staticMethod.invoke(null, "static");

    Function<String, String> lam = x -> app.lambdaTarget(x);
    String r5 = lam.apply("lam");

    Constructor<ExecutionPointcutApp> ctor =
        ExecutionPointcutApp.class.getConstructor(String.class);
    ExecutionPointcutApp reflectedApp = ctor.newInstance("ctor-marker");
    String r6 = reflectedApp.getMarker();

    int r7 = app.recursiveCount(RECURSION_DEPTH);

    VirtualBase virtualRef = new VirtualSub();
    String r8 = virtualRef.virtualMethod("vd");

    VirtualIface ifaceRef = new VirtualIfaceImpl();
    String r9 = ifaceRef.ifaceMethod("id");

    System.out.println(
        "results: "
            + r1
            + "|"
            + r2
            + "|"
            + r3
            + "|"
            + r4
            + "|"
            + r5
            + "|"
            + r6
            + "|"
            + r7
            + "|"
            + r8
            + "|"
            + r9);
  }
}
