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
   * Exercises all six invocation paths and prints a single {@code results: ...} marker line.
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

    System.out.println("results: " + r1 + "|" + r2 + "|" + r3 + "|" + r4 + "|" + r5 + "|" + r6);
  }
}
