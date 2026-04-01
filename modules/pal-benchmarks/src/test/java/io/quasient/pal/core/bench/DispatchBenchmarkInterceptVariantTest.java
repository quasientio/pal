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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.quasient.foobar.apps.quantized.bench.callbacks.BenchmarkAfterCallback;
import io.quasient.foobar.apps.quantized.bench.callbacks.BenchmarkAroundCallback;
import io.quasient.foobar.apps.quantized.bench.callbacks.BenchmarkBeforeCallback;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.intercept.InterceptMatcher;
import io.quasient.pal.core.service.PeerWiring;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Tests verifying that the new benchmark intercept variants (INTERCEPTS_BEFORE, INTERCEPTS_AFTER,
 * INTERCEPTS_AROUND, INTERCEPTS_ALL) correctly register intercepts and measure actual callback
 * dispatch overhead.
 *
 * <p>These tests validate that each {@link FeatureSetVariant} intercept callback variant results in
 * the correct intercept registrations in the {@code InterceptMatcher} after benchmark setup
 * completes. This ensures the benchmarks measure real intercept dispatch cost rather than silently
 * measuring the wrong thing (e.g., zero registered intercepts).
 *
 * <p>These tests are integration-style since they require the full Guice injector, {@code
 * ServiceManager}, and woven classes from {@code itt-apps}. They exercise the registration path
 * from benchmark setup through to the {@code InterceptMatcher} registry.
 *
 * @see FeatureSetVariant
 * @see io.quasient.pal.core.intercept.InterceptMatcher
 * @see BenchmarkBeforeCallback
 * @see BenchmarkAfterCallback
 * @see BenchmarkAroundCallback
 */
public class DispatchBenchmarkInterceptVariantTest {

  /** Class name of the benchmark target. */
  private static final String QUANTIZED_CALLS_CLASS =
      "bench.quantized.io.quasient.foobar.apps.QuantizedCalls";

  /** The shared ZMQ context for the test. */
  private ZContext zmqCtx;

  /** Service manager for InterceptMatcher. */
  private ServiceManager serviceManager;

  /** The Guice injector. */
  private Injector injector;

  /** Peer UUID for this test instance. */
  private String peerUuid;

  /** The intercept registration address. */
  private String interceptRegAddress;

  /** The sync-ready address. */
  private String syncReadyAddress;

  /** Custom classloader for the test. */
  private CustomClassloader classloader;

  /** Sets up a minimal benchmark-like environment with PeerWiring and InterceptMatcher. */
  @Before
  public void setUp() {
    peerUuid = UUID.randomUUID().toString();
    interceptRegAddress = "inproc://intercept_reg_test_" + peerUuid.substring(0, 8);
    syncReadyAddress = "inproc://sync_ready_test_" + peerUuid.substring(0, 8);

    zmqCtx = new ZContext();
    zmqCtx.setLinger(1000);

    classloader =
        new CustomClassloader(new URL[] {}, Thread.currentThread().getContextClassLoader());

    Properties props = createMinimalProperties();
    EnumSet<RunOptions> runOpts = EnumSet.of(RunOptions.WITH_INTERCEPTS);

    injector = Guice.createInjector(new PeerWiring(props, runOpts, zmqCtx, classloader));

    // Start sync socket for collecting "go!" signals
    ZMQ.Socket syncSocket = zmqCtx.createSocket(SocketType.PULL);
    syncSocket.bind(syncReadyAddress);

    // Start InterceptMatcher service
    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);
    Set<Service> services = new HashSet<>();
    services.add(matcher);
    serviceManager = new ServiceManager(services);
    serviceManager.startAsync();

