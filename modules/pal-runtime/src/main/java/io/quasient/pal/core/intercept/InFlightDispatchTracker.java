/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks in-flight dispatch operations to enable coordinated intercept activation with guaranteed
 * quiescence.
 *
 * <p>This class provides thread-safe tracking of method, constructor, and field operation
 * dispatches as they enter and exit execution. It supports:
 *
 * <ul>
 *   <li><b>Counter tracking:</b> Tracks dispatch entry/exit per dispatch key using {@link
 *       LongAdder} for low-contention counting
 *   <li><b>Parameter-type-aware keys:</b> Dispatch keys incorporate parameter types so that
 *       overloaded methods (e.g., {@code add(int)} vs {@code add(int,int)}) have separate counters
 *   <li><b>Pattern matching:</b> Supports wildcard patterns for class and method names using
 *       AntPathMatcher (e.g., "com.example.*", "*.Calculator.add")
 *   <li><b>Quiescence waiting:</b> Blocks until all matching dispatches complete with configurable
 *       timeout
 *   <li><b>Fencing mechanism:</b> Blocks new dispatches matching a pattern using {@link
 *       ReentrantLock} with {@link Condition}
 * </ul>
 *
 * <p><b>Key format:</b>
 *
 * <table>
 *   <tr><th>Operation</th><th>Key Format</th><th>Example</th></tr>
 *   <tr><td>Method (with params)</td><td>{@code className.methodName(p1,p2)}</td>
 *       <td>{@code com.example.Calc.add(int,java.lang.String)}</td></tr>
 *   <tr><td>Method (no params)</td><td>{@code className.methodName()}</td>
 *       <td>{@code com.example.Calc.reset()}</td></tr>
 *   <tr><td>Constructor</td><td>{@code className.new(p1)}</td>
 *       <td>{@code com.example.Foo.new(int)}</td></tr>
 *   <tr><td>Constructor (no-arg)</td><td>{@code className.new()}</td>
 *       <td>{@code com.example.Foo.new()}</td></tr>
 *   <tr><td>Field op</td><td>{@code className.fieldName}</td>
 *       <td>{@code com.example.Foo.myField}</td></tr>
 * </table>
 *
 * <p>The presence or absence of parentheses distinguishes methods/constructors from field
 * operations.
 *
 * <p><b>Thread safety:</b> All public methods are thread-safe and may be called concurrently from
 * multiple threads. Counters use {@link LongAdder} for high-performance concurrent increments.
 * Fencing uses {@link ReentrantLock} per dispatch key to avoid blocking unrelated dispatches.
 *
 * <p><b>Usage example:</b>
 *
 * <pre>{@code
 * InFlightDispatchTracker tracker = ...;
 *
 * // In dispatch hot-path (method with params):
 * tracker.enterDispatch("com.example.Calculator", "add", new String[]{"int", "int"});
 * try {
 *   // ... execute method ...
 * } finally {
 *   tracker.exitDispatch("com.example.Calculator", "add", new String[]{"int", "int"});
 * }
 *
 * // Field operation (null parameterTypes):
 * tracker.enterDispatch("com.example.Foo", "myField", null);
 *
 * // When registering intercept:
 * tracker.startFencing("com.example.Calculator", "add", new String[]{"int", "int"});
 * try {
 *   boolean quiescent = tracker.waitForQuiescence(
 *       "com.example.Calculator", "add", new String[]{"int", "int"}, 5000);
 *   if (quiescent) {
 *     // ... activate intercept ...
 *   }
 * } finally {
 *   tracker.stopFencing("com.example.Calculator", "add", new String[]{"int", "int"});
 * }
 * }</pre>
 *
 * <p><b>Performance considerations:</b> This class is designed for use on the dispatch hot-path.
 * Counter operations use {@link LongAdder}, which provides excellent performance under contention.
 * Pattern matching is only performed when checking for in-flight dispatches or starting fencing,
 * not on every enter/exit.
 *
 * @see io.quasient.pal.core.options.RunOptions#WITH_IN_FLIGHT_TRACKING
 * @see io.quasient.pal.common.directory.nodes.InterceptRequest#isForceImmediate()
 */
