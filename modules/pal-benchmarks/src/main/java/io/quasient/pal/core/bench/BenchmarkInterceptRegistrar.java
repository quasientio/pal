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
package io.quasient.pal.core.bench;

import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.core.intercept.InterceptMatcher;
import io.quasient.pal.core.internal.messages.InterceptEventMsg;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptableMethod;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Utility class that encapsulates intercept registration logic for benchmarks.
 *
 * <p>This class isolates the benchmark from internal intercept registration APIs (ZMQ REP socket
 * protocol, InterceptEventMsg wire format, InterceptMessage construction), providing a stable
 * interface that can be updated in one place if those internals change.
 *
 * <p>Intercepts are registered via the InterceptMatcher's ZMQ REP socket at the configured {@code
 * inproc://intercept_reg} address. Each registration sends a serialized {@link InterceptMessage}
 * and waits for a response code confirming success.
 *
 * @see InterceptMatcher
 * @see FeatureSetVariant
 */
public final class BenchmarkInterceptRegistrar {

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkInterceptRegistrar.class);

  /**
   * Class pattern matching all methods in the benchmark target class.
   *
   * <p>This pattern targets the woven {@code QuantizedCalls} class that benchmarks invoke (both
   * {@code toUpperCase} and {@code sort}).
   */
  static final String BENCHMARK_CLASS_PATTERN =
      "bench.quantized.io.quasient.foobar.apps.QuantizedCalls";

  /**
   * Benchmark method signatures to register intercepts for.
   *
   * <p>Each entry is a pair of (methodName, parameterTypes). The intercept matching system requires
   * exact parameter type matching, so each benchmark method must be registered separately.
   */
  private static final String[][] BENCHMARK_METHODS = {
    {"toUpperCase", "java.lang.String"},
    {"sort", "double[]"},
  };

  /** Fully qualified callback class for BEFORE intercepts. */
  static final String BEFORE_CALLBACK_CLASS =
      "callbacks.bench.quantized.io.quasient.foobar.apps.BenchmarkBeforeCallback";

  /** Callback method name for BEFORE intercepts. */
  static final String BEFORE_CALLBACK_METHOD = "onBefore";

  /** Fully qualified callback class for AFTER intercepts. */
  static final String AFTER_CALLBACK_CLASS =
      "callbacks.bench.quantized.io.quasient.foobar.apps.BenchmarkAfterCallback";

  /** Callback method name for AFTER intercepts. */
  static final String AFTER_CALLBACK_METHOD = "onAfter";

  /** Fully qualified callback class for AROUND intercepts. */
  static final String AROUND_CALLBACK_CLASS =
      "callbacks.bench.quantized.io.quasient.foobar.apps.BenchmarkAroundCallback";

  /** Callback method name for AROUND intercepts. */
  static final String AROUND_CALLBACK_METHOD = "onAround";

  /** Counter for generating unique intercept message IDs within a peer. */
  private static final AtomicLong MESSAGE_ID_COUNTER = new AtomicLong(0);

  /** Private constructor to prevent instantiation. */
  private BenchmarkInterceptRegistrar() {}

  /**
   * Registers benchmark intercepts for the given variant.
   *
   * <p>Based on the variant, this method registers BEFORE, AFTER, AROUND, or combinations thereof.
   * All intercepts target {@code QuantizedCalls.*} with the callback peer set to the local peer
   * UUID.
   *
   * <p>Registration occurs via the InterceptMatcher's ZMQ REP socket at the given address. A ZMQ
   * REQ socket is created, connected, used to register all required intercepts, and then closed.
   *
   * @param variant the benchmark variant determining which intercept types to register
   * @param peerUuid the local peer UUID (used as callback peer)
   * @param zmqCtx the shared ZMQ context
   * @param interceptRegAddress the inproc address of the InterceptMatcher's REP socket
   * @return the number of intercepts successfully registered
   * @throws IllegalStateException if any intercept registration fails
   */
  public static int registerIntercepts(
      FeatureSetVariant variant, String peerUuid, ZContext zmqCtx, String interceptRegAddress) {

    InterceptType[] types = getInterceptTypes(variant);
    if (types.length == 0) {
      return 0;
    }

    ZMQ.Socket reqSocket = zmqCtx.createSocket(SocketType.REQ);
    try {
      reqSocket.connect(interceptRegAddress);

      int registered = 0;
      for (InterceptType type : types) {
        for (String[] methodSpec : BENCHMARK_METHODS) {
          String methodName = methodSpec[0];
          String[] paramTypes = new String[] {methodSpec[1]};
          InterceptMessage message = buildInterceptMessage(type, peerUuid, methodName, paramTypes);
          InterceptEventMsg eventMsg = new InterceptEventMsg(message);

          boolean sent = eventMsg.send(reqSocket);
          if (!sent) {
            throw new IllegalStateException(
                "Failed to send intercept registration for " + type.name() + "." + methodName);
          }

          String response = reqSocket.recvStr();
          if (InterceptMatcher.REGISTER_OK_RESPONSE.equals(response)
              || InterceptMatcher.REGISTER_ASYNC_PENDING_RESPONSE.equals(response)) {
            registered++;
            logger.info(
                "Registered {} intercept for benchmark method {}({}) (response={})",
                type.name(),
                methodName,
                paramTypes[0],
                response);
          } else {
            throw new IllegalStateException(
                "Intercept registration failed for "
                    + type.name()
                    + "."
                    + methodName
                    + " with response code: "
                    + response);
          }
        }
      }
      return registered;
    } finally {
      reqSocket.close();
    }
  }

  /**
   * Returns the intercept types to register for the given variant.
   *
   * @param variant the benchmark variant
   * @return an array of intercept types to register (empty if none needed)
   */
  static InterceptType[] getInterceptTypes(FeatureSetVariant variant) {
    return switch (variant) {
      case INTERCEPTS_BEFORE, INTERCEPTS_BEFORE_IN_FLIGHT ->
          new InterceptType[] {InterceptType.BEFORE};
      case INTERCEPTS_AFTER -> new InterceptType[] {InterceptType.AFTER};
      case INTERCEPTS_AROUND -> new InterceptType[] {InterceptType.AROUND};
      case INTERCEPTS_BEFORE_AFTER ->
          new InterceptType[] {InterceptType.BEFORE, InterceptType.AFTER};
      case INTERCEPTS_ALL ->
          new InterceptType[] {InterceptType.BEFORE, InterceptType.AFTER, InterceptType.AROUND};
      case INTERCEPTS_BEFORE_ASYNC -> new InterceptType[] {InterceptType.BEFORE_ASYNC};
      case INTERCEPTS_AFTER_ASYNC -> new InterceptType[] {InterceptType.AFTER_ASYNC};
      default -> new InterceptType[0];
    };
  }

  /**
   * Returns whether the given variant requires intercept registration during benchmark setup.
   *
   * @param variant the benchmark variant to check
   * @return true if the variant requires intercept registration
   */
  public static boolean requiresInterceptRegistration(FeatureSetVariant variant) {
    return getInterceptTypes(variant).length > 0;
  }

  /**
   * Builds an {@link InterceptMessage} for the given intercept type and method signature.
   *
   * <p>The message targets a specific method on {@code QuantizedCalls} and uses the appropriate
   * no-op callback handler. The {@code forceImmediate} flag is set to {@code true} to ensure
   * registration completes synchronously during benchmark setup (before any dispatches start),
   * regardless of whether in-flight tracking is enabled.
   *
   * @param type the intercept type (BEFORE, AFTER, AROUND, etc.)
   * @param peerUuid the local peer UUID for callback dispatch
   * @param methodName the target method name
   * @param paramTypes the target method's parameter types
   * @return a fully configured InterceptMessage ready for registration
   */
  static InterceptMessage buildInterceptMessage(
      InterceptType type, String peerUuid, String methodName, String[] paramTypes) {
    String callbackClass;
    String callbackMethod;
    switch (type) {
      case BEFORE, BEFORE_ASYNC -> {
        callbackClass = BEFORE_CALLBACK_CLASS;
        callbackMethod = BEFORE_CALLBACK_METHOD;
      }
      case AFTER, AFTER_ASYNC -> {
        callbackClass = AFTER_CALLBACK_CLASS;
        callbackMethod = AFTER_CALLBACK_METHOD;
      }
      case AROUND -> {
        callbackClass = AROUND_CALLBACK_CLASS;
        callbackMethod = AROUND_CALLBACK_METHOD;
      }
      default -> throw new IllegalArgumentException("Unsupported intercept type: " + type);
    }

    String messageId = peerUuid + "-bench-" + MESSAGE_ID_COUNTER.getAndIncrement();

    return new InterceptMessage()
        .withPeerUuid(peerUuid)
        .withMessageId(messageId)
        .withInterceptType(type.toByte())
        .withClazz(BENCHMARK_CLASS_PATTERN)
        .withMethod(new InterceptableMethod().withName(methodName).withParameterTypes(paramTypes))
        .withCallbackClass(callbackClass)
        .withCallbackMethod(callbackMethod)
        .withForceImmediate(true);
  }
}