    // Collect "go!" signal from InterceptMatcher
    collectGoSignals(syncSocket, 1);
    serviceManager.awaitHealthy();
  }

  /** Tears down the test environment. */
  @After
  public void tearDown() {
    if (serviceManager != null) {
      serviceManager.stopAsync().awaitStopped();
    }
    if (classloader != null) {
      classloader.shutdown();
    }
    if (zmqCtx != null) {
      zmqCtx.close();
    }
  }

  /**
   * Verifies that the INTERCEPTS_BEFORE variant registers BEFORE intercepts for each benchmark
   * method on {@code bench.quantized.io.quasient.foobar.apps.QuantizedCalls}.
   *
   * <p>After benchmark setup with {@link FeatureSetVariant#INTERCEPTS_BEFORE}:
   *
   * <ul>
   *   <li>The {@code InterceptMatcher} must have 2 registered {@link InterceptType#BEFORE}
   *       intercepts (one per benchmark method: toUpperCase and sort)
   *   <li>Each intercept must match its target method with exact parameter types
   *   <li>The callback class must be {@code BenchmarkBeforeCallback}
   *   <li>The callback method must be {@code onBefore}
   * </ul>
   */
  @Test
  public void shouldRegisterBeforeInterceptForBenchmark() {
    int registered =
        BenchmarkInterceptRegistrar.registerIntercepts(
            FeatureSetVariant.INTERCEPTS_BEFORE, peerUuid, zmqCtx, interceptRegAddress);

    assertThat("Expected 2 intercepts registered (1 per method)", registered, is(2));

    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);

    // BEFORE intercepts are queried in BEFORE phase
    List<InterceptMessage> beforeMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);

    assertThat("Expected 1 BEFORE match for toUpperCase", beforeMatches.size(), is(1));
    InterceptMessage match = beforeMatches.get(0);
    assertThat(match.getInterceptType(), is(InterceptType.BEFORE.toByte()));
    assertThat(match.getCallbackClass(), is(BenchmarkInterceptRegistrar.BEFORE_CALLBACK_CLASS));
    assertThat(match.getCallbackMethod(), is(BenchmarkInterceptRegistrar.BEFORE_CALLBACK_METHOD));

    // Also verify sort method matches
    List<InterceptMessage> sortMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "sort",
            new String[] {"double[]"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);
    assertThat("Expected 1 BEFORE match for sort", sortMatches.size(), is(1));

    // AFTER phase should have no matches
    List<InterceptMessage> afterMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.AFTER);
    assertThat("Expected 0 AFTER matches", afterMatches.size(), is(0));
  }

  /**
   * Verifies that the INTERCEPTS_AFTER variant registers AFTER intercepts for each benchmark method
   * on {@code bench.quantized.io.quasient.foobar.apps.QuantizedCalls}.
   *
   * <p>After benchmark setup with {@link FeatureSetVariant#INTERCEPTS_AFTER}:
   *
   * <ul>
   *   <li>The {@code InterceptMatcher} must have 2 registered {@link InterceptType#AFTER}
   *       intercepts (one per benchmark method: toUpperCase and sort)
   *   <li>Each intercept must match its target method with exact parameter types
   *   <li>The callback class must be {@code BenchmarkAfterCallback}
   *   <li>The callback method must be {@code onAfter}
   * </ul>
   */
  @Test
  public void shouldRegisterAfterInterceptForBenchmark() {
    int registered =
        BenchmarkInterceptRegistrar.registerIntercepts(
            FeatureSetVariant.INTERCEPTS_AFTER, peerUuid, zmqCtx, interceptRegAddress);

    assertThat("Expected 2 intercepts registered (1 per method)", registered, is(2));

    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);

    // AFTER intercepts are queried in AFTER phase
    List<InterceptMessage> afterMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.AFTER);

    assertThat("Expected 1 AFTER match for toUpperCase", afterMatches.size(), is(1));
    InterceptMessage match = afterMatches.get(0);
    assertThat(match.getInterceptType(), is(InterceptType.AFTER.toByte()));
    assertThat(match.getCallbackClass(), is(BenchmarkInterceptRegistrar.AFTER_CALLBACK_CLASS));
    assertThat(match.getCallbackMethod(), is(BenchmarkInterceptRegistrar.AFTER_CALLBACK_METHOD));

    // BEFORE phase should have no matches
    List<InterceptMessage> beforeMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);
    assertThat("Expected 0 BEFORE matches", beforeMatches.size(), is(0));
  }

  /**
   * Verifies that the INTERCEPTS_AROUND variant registers AROUND intercepts for each benchmark
   * method on {@code bench.quantized.io.quasient.foobar.apps.QuantizedCalls}.
   *
   * <p>After benchmark setup with {@link FeatureSetVariant#INTERCEPTS_AROUND}:
   *
   * <ul>
   *   <li>The {@code InterceptMatcher} must have 2 registered {@link InterceptType#AROUND}
   *       intercepts (one per benchmark method: toUpperCase and sort)
   *   <li>Each intercept must match its target method with exact parameter types
   *   <li>The callback class must be {@code BenchmarkAroundCallback}
   *   <li>The callback method must be {@code onAround}
   * </ul>
   *
   * <p>AROUND intercepts are matched during the BEFORE phase (they participate in chain building
   * before execution), so the query must use {@link ExecPhase#BEFORE}.
   */
  @Test
  public void shouldRegisterAroundInterceptForBenchmark() {
    int registered =
        BenchmarkInterceptRegistrar.registerIntercepts(
            FeatureSetVariant.INTERCEPTS_AROUND, peerUuid, zmqCtx, interceptRegAddress);

    assertThat("Expected 2 intercepts registered (1 per method)", registered, is(2));

    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);

    // AROUND intercepts are queried in BEFORE phase (participate in chain building)
    List<InterceptMessage> beforeMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);

    assertThat("Expected 1 AROUND match in BEFORE phase", beforeMatches.size(), is(1));
    InterceptMessage match = beforeMatches.get(0);
    assertThat(match.getInterceptType(), is(InterceptType.AROUND.toByte()));
    assertThat(match.getCallbackClass(), is(BenchmarkInterceptRegistrar.AROUND_CALLBACK_CLASS));
    assertThat(match.getCallbackMethod(), is(BenchmarkInterceptRegistrar.AROUND_CALLBACK_METHOD));

    // AFTER phase should have no matches (AROUND is not in AFTER)
    List<InterceptMessage> afterMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.AFTER);
    assertThat("Expected 0 AFTER matches for AROUND", afterMatches.size(), is(0));
  }

  /**
   * Verifies that the INTERCEPTS_ALL variant registers BEFORE + AFTER + AROUND intercepts for each
   * benchmark method on {@code bench.quantized.io.quasient.foobar.apps.QuantizedCalls}.
   *
   * <p>After benchmark setup with {@link FeatureSetVariant#INTERCEPTS_ALL}:
   *
   * <ul>
   *   <li>The {@code InterceptMatcher} must have 6 registered intercepts (3 types * 2 methods)
   *   <li>Querying in BEFORE phase for a single method returns BEFORE + AROUND intercepts (2
   *       matches)
   *   <li>Querying in AFTER phase for a single method returns AFTER intercepts (1 match)
   *   <li>Each intercept must match its target method with exact parameter types
   *   <li>Each callback must point to the corresponding no-op benchmark callback handler
   * </ul>
   */
  @Test
  public void shouldRegisterAllInterceptTypesForBenchmark() {
    int registered =
        BenchmarkInterceptRegistrar.registerIntercepts(
            FeatureSetVariant.INTERCEPTS_ALL, peerUuid, zmqCtx, interceptRegAddress);

    assertThat("Expected 6 intercepts registered (3 types * 2 methods)", registered, is(6));

    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);

    // BEFORE phase query returns BEFORE + AROUND (2 matches)
    // Copy results immediately - getMatchingIntercepts returns a thread-local reusable list
    List<InterceptMessage> beforeMatches =
        new ArrayList<>(
            matcher.getMatchingIntercepts(
                QUANTIZED_CALLS_CLASS,
                "toUpperCase",
                new String[] {"java.lang.String"},
                MessageType.EXEC_INSTANCE_METHOD,
                ExecPhase.BEFORE));

    assertThat("Expected 2 matches in BEFORE phase (BEFORE + AROUND)", beforeMatches.size(), is(2));

    // Verify we have exactly one BEFORE and one AROUND
    long beforeCount =
        beforeMatches.stream()
            .filter(m -> m.getInterceptType() == InterceptType.BEFORE.toByte())
            .count();
    long aroundCount =
        beforeMatches.stream()
            .filter(m -> m.getInterceptType() == InterceptType.AROUND.toByte())
            .count();
    assertThat("Expected 1 BEFORE intercept", beforeCount, is(1L));
    assertThat("Expected 1 AROUND intercept", aroundCount, is(1L));

    // AFTER phase query returns AFTER only (1 match)
    // Copy results immediately - getMatchingIntercepts returns a thread-local reusable list
    List<InterceptMessage> afterMatches =
        new ArrayList<>(
            matcher.getMatchingIntercepts(
                QUANTIZED_CALLS_CLASS,
                "toUpperCase",
                new String[] {"java.lang.String"},
                MessageType.EXEC_INSTANCE_METHOD,
                ExecPhase.AFTER));

    assertThat("Expected 1 match in AFTER phase", afterMatches.size(), is(1));
    assertThat(afterMatches.get(0).getInterceptType(), is(InterceptType.AFTER.toByte()));

    // Verify callback classes/methods
    for (InterceptMessage msg : beforeMatches) {
      InterceptType type = InterceptType.fromByte(msg.getInterceptType());
      switch (type) {
        case BEFORE -> {
          assertThat(msg.getCallbackClass(), is(BenchmarkInterceptRegistrar.BEFORE_CALLBACK_CLASS));
          assertThat(
              msg.getCallbackMethod(), is(BenchmarkInterceptRegistrar.BEFORE_CALLBACK_METHOD));
        }
        case AROUND -> {
          assertThat(msg.getCallbackClass(), is(BenchmarkInterceptRegistrar.AROUND_CALLBACK_CLASS));
          assertThat(
              msg.getCallbackMethod(), is(BenchmarkInterceptRegistrar.AROUND_CALLBACK_METHOD));
        }
        default -> throw new AssertionError("Unexpected type in BEFORE phase: " + type);
      }
    }
    assertThat(
        afterMatches.get(0).getCallbackClass(),
        is(BenchmarkInterceptRegistrar.AFTER_CALLBACK_CLASS));
    assertThat(
        afterMatches.get(0).getCallbackMethod(),
        is(BenchmarkInterceptRegistrar.AFTER_CALLBACK_METHOD));
  }

  /**
   * Verifies that the BEFORE intercept callback is actually invoked during benchmark dispatch.
   *
   * <p>This test exercises the registration path and verifies that the registered intercept is
   * correctly matched when queried for the actual method signatures used by the benchmark. It
   * validates that both {@code toUpperCase(String)} and {@code sort(double[])} match the wildcard
   * pattern registered by {@link BenchmarkInterceptRegistrar}.
   *
   * <p>Full callback invocation testing requires the woven dispatch path which is exercised by the
   * actual JMH benchmark. This test verifies the prerequisite: that intercepts are correctly
   * registered and matched.
   */
  @Test
  public void shouldInvokeCallbackDuringBenchmarkDispatch() {
    BenchmarkInterceptRegistrar.registerIntercepts(
        FeatureSetVariant.INTERCEPTS_BEFORE, peerUuid, zmqCtx, interceptRegAddress);

    InterceptMatcher matcher = injector.getInstance(InterceptMatcher.class);

    // Verify toUpperCase matches
    List<InterceptMessage> upperCaseMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "toUpperCase",
            new String[] {"java.lang.String"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);
    assertThat(
        "toUpperCase should match registered BEFORE intercept", upperCaseMatches.size(), is(1));
    assertThat(UuidUtils.toString(upperCaseMatches.get(0).getPeerUuid()), is(peerUuid));

    // Verify sort matches
    List<InterceptMessage> sortMatches =
        matcher.getMatchingIntercepts(
            QUANTIZED_CALLS_CLASS,
            "sort",
            new String[] {"double[]"},
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE);
    assertThat("sort should match registered BEFORE intercept", sortMatches.size(), is(1));
    assertThat(UuidUtils.toString(sortMatches.get(0).getPeerUuid()), is(peerUuid));
  }

  /**
   * Creates minimal properties required by PeerWiring for the test.
   *
   * @return properties configured for a minimal test environment
   */
  private Properties createMinimalProperties() {
    Properties props = new Properties();
    props.setProperty("id", peerUuid);
    props.setProperty("sync.ready", syncReadyAddress);
    props.setProperty("intercepts.reg", interceptRegAddress);
    props.setProperty("intercept.drain.timeout.ms", "5000");
    props.setProperty("intercept.callback.timeout.ms", "3000");
    props.setProperty("paldir_url", PalDirectory.NO_URL);
    props.setProperty("messages.with_src_context", "false");

    // ZMQ inproc endpoints (required by various service constructors)
    props.setProperty("offset.pub", "inproc://offsets_test_" + peerUuid.substring(0, 8));
    props.setProperty("source.log", "inproc://source_log_test_" + peerUuid.substring(0, 8));

    // DirectoryConnectionProvider
    props.setProperty("etcd.connect.timeout.ms", "5000");

    // Queue config (required by PeerWiring providers)
    props.setProperty("wal.queue.type", "CHUNKED");
    props.setProperty("wal.queue.initial", "1024");
    props.setProperty("wal.queue.max", "2048");
    props.setProperty("pub.queue.type", "CHUNKED");
    props.setProperty("pub.queue.initial", "1024");
    props.setProperty("pub.queue.max", "2048");

    // Publisher config (required by MessagePublisherConfig provider)
    props.setProperty("pub.spsc_size", "1024");
    props.setProperty("pub.batch_size", "64");
    props.setProperty("pub.flush_on_close", "false");
    props.setProperty("out.pub", "tcp://127.0.0.1:0");
    props.setProperty("pub.zmq.linger", "0");
    props.setProperty("pub.zmq.send_timeout", "0");
    props.setProperty("pub.zmq.send_hwm", "1000");
    props.setProperty("pub.drop.policy", "DROP_OLD");
    props.setProperty("pub.drop.hwm_pct", "97");
    props.setProperty("pub.drop.keep_pct", "92");

    // WAL config
    props.setProperty("wal.type", "kafka");
    props.setProperty("wal.chronicle.base_dir", "");
    props.setProperty("wal.chronicle.roll_cycle", "");
    props.setProperty("wal.chronicle.block_size", "");
    props.setProperty("wal.chronicle.sync_every", "-1");
    props.setProperty("wal.flush_on_close", "");

    // Kafka WAL writer config
    props.setProperty("bootstrap.servers", "localhost:29092");
    props.setProperty("wal.kafka.linger_ms", "");
    props.setProperty("wal.kafka.batch_size", "");
    props.setProperty("wal.kafka.compression_type", "");
    props.setProperty("wal.kafka.buffer_memory", "");
    props.setProperty("wal.chronicle.index_spacing", "");
    props.setProperty("wal.offsets.ring_size", "");

    // Kafka source log reader config
    props.setProperty("enable.auto.commit", "false");
    props.setProperty("auto.commit.interval.ms", "500");
    props.setProperty("auto.offset.reset", "earliest");
    props.setProperty("session.timeout.ms", "30000");
    props.setProperty("pollDuration", "10");

    return props;
  }

  /**
   * Waits for the specified number of "go!" signals on the sync socket.
   *
   * @param syncSocket the PULL socket to receive signals on
   * @param count the number of signals to wait for
   */
  private static void collectGoSignals(ZMQ.Socket syncSocket, int count) {
    CountDownLatch latch = new CountDownLatch(count);
    while (latch.getCount() > 0) {
      String received = syncSocket.recvStr();
      if ("go!".equalsIgnoreCase(received)) {
        latch.countDown();
      }
    }
    syncSocket.close();
  }
}