@Singleton
public class InFlightDispatchTracker {

  /** Logger instance for debug and trace messages. */
  private static final Logger logger = LoggerFactory.getLogger(InFlightDispatchTracker.class);

  /**
   * Encapsulates counter and lock for a single dispatch key (className.methodName).
   *
   * <p>This inner class combines the in-flight counter with a lock/condition for fencing. Each
   * dispatch key has its own {@code DispatchCounter} to avoid lock contention between unrelated
   * dispatches.
   */
  private static class DispatchCounter {
    /** Counter for in-flight dispatches. Uses LongAdder for low-contention concurrent updates. */
    private final LongAdder count = new LongAdder();

    /** Lock for fencing mechanism. Held by threads entering a fenced dispatch. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Condition for waiting until fencing is lifted. */
    private final Condition notFenced = lock.newCondition();

    /** Flag indicating whether new dispatches are fenced (blocked). */
    private volatile boolean fenced = false;

    /**
     * Increments the in-flight counter.
     *
     * <p>This method is lock-free and designed for high-throughput dispatch tracking.
     */
    void increment() {
      count.increment();
    }

    /**
     * Decrements the in-flight counter.
     *
     * <p>This method is lock-free and designed for high-throughput dispatch tracking.
     */
    void decrement() {
      count.decrement();
    }

    /**
     * Returns the current count of in-flight dispatches.
     *
     * @return the sum of all increments minus decrements
     */
    long sum() {
      return count.sum();
    }

    /**
     * Blocks if this dispatch is currently fenced, waiting until the fence is lifted.
     *
     * <p>This method is called before entering a dispatch. If fencing is active, the calling thread
     * will block until {@link #stopFencing()} is called or the thread is interrupted.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    void awaitNotFenced() throws InterruptedException {
      if (!fenced) {
        return; // Fast path: no fencing active
      }
      lock.lock();
      try {
        while (fenced) {
          notFenced.await();
        }
      } finally {
        lock.unlock();
      }
    }

    /**
     * Starts fencing for this dispatch, blocking all new {@link #awaitNotFenced()} calls.
     *
     * <p>Fencing does not affect dispatches that have already entered (already incremented the
     * counter). It only blocks new dispatches.
     */
    void startFencing() {
      lock.lock();
      try {
        fenced = true;
      } finally {
        lock.unlock();
      }
    }

    /**
     * Stops fencing for this dispatch, unblocking all threads waiting in {@link #awaitNotFenced()}.
     */
    void stopFencing() {
      lock.lock();
      try {
        fenced = false;
        notFenced.signalAll();
      } finally {
        lock.unlock();
      }
    }
  }

  /**
   * Map from dispatch key to counter/lock structure.
   *
   * <p>Keys use the format described in the class Javadoc: {@code className.methodName(p1,p2)} for
   * methods/constructors with parameters, {@code className.methodName()} for no-arg
   * methods/constructors, and {@code className.fieldName} for field operations.
   *
   * <p>This map grows dynamically as new dispatches are tracked. Entries are never removed to avoid
   * race conditions and to optimize for the common case where the same methods are called
   * repeatedly.
   */
  private final ConcurrentHashMap<String, DispatchCounter> dispatchCounters =
      new ConcurrentHashMap<>();

  /**
   * Map of active fence patterns.
   *
   * <p>When a pattern is added to this map, any enterDispatch call matching the pattern will be
   * fenced, even if the specific dispatch key doesn't exist in dispatchCounters yet.
   */
  private final ConcurrentHashMap<String, Boolean> fencedPatterns = new ConcurrentHashMap<>();

  /**
   * Matcher for comparing dot-separated patterns.
   *
   * <p>Configured to match case-insensitively with token trimming, consistent with intercept
   * pattern matching elsewhere in the codebase (see {@link InterceptRequestEntry}).
   *
   * <p><b>Thread safety:</b> This shared static instance is safe for concurrent use. {@code
   * AntPathMatcherArrays} (v1.0.0) is an immutable, effectively thread-safe class: all instance
   * fields are {@code final}, there is no mutable state (no caches, buffers, or counters), and
   * {@code isMatch()} is a pure function that operates only on method-local {@code char[]} arrays
   * and final fields. See {@link AntPathMatcherThreadSafetyTest} for empirical verification.
   */
  private static final AntPathMatcherArrays matcher =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  /**
   * Thread-local {@link StringBuilder} reused for building dispatch keys, eliminating per-call
   * allocations of stream pipelines and intermediate strings.
   *
   * <p>Each thread gets its own {@code StringBuilder} instance that is reset (via {@link
   * StringBuilder#setLength(int)}) before each use. This avoids the overhead of {@link
   * java.util.Arrays#stream(Object[])} and {@link
   * java.util.stream.Collectors#joining(CharSequence)} that the original implementation used.
   */
  private static final ThreadLocal<StringBuilder> TL_KEY_BUILDER =
      ThreadLocal.withInitial(() -> new StringBuilder(128));

  /**
   * Records that a dispatch has entered execution for the specified class, method, and parameter
   * types.
   *
   * <p>This method increments the in-flight counter for the dispatch key built from the class name,
   * method name, and parameter types. If fencing is active for this dispatch, the calling thread
   * will block until the fence is lifted.
   *
   * <p><b>Usage:</b> Call this method immediately before executing a method, constructor, or field
   * operation, typically at the start of the dispatch hot-path.
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently from
   * multiple threads.
   *
   * @param className the fully qualified class name (e.g., "com.example.Calculator")
   * @param executableName the method, constructor ("new"), or field name (e.g., "add")
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   * @throws InterruptedException if the thread is interrupted while waiting for fencing to be
   *     lifted
   */
  public void enterDispatch(String className, String executableName, String[] parameterTypes)
      throws InterruptedException {
    String key = buildKey(className, executableName, parameterTypes);

    // Check if there's a fenced pattern that matches this key
    boolean shouldFence = false;
    for (String pattern : fencedPatterns.keySet()) {
      if (matchesPattern(pattern, key)) {
        shouldFence = true;
        break;
      }
    }

    DispatchCounter counter;
    if (shouldFence) {
      // Create counter with fencing if it doesn't exist
      counter =
          dispatchCounters.computeIfAbsent(
              key,
              k -> {
                DispatchCounter newCounter = new DispatchCounter();
                newCounter.startFencing();
                return newCounter;
              });
    } else {
      counter = dispatchCounters.computeIfAbsent(key, k -> new DispatchCounter());
    }

    // Block if fenced
    counter.awaitNotFenced();

    // Increment counter
    counter.increment();

    if (logger.isTraceEnabled()) {
      logger.trace("Entered dispatch for {}, count={}", key, counter.sum());
    }
  }

  /**
   * Records that a dispatch has exited execution for the specified class, method, and parameter
   * types.
   *
   * <p>This method decrements the in-flight counter for the dispatch key. If no counter exists for
   * this key, this method logs a warning but does not throw an exception.
   *
   * <p><b>Usage:</b> Call this method in a {@code finally} block after dispatch execution to ensure
   * the counter is always decremented, even if an exception occurs.
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently from
   * multiple threads.
   *
   * @param className the fully qualified class name (e.g., "com.example.Calculator")
   * @param executableName the method, constructor ("new"), or field name (e.g., "add")
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   */
  public void exitDispatch(String className, String executableName, String[] parameterTypes) {
    String key = buildKey(className, executableName, parameterTypes);
    DispatchCounter counter = dispatchCounters.get(key);

    if (counter == null) {
      if (logger.isWarnEnabled()) {
        logger.warn(
            "exitDispatch called for {} but no counter exists (missing enterDispatch?)", key);
      }
      return;
    }

    counter.decrement();

    if (logger.isTraceEnabled()) {
      logger.trace("Exited dispatch for {}, count={}", key, counter.sum());
    }
  }

  /**
   * Checks whether any dispatches are currently in-flight for operations matching the specified
   * pattern.
   *
   * <p>This method searches all tracked dispatch keys for matches against the pattern built from
   * the class pattern, method pattern, and parameter types. The pattern may contain wildcards
   * (e.g., "com.example.*" or "*.Calculator.add").
   *
   * <p><b>Pattern matching rules:</b>
   *
   * <ul>
   *   <li>{@code "*"} matches any sequence of characters within a single dot-separated segment
   *   <li>{@code "**"} matches any sequence of segments (not typically needed for class.method
   *       patterns)
   *   <li>Matching is case-insensitive
   *   <li>Leading/trailing whitespace in patterns is ignored
   *   <li>Parameter types are exact-matched (no wildcards in param types)
   *   <li>If one has params and the other doesn't, no match (prevents field/method
   *       cross-contamination)
   * </ul>
   *
   * <p><b>Examples:</b>
   *
   * <pre>
   * hasInFlightDispatches("com.example.Calculator", "add", new String[]{"int"})
   * hasInFlightDispatches("com.example.*", "*", new String[0])  // all no-arg methods
   * hasInFlightDispatches("*", "*", null)                       // all field ops
   * </pre>
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently.
   *
   * @param classPattern the class name pattern, potentially with wildcards
   * @param executableNamePattern the method/constructor/field name pattern, potentially with
   *     wildcards
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   * @return {@code true} if any matching dispatches have a counter > 0, {@code false} otherwise
   */
  public boolean hasInFlightDispatches(
      String classPattern, String executableNamePattern, String[] parameterTypes) {
    String pattern = buildKey(classPattern, executableNamePattern, parameterTypes);

    for (Map.Entry<String, DispatchCounter> entry : dispatchCounters.entrySet()) {
      String key = entry.getKey();
      if (matchesPattern(pattern, key)) {
        long count = entry.getValue().sum();
        if (count > 0) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Found in-flight dispatches for pattern '{}': {} has count={}",
                pattern,
                key,
                count);
          }
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Waits until all dispatches matching the specified pattern have completed (quiescence), or until
   * the timeout expires.
   *
   * <p>This method polls {@link #hasInFlightDispatches(String, String, String[])} at short
   * intervals until either:
   *
   * <ul>
   *   <li>No in-flight dispatches remain for the pattern (quiescence achieved), or
   *   <li>The specified timeout elapses
   * </ul>
   *
   * <p><b>Usage:</b> Call this method after starting fencing and before activating an intercept to
   * ensure all in-flight calls have completed.
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently.
   *
   * @param classPattern the class name pattern, potentially with wildcards
   * @param executableNamePattern the method/constructor/field name pattern, potentially with
   *     wildcards
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   * @param timeoutMs the maximum time to wait in milliseconds
   * @return {@code true} if quiescence was achieved before the timeout, {@code false} if the
   *     timeout expired while dispatches were still in-flight
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public boolean waitForQuiescence(
      String classPattern, String executableNamePattern, String[] parameterTypes, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;

    while (hasInFlightDispatches(classPattern, executableNamePattern, parameterTypes)) {
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Timeout waiting for quiescence of {}.{} after {}ms",
              classPattern,
              executableNamePattern,
              timeoutMs);
        }
        return false; // Timeout
      }

      // Short sleep to avoid busy-waiting
      // Use a small interval (1ms) for responsive quiescence detection
      Thread.sleep(Math.min(1, remaining));
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Achieved quiescence for {}.{}", classPattern, executableNamePattern);
    }
    return true;
  }

  /**
   * Starts fencing for all dispatches matching the specified pattern, blocking new {@link
   * #enterDispatch(String, String, String[])} calls.
   *
   * <p>After calling this method, any thread attempting to enter a dispatch matching the pattern
   * will block until {@link #stopFencing(String, String, String[])} is called.
   *
   * <p><b>Important:</b> Fencing does not affect dispatches that have already entered (already
   * called {@link #enterDispatch(String, String, String[])}). Those dispatches will continue
   * executing and will still be counted by {@link #hasInFlightDispatches(String, String,
   * String[])}.
   *
   * <p><b>Usage pattern:</b>
   *
   * <pre>{@code
   * tracker.startFencing(classPattern, methodPattern, parameterTypes);
   * try {
   *   boolean quiescent = tracker.waitForQuiescence(
   *       classPattern, methodPattern, parameterTypes, timeoutMs);
   *   if (quiescent) {
   *     // Activate intercept
   *   }
   * } finally {
   *   tracker.stopFencing(classPattern, methodPattern, parameterTypes);
   * }
   * }</pre>
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently.
   *
   * @param classPattern the class name pattern, potentially with wildcards
   * @param executableNamePattern the method/constructor/field name pattern, potentially with
   *     wildcards
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   */
  public void startFencing(
      String classPattern, String executableNamePattern, String[] parameterTypes) {
    String pattern = buildKey(classPattern, executableNamePattern, parameterTypes);
    // Store the fence pattern for checking during enterDispatch
    fencedPatterns.put(pattern, Boolean.TRUE);

    // Also fence existing counters
    for (Map.Entry<String, DispatchCounter> entry : dispatchCounters.entrySet()) {
      String key = entry.getKey();
      if (matchesPattern(pattern, key)) {
        entry.getValue().startFencing();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Started fencing for dispatch key '{}' (matches pattern '{}')", key, pattern);
        }
      }
    }
  }

  /**
   * Stops fencing for all dispatches matching the specified pattern, unblocking threads waiting in
   * {@link #enterDispatch(String, String, String[])}.
   *
   * <p>After calling this method, threads blocked in {@link #enterDispatch(String, String,
   * String[])} for matching dispatches will be unblocked and allowed to proceed.
   *
   * <p><b>Thread safety:</b> This method is thread-safe and may be called concurrently.
   *
   * @param classPattern the class name pattern, potentially with wildcards
   * @param executableNamePattern the method/constructor/field name pattern, potentially with
   *     wildcards
   * @param parameterTypes the parameter type names for methods/constructors ({@code new String[0]}
   *     for no-arg), or {@code null} for field operations
   */
  public void stopFencing(
      String classPattern, String executableNamePattern, String[] parameterTypes) {
    String pattern = buildKey(classPattern, executableNamePattern, parameterTypes);

    // Remove the pattern from fenced patterns
    fencedPatterns.remove(pattern);

    for (Map.Entry<String, DispatchCounter> entry : dispatchCounters.entrySet()) {
      String key = entry.getKey();
      if (matchesPattern(pattern, key)) {
        entry.getValue().stopFencing();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Stopped fencing for dispatch key '{}' (matches pattern '{}')", key, pattern);
        }
      }
    }
  }

  /**
   * Builds a dispatch key from class name, executable name, and parameter types.
   *
   * <p>The key format distinguishes methods/constructors from field operations:
   *
   * <ul>
   *   <li>Methods/constructors: {@code className.executableName(param1,param2)}
   *   <li>No-arg methods/constructors: {@code className.executableName()}
   *   <li>Field operations: {@code className.fieldName} (no parentheses)
   * </ul>
   *
   * <p>This implementation uses a thread-local {@link StringBuilder} to avoid the overhead of
   * {@link java.util.Arrays#stream(Object[])} and {@link
   * java.util.stream.Collectors#joining(CharSequence)} that was used in the original stream-based
   * approach.
   *
   * @param className the fully qualified class name
   * @param executableName the method, constructor ("new"), or field name
   * @param parameterTypes the parameter type names, or {@code null} for field operations
   * @return the dispatch key string
   */
  static String buildKey(String className, String executableName, String[] parameterTypes) {
    if (parameterTypes == null) {
      return className + "." + executableName;
    }
    StringBuilder sb = TL_KEY_BUILDER.get();
    sb.setLength(0);
    sb.append(className).append('.').append(executableName).append('(');
    for (int i = 0; i < parameterTypes.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(parameterTypes[i]);
    }
    sb.append(')');
    return sb.toString();
  }

  /**
   * Builds a dispatch key from pre-computed executable path and joined parameter types.
   *
   * <p>This overload eliminates redundant string construction when the caller has already computed
   * the {@code className.executableName} path and the comma-joined parameter types string. This is
   * useful in the {@code InterceptChecker} where these values are computed once and reused across
   * multiple intercept entry checks.
   *
   * <p>The key format is the same as {@link #buildKey(String, String, String[])}:
   *
   * <ul>
   *   <li>With params: {@code executablePath(joinedParamTypes)} e.g., {@code
   *       "com.example.Calc.add(int,String)"}
   *   <li>Without params (null joinedParamTypes): {@code executablePath} e.g., {@code
   *       "com.example.Foo.myField"}
   * </ul>
   *
   * @param executablePath the pre-computed {@code className.executableName} string
   * @param joinedParamTypes the comma-joined parameter types, or {@code null} for field operations
   * @return the dispatch key string
   */
  static String buildKey(String executablePath, String joinedParamTypes) {
    if (joinedParamTypes == null) {
      return executablePath;
    }
    return executablePath + "(" + joinedParamTypes + ")";
  }

  /**
   * Matches a fence pattern against a dispatch key, taking parameter types into account.
   *
   * <p>The matching process:
   *
   * <ol>
   *   <li>Split both strings on the first {@code '('} to separate the class.method portion from the
   *       parameter types portion
   *   <li>AntPath-match the class.method portions (supports wildcards like {@code com.example.*})
   *   <li>If both have parameter types, exact-match the parenthesized suffix
   *   <li>If one has params and the other doesn't, no match (prevents field/method
   *       cross-contamination)
   * </ol>
   *
   * @param pattern the fence pattern (may contain wildcards in class.method portion)
   * @param key the dispatch key to match against
   * @return {@code true} if the pattern matches the key
   */
  static boolean matchesPattern(String pattern, String key) {
    int patternParenIdx = pattern.indexOf('(');
    int keyParenIdx = key.indexOf('(');

    // Extract class.method and param portions
    String patternClassMethod;
    String patternParams;
    if (patternParenIdx >= 0) {
      patternClassMethod = pattern.substring(0, patternParenIdx);
      patternParams = pattern.substring(patternParenIdx);
    } else {
      patternClassMethod = pattern;
      patternParams = null;
    }

    String keyClassMethod;
    String keyParams;
    if (keyParenIdx >= 0) {
      keyClassMethod = key.substring(0, keyParenIdx);
      keyParams = key.substring(keyParenIdx);
    } else {
      keyClassMethod = key;
      keyParams = null;
    }

    // If one has params and the other doesn't, no match
    // This prevents field ops from matching method/ctor patterns and vice versa
    if ((patternParams == null) != (keyParams == null)) {
      return false;
    }

    // AntPath-match the class.method portions
    if (!matcher.isMatch(patternClassMethod, keyClassMethod)) {
      return false;
    }

    // If both have param types, exact-match the parenthesized suffix
    if (patternParams != null) {
      return patternParams.equals(keyParams);
    }

    // Both are field ops (no params) and class.method matched
    return true;
  }
}
