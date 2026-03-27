/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.replay.WalReader;
import io.quasient.pal.common.runtime.DispatchForwarder;
import io.quasient.pal.common.runtime.ProxyDispatcher;
import io.quasient.pal.common.runtime.ThreadAffinity;
import io.quasient.pal.core.annotations.AnnotationProcessor;
import io.quasient.pal.core.annotations.AnnotationsProcessor;
import io.quasient.pal.core.dispatcher.InterceptAsyncThreadFactory;
import io.quasient.pal.core.execution.JavaFxInvocationExecutor;
import io.quasient.pal.core.execution.ThreadAffinityDispatcher;
import io.quasient.pal.core.execution.java.AspectProxyDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.intercept.ExceptionPolicyConfig;
import io.quasient.pal.core.intercept.ExceptionPolicyResolver;
import io.quasient.pal.core.intercept.InFlightDispatchTracker;
import io.quasient.pal.core.intercept.InterceptActivationCoordinator;
import io.quasient.pal.core.intercept.PendingInterceptActivation;
import io.quasient.pal.core.intercept.VirtualThreadCallbackExecutor;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.concurrent.MpscKind;
import io.quasient.pal.core.recording.RecordingScope;
import io.quasient.pal.core.recording.RecordingScopeParser;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.replay.ReplayPolicyParser;
import io.quasient.pal.core.replay.SideEffectAnalyzer;
import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import io.quasient.pal.core.rpc.policy.RpcPolicyChecker;
import io.quasient.pal.core.rpc.policy.RpcPolicyFileWatcher;
import io.quasient.pal.core.rpc.policy.RpcPolicyHolder;
import io.quasient.pal.core.rpc.policy.RpcPolicyParser;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.core.transport.WalType;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.chronicle.ChronicleQueueFactory;
import io.quasient.pal.core.transport.chronicle.ChronicleSourceLogReader;
import io.quasient.pal.core.transport.chronicle.ChronicleWalWriter;
import io.quasient.pal.core.transport.chronicle.DefaultChronicleQueueFactory;
import io.quasient.pal.core.transport.kafka.KafkaSourceLogReader;
import io.quasient.pal.core.transport.kafka.KafkaWalWriter;
import io.quasient.pal.core.transport.kafka.ProducerFactory;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import io.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * Guice module for wiring peer components within the Pal runtime.
 *
 * <p>This module sets up dependency injection bindings for key runtime components, such as the
 * ZeroMQ context, custom class loader, and service thread management. It also initializes internal
 * queues and configuration properties.
 */
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Guice wiring module - shared references for dependency injection")
public class PeerWiring extends AbstractModule {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(PeerWiring.class);

  /**
   * Configuration properties used for module initialization and binding. Expected to include
   * necessary keys such as "id" for peer identification.
   */
  private final Properties properties;

  /** ZeroMQ context used for managing communication channels within the peer's core services. */
  private final ZContext zmqContext;

  /** Unique identifier for this peer, derived from the "id" property in the configuration. */
  private final UUID peerUuid;

  /** Custom class loader used for dynamic loading and isolation of classes at runtime. */
  private final CustomClassloader customClassloader;

  /** Runtime options controlling behavior and configuration of the peer. */
  private final Set<RunOptions> runOptions;

  /**
   * Thread group for managing service-related threads and facilitating centralized thread handling.
   */
  private final ThreadGroup serviceThreadGroup = new ThreadGroup("service-threads");

  /**
   * Constructs a new PeerWiring module.
   *
   * <p>This constructor initializes the module with configuration properties, runtime options,
   * ZeroMQ context, and a custom class loader. It also extracts the peer's unique identifier from
   * the provided properties and augments these properties with predefined service names.
   *
   * @param properties configuration properties; must include an "id" key representing this peer's
   *     UUID.
   * @param runOptions a set of runtime options that modify the peer's behavior.
   * @param zmqContext ZeroMQ context for managing distributed message passing.
   * @param customClassloader class loader used for dynamic loading and isolation of runtime
   *     classes.
   */
  public PeerWiring(
      Properties properties,
      Set<RunOptions> runOptions,
      ZContext zmqContext,
      CustomClassloader customClassloader) {
    if (logger.isDebugEnabled()) {
      logger.debug("Created guice module with properties:{}", printedProperties(properties));
    }
    this.properties = properties;
    addServiceNamesToProps();
    this.runOptions = runOptions;
    this.zmqContext = zmqContext;
    this.peerUuid = UUID.fromString(properties.getProperty("id"));
    this.customClassloader = customClassloader;
  }

  /**
   * Generates a sorted string representation of the configuration properties.
   *
   * <p>This utility method sorts the property keys and formats them as key-value pairs for logging
   * purposes.
   *
   * @param props the configuration properties to be printed.
   * @return a formatted string representation of the properties.
   */
  private static String printedProperties(Properties props) {
    final StringBuilder sb = new StringBuilder();
    final List<String> keys = new ArrayList<>(props.stringPropertyNames());
    Collections.sort(keys);
    for (String key : keys) {
      sb.append("\n").append(key).append(":").append(props.getProperty(key));
    }
    return sb.toString();
  }

  /**
   * Augments the configuration properties with predefined service names.
   *
   * <p>This method sets specific property entries for service identifiers, thereby ensuring that
   * service-related traces are clearly distinguishable.
   */
  private void addServiceNamesToProps() {
    // use underscore in names to better filter service-related traces
    properties.setProperty("LogReader.service", "Log_Reader");
    properties.setProperty("WalWriter.service", "WAL_Writer");
    properties.setProperty("ZmqRpcServer.service", "ZMQ_RPC_Request_Dispatcher");
    properties.setProperty("JsonRpcRequestServer.service", "JSON_RPC_Request_Dispatcher");
    properties.setProperty("MessagePublisher.service", "Message_Publisher");
    properties.setProperty("Intercepts.service", "Intercepts_Processor");
    properties.setProperty("Session.service", "Session_Service");
  }

  /**
   * Configures the Guice dependency injection module.
   *
   * <p>This method binds configuration properties to named values, assigns implementations to
   * interfaces, and requests static injections for essential components. It ensures that the peer's
   * runtime dependencies are properly established.
   */
  @Override
  protected void configure() {

    // Default sourceAndWalAreSameLog to false when not set by Main.addMiscProperties()
    // (e.g., when PeerWiring is used directly in benchmarks or tests).
    properties.putIfAbsent("log.sourceAndWalAreSameLog", "false");

    Names.bindProperties(binder(), properties);

    // bind implementations
    bind(ProxyDispatcher.class).to(AspectProxyDispatcher.class);

    // Chronicle path may be chosen in the @Provides method; make sure its interface is bound.
    bind(ChronicleQueueFactory.class).to(DefaultChronicleQueueFactory.class);

    // Ensure AnnotationsProcessor is a singleton (in addition to any annotation used).
    bind(AnnotationsProcessor.class).asEagerSingleton();

    // Contribute implementations to the AnnotationProcessor set. Example:
    @SuppressWarnings("unused")
    Multibinder<AnnotationProcessor> unused =
        Multibinder.newSetBinder(binder(), AnnotationProcessor.class);
    // Register an existing processor:
    // setBinder.addBinding().to(InterceptAnnotationProcessor.class);

    // common and cxn library classes are not annotated with @Singleton
    bind(ObjectLookupStore.class)
        .toProvider(ConcurrentHashMapObjectLookupStore::createSyncManaged)
        .asEagerSingleton();
    bind(MessageBuilder.class).toProvider(() -> new MessageBuilder(peerUuid)).asEagerSingleton();
    bind(DirectoryConnectionProvider.class).asEagerSingleton();

    // Intercept coordination components
    bind(InFlightDispatchTracker.class).asEagerSingleton();
    bind(InterceptActivationCoordinator.class).asEagerSingleton();

    // RPC policy components
    bind(RpcPolicyHolder.class).in(Singleton.class);
    bind(RpcPolicyChecker.class).in(Singleton.class);

    // AspectProxy and DispatchForwarder's fields are static
    requestStaticInjection(AspectProxyDispatcher.class);
    requestStaticInjection(DispatchForwarder.class);
  }

  /**
   * Provides the drain timeout for intercept activation coordinator.
   *
   * <p>This timeout controls how long to wait for in-flight dispatches to complete before
   * activating an intercept. Configured via the "intercept.drain.timeout.ms" property (default:
   * 5000ms).
   *
   * @return the drain timeout in milliseconds
   */
  @SuppressWarnings("unused")
  @Provides
  @Named("intercept.drain.timeout.ms")
  public long provideInterceptDrainTimeout() {
    return Long.parseLong(properties.getProperty("intercept.drain.timeout.ms"));
  }

  /**
   * Provides the default callback timeout for intercept callback dispatch.
   *
   * <p>This timeout controls how long the intercepted peer waits for a callback peer to respond to
   * synchronous BEFORE/AFTER callbacks. Can be overridden per-intercept. A value of 0 means no
   * timeout (infinite wait). Configured via the "intercept.callback.timeout.ms" property (default:
   * 3000ms).
   *
   * @return the callback timeout in milliseconds
   */
  @SuppressWarnings("unused")
  @Provides
  @Named("intercept.callback.timeout.ms")
  public long provideInterceptCallbackTimeout() {
    return Long.parseLong(properties.getProperty("intercept.callback.timeout.ms"));
  }

  /**
   * Provides the MPSC queue for pending intercept activations.
   *
   * <p>This queue is shared between the {@link InterceptActivationCoordinator} and the {@link
   * io.quasient.pal.core.intercept.InterceptMatcher}. After drain operations complete quiescence,
   * they enqueue pending activations here. The InterceptMatcher polls this queue and registers the
   * intercepts, maintaining single-writer semantics for the intercept registry.
   *
   * <p>The queue is unbounded (using {@link MpscUnboundedArrayQueue}) to ensure drain threads never
   * block when enqueuing. The chunk size is 64 for balanced memory usage and throughput.
   *
   * @return the MPSC queue for pending intercept activations
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Named("intercept.pending.activations.queue")
  public MessagePassingQueue<PendingInterceptActivation> providePendingActivationsQueue() {
    // Initial chunk size of 64 for balanced memory usage and throughput
    return new MpscUnboundedArrayQueue<>(64);
  }

  /**
   * Provides the ZeroMQ context for message passing.
   *
   * @return the ZeroMQ context used for managing communication channels.
   */
  @Provides
  @SuppressWarnings({"unused", "CloseableProvides"})
  public ZContext provideZmqContext() {
    return zmqContext;
  }

  /**
   * Provides a {@link ProducerFactory} which allows for lazy initialization of the Kafka Producer,
   * once the Wal's bootstrap servers are known (see {@link KafkaWalWriter#writeToLog}).
   *
   * @return a factory that returns a real {@link KafkaProducer}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  ProducerFactory provideProducerFactory() {
    return KafkaProducer::new;
  }

  /**
   * Provides a {@link Path} to the base directory for Chronicle queue files.
   *
   * <p>The base directory is determined from the "wal.chronicle.base_dir" property, which can be
   * set via the --chronicle-base-dir CLI option or CHRONICLE_BASE_DIR environment variable. If not
   * specified, defaults to the current working directory. This is only used for relative Chronicle
   * paths (file:mylog); absolute paths (file:/path/mylog) ignore this setting.
   *
   * @return base dir path for Chronicle queues
   */
  @SuppressWarnings("unused")
  @Provides
  @Named("chronicleBaseDir")
  Path provideChronicleBaseDir() {
    String pathStr = properties.getProperty("wal.chronicle.base_dir");
    if (pathStr == null || pathStr.isBlank()) {
      // Default to current working directory for relative paths
      return Paths.get(".");
    }
    return Paths.get(pathStr);
  }

  /**
   * Provides the nullable, configured WAL Writer.
   *
   * @param kafka guice-created provider for {@link KafkaWalWriter}
   * @param chronicle guice-created provider for {@link ChronicleWalWriter}
   * @return the configured WAL writer; null if run options don't contain {@code WITH_WAL}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Nullable
  WalWriter provideWalWriter(
      Provider<KafkaWalWriter> kafka, Provider<ChronicleWalWriter> chronicle) {

    if (!runOptions.contains(RunOptions.WITH_WAL)) {
      return null;
    }

    WalType walType =
        WalType.valueOf(properties.getProperty("wal.type").toUpperCase(Locale.ENGLISH));

    return switch (walType) {
      case KAFKA -> kafka.get();
      case CHRONICLE -> chronicle.get();
    };
  }

  /**
   * Provides the nullable, configured Source Log Reader.
   *
   * @param kafka guice-created provider for {@link KafkaSourceLogReader}
   * @param chronicle guice-created provider for {@link ChronicleSourceLogReader}
   * @return the configured source log reader; null if run options don't contain {@code
   *     WITH_SOURCE_LOG}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Nullable
  SourceLogReader provideSourceLogReader(
      Provider<KafkaSourceLogReader> kafka, Provider<ChronicleSourceLogReader> chronicle) {

    if (!runOptions.contains(RunOptions.WITH_SOURCE_LOG)) {
      return null;
    }

    String logType = properties.getProperty("source_log.type", "KAFKA");

    return switch (logType.toUpperCase(Locale.ENGLISH)) {
      case "KAFKA" -> kafka.get();
      case "CHRONICLE" -> chronicle.get();
      default -> throw new IllegalStateException("Unknown source log type: " + logType);
    };
  }

  /**
   * Provides the WAL Queue singleton, initialized with the values given by the corresponding {@code
   * "wal.queue.*"} parameters.
   *
   * @return the initialized WAL queue instance, or null if type is {@link MpscKind#NONE}.
   */
  @Provides
  @Nullable
  @Singleton
  @Named("wal_queue")
  @SuppressWarnings("unused")
  public HwmMessageQueue<OutboundMsg> provideWalQueue() {
    MpscKind kind =
        MpscKind.valueOf(
            properties.getProperty("wal.queue.type", "CHUNKED").toUpperCase(Locale.ENGLISH));

    if (MpscKind.NONE.equals(kind)) {
      return null;
    }

    int initial = Integer.parseInt(properties.getProperty("wal.queue.initial", "1024"));
    int max = Integer.parseInt(properties.getProperty("wal.queue.max", "2048"));
    return HwmMessageQueue.createQueue(kind, initial, max);
  }

  /**
   * Provides the PUB Queue singleton, initialized with the values given by the corresponding {@code
   * "pub.queue.*"} parameters.
   *
   * @return the initialized PUB queue instance
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Named("pub_queue")
  public HwmMessageQueue<OutboundMsg> providePubQueue() {
    MpscKind kind =
        MpscKind.valueOf(
            properties.getProperty("pub.queue.type", "CHUNKED").toUpperCase(Locale.ENGLISH));

    if (MpscKind.NONE.equals(kind)) {
      throw new IllegalArgumentException("PUB queue type cannot be 'NONE'");
    }

    int initial = Integer.parseInt(properties.getProperty("pub.queue.initial", "1024"));
    int max = Integer.parseInt(properties.getProperty("pub.queue.max", "2048"));
    return HwmMessageQueue.createQueue(kind, initial, max);
  }

  /** Shared failure flag – singleton instance */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Named("walFailed")
  public AtomicBoolean provideWalFailedFlag() {
    return new AtomicBoolean(false);
  }

  /**
   * Returns ZMQ endpoint of sessions service
   *
   * @return zmq endpoint (address) if sessions option is enabled, otherwise null.
   */
  @SuppressWarnings("unused")
  @Provides
  @Named("sessionServiceEndpoint")
  @Nullable
  public String provideSessionServiceEndpoint() {
    if (runOptions.contains(RunOptions.WITH_SESSIONS)) {
      return properties.getProperty("session.svc");
    }
    return null;
  }

  /**
   * Provides the unique peer identifier.
   *
   * @return the UUID associated with this peer, as derived from the configuration properties.
   */
  @SuppressWarnings("unused")
  @Provides
  public UUID providePeerUuid() {
    return peerUuid;
  }

  /**
   * Provides the thread group for service-related threads.
   *
   * @return the ThreadGroup designated for organizing service threads.
   */
  @SuppressWarnings("unused")
  @Provides
  public ThreadGroup provideServiceThreadGroup() {
    return serviceThreadGroup;
  }

  /**
   * Provides the custom class loader for dynamic class loading.
   *
   * @return the CustomClassloader used for loading classes at runtime.
   */
  @SuppressWarnings({"unused", "CloseableProvides"})
  @Provides
  public CustomClassloader provideCustomClassloader() {
    return customClassloader;
  }

  /**
   * Provides the set of runtime options for the peer.
   *
   * @return a Set of RunOptions that dictate the peer's runtime behavior.
   */
  @SuppressWarnings("unused")
  @Provides
  public Set<RunOptions> provideRunOptions() {
    return runOptions;
  }

  /**
   * Provides the {@link PublishingDropPolicy} for dropping messages to publish, on queue
   * congestion.
   *
   * @return the configured message Drop policy
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  public PublishingDropPolicy providePublishingDropPolicy() {
    return PublishingDropPolicy.valueOf(
        properties.getProperty("pub.drop.policy").toUpperCase(Locale.ENGLISH));
  }

  /**
   * Provides the configuration for {@link MessagePublisher}, parsed from the properties.
   *
   * @return record with the given configuration
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  public MessagePublisherConfig provideMessagePublisherConfig(
      PublishingDropPolicy publishingDropPolicy) {

    return new MessagePublisherConfig(
        Integer.parseInt(properties.getProperty("pub.spsc_size")),
        Integer.parseInt(properties.getProperty("pub.batch_size")),
        Boolean.parseBoolean(properties.getProperty("pub.flush_on_close")),
        properties.getProperty("out.pub"),
        Integer.parseInt(properties.getProperty("pub.zmq.linger")),
        Integer.parseInt(properties.getProperty("pub.zmq.send_timeout")),
        Integer.parseInt(properties.getProperty("pub.zmq.send_hwm")),
        publishingDropPolicy,
        Integer.parseInt(properties.getProperty("pub.drop.hwm_pct")),
        Integer.parseInt(properties.getProperty("pub.drop.keep_pct")));
  }

  /**
   * Provides the executor service for asynchronous local intercept callbacks.
   *
   * <p>This executor is used to run BEFORE_ASYNC and AFTER_ASYNC intercept callbacks in the
   * background without blocking the main execution thread.
   *
   * <p><b>Executor selection (via {@link VirtualThreadCallbackExecutor}):</b>
   *
   * <ul>
   *   <li><b>Java 21+:</b> Virtual thread executor ({@code
   *       Executors.newVirtualThreadPerTaskExecutor()}) — eliminates thread pool sizing, ~1KB per
   *       thread
   *   <li><b>Java 17-20:</b> Cached thread pool ({@code Executors.newCachedThreadPool()}) — auto
   *       scales to demand
   *   <li><b>Override:</b> Set {@code -Dpal.intercept.async.executor=VIRTUAL|CACHED|FIXED}
   * </ul>
   *
   * <p>For CACHED and FIXED executors, threads are created via {@link InterceptAsyncThreadFactory}
   * which ensures threads inherit the {@link CustomClassloader} for proper class resolution during
   * callback execution.
   *
   * @return an ExecutorService for async intercept callbacks
   * @see VirtualThreadCallbackExecutor
   */
  @SuppressWarnings({"unused", "CloseableProvides"})
  @Provides
  @Singleton
  @Named("intercept.async.executor")
  public ExecutorService provideInterceptAsyncExecutor() {
    return VirtualThreadCallbackExecutor.create(
        new InterceptAsyncThreadFactory(serviceThreadGroup, customClassloader));
  }

  /**
   * Provides the exception policy configuration for intercept callbacks.
   *
   * <p>This configuration determines how exceptions thrown by intercept callbacks are handled. The
   * configuration is built from system properties and CLI flags in the following precedence order:
   *
   * <ol>
   *   <li>CLI flags (--exception-policy, --checked-exception-policy)
   *   <li>System properties (pal.intercept.exception-policy.default, etc.)
   *   <li>Built-in defaults (PROPAGATE_CONTROLLED_ONLY, WRAP)
   * </ol>
   *
   * <p><b>Default policies (safety-first approach):</b>
   *
   * <ul>
   *   <li>Global exception propagation: PROPAGATE_CONTROLLED_ONLY
   *   <li>Global checked exception: WRAP
   *   <li>ASYNC intercepts (BEFORE_ASYNC, AFTER_ASYNC): SWALLOW_ALL (hardcoded, unchangeable)
   * </ul>
   *
   * <p><b>System properties for configuration:</b>
   *
   * <ul>
   *   <li>{@code pal.intercept.exception-policy.default=POLICY} - Global propagation policy
   *   <li>{@code pal.intercept.exception-policy.before=POLICY} - Per-type propagation for BEFORE
   *   <li>{@code pal.intercept.exception-policy.after=POLICY} - Per-type propagation for AFTER
   *   <li>{@code pal.intercept.exception-policy.around=POLICY} - Per-type propagation for AROUND
   *   <li>{@code pal.intercept.checked-exception-policy.default=POLICY} - Global checked policy
   *   <li>{@code pal.intercept.checked-exception-policy.before=POLICY} - Per-type checked for
   *       BEFORE
   *   <li>{@code pal.intercept.checked-exception-policy.after=POLICY} - Per-type checked for AFTER
   *   <li>{@code pal.intercept.checked-exception-policy.around=POLICY} - Per-type checked for
   *       AROUND
   * </ul>
   *
   * @return the configured exception policy
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  public ExceptionPolicyConfig provideExceptionPolicyConfig() {
    ExceptionPolicyConfig.Builder builder = new ExceptionPolicyConfig.Builder();

    // Parse global exception propagation policy from properties or use default
    String globalExceptionPolicyStr =
        properties.getProperty("pal.intercept.exception-policy.default");
    if (globalExceptionPolicyStr != null && !globalExceptionPolicyStr.isBlank()) {
      try {
        ExceptionPropagationPolicy policy =
            ExceptionPropagationPolicy.valueOf(
                globalExceptionPolicyStr.trim().toUpperCase(Locale.ENGLISH));
        builder.globalPropagationPolicy(policy);
      } catch (IllegalArgumentException e) {
        logger.warn(
            "Invalid exception propagation policy '{}', using default", globalExceptionPolicyStr);
      }
    }
    // If not specified, builder uses default: PROPAGATE_CONTROLLED_ONLY

    // Parse global checked exception policy from properties or use default
    String globalCheckedPolicyStr =
        properties.getProperty("pal.intercept.checked-exception-policy.default");
    if (globalCheckedPolicyStr != null && !globalCheckedPolicyStr.isBlank()) {
      try {
        CheckedExceptionPolicy policy =
            CheckedExceptionPolicy.valueOf(
                globalCheckedPolicyStr.trim().toUpperCase(Locale.ENGLISH));
        builder.globalCheckedExceptionPolicy(policy);
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid checked exception policy '{}', using default", globalCheckedPolicyStr);
      }
    }
    // If not specified, builder uses default: WRAP

    // Parse per-type exception propagation policies
    for (InterceptType type :
        new InterceptType[] {InterceptType.BEFORE, InterceptType.AFTER, InterceptType.AROUND}) {
      String key =
          "pal.intercept.exception-policy."
              + type.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
      String policyStr = properties.getProperty(key);
      if (policyStr != null && !policyStr.isBlank()) {
        try {
          ExceptionPropagationPolicy policy =
              ExceptionPropagationPolicy.valueOf(policyStr.trim().toUpperCase(Locale.ENGLISH));
          builder.perTypePropagationPolicy(type, policy);
        } catch (IllegalArgumentException e) {
          logger.warn("Invalid exception propagation policy '{}' for type {}", policyStr, type);
        }
      }
    }

    // Parse per-type checked exception policies
    for (InterceptType type :
        new InterceptType[] {InterceptType.BEFORE, InterceptType.AFTER, InterceptType.AROUND}) {
      String key =
          "pal.intercept.checked-exception-policy."
              + type.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
      String policyStr = properties.getProperty(key);
      if (policyStr != null && !policyStr.isBlank()) {
        try {
          CheckedExceptionPolicy policy =
              CheckedExceptionPolicy.valueOf(policyStr.trim().toUpperCase(Locale.ENGLISH));
          builder.perTypeCheckedExceptionPolicy(type, policy);
        } catch (IllegalArgumentException e) {
          logger.warn("Invalid checked exception policy '{}' for type {}", policyStr, type);
        }
      }
    }

    // Hardcode ASYNC intercepts to always use SWALLOW_ALL
    // This is unchangeable to ensure async callbacks never block or propagate exceptions
    builder.perTypePropagationPolicy(
        InterceptType.BEFORE_ASYNC, ExceptionPropagationPolicy.SWALLOW_ALL);
    builder.perTypePropagationPolicy(
        InterceptType.AFTER_ASYNC, ExceptionPropagationPolicy.SWALLOW_ALL);

    ExceptionPolicyConfig config = builder.build();
    logger.info(
        "Exception policy configuration: global propagation={}, global checked={}",
        config.getGlobalPropagationPolicy(),
        config.getGlobalCheckedExceptionPolicy());
    logger.debug("ASYNC intercepts hardcoded to SWALLOW_ALL");

    return config;
  }

  /**
   * Provides the {@link ExceptionPolicyResolver} for resolving exception policies.
   *
   * @param config the exception policy configuration
   * @return the exception policy resolver
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  public ExceptionPolicyResolver provideExceptionPolicyResolver(ExceptionPolicyConfig config) {
    return new ExceptionPolicyResolver(config);
  }

  /**
   * Provides the {@link ThreadAffinityDispatcher} for routing invocations based on thread affinity.
   *
   * <p>When the {@code execution.fx.thread.enabled} property is {@code true}, registers a {@link
   * JavaFxInvocationExecutor} for the {@link ThreadAffinity#FX_THREAD} affinity key. The executor
   * marshals invocations onto the JavaFX Application Thread.
   *
   * @return the configured thread affinity dispatcher
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  public ThreadAffinityDispatcher provideThreadAffinityDispatcher() {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    String fxThreadEnabled = properties.getProperty("execution.fx.thread.enabled", "false");
    if (Boolean.parseBoolean(fxThreadEnabled)) {
      long timeoutMs = Long.parseLong(properties.getProperty("execution.fx.timeout.ms", "30000"));
      dispatcher.register(
          ThreadAffinity.FX_THREAD, new JavaFxInvocationExecutor(timeoutMs, customClassloader));
    }
    return dispatcher;
  }

  /**
   * Provides the {@link ReplayContext} for deterministic WAL replay, or {@code null} when the peer
   * is not running in replay mode.
   *
   * <p>When {@link RunOptions#WITH_REPLAY} is set, the entire WAL is loaded into memory during peer
   * initialization (before {@code main()} is called). This is acceptable for Phase 1's scope of
   * single-threaded debugging sessions.
   *
   * <p>Supports both Chronicle Queue and Kafka WAL backends. The backend is determined by the
   * {@code replay.wal.path} property: paths starting with {@code file:} use Chronicle Queue, all
   * others are treated as Kafka topic names (requiring {@code replay.kafka.servers}).
   *
   * @return the replay context, or {@code null} if not in replay mode
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Nullable
  ReplayContext provideReplayContext() {
    if (!runOptions.contains(RunOptions.WITH_REPLAY)) {
      return null;
    }

    String walPathStr = properties.getProperty("replay.wal.path");
    if (walPathStr == null || walPathStr.isBlank()) {
      throw new IllegalStateException("WITH_REPLAY is set but replay.wal.path is not configured");
    }

    List<WalEntry> entries;
    if (WalReader.isChronicleLog(walPathStr)) {
      Path walPath = resolveReplayWalPath(walPathStr);
      entries = WalReader.readChronicleWal(walPath);
    } else {
      String kafkaServers = properties.getProperty("replay.kafka.servers");
      if (kafkaServers == null || kafkaServers.isBlank()) {
        throw new IllegalStateException("Kafka replay WAL requires replay.kafka.servers property");
      }
      entries = WalReader.readKafkaWal(kafkaServers, walPathStr);
    }

    WalIndex index = WalIndex.build(entries);
    logger.info(
        "Replay WAL loaded: {} entries, {} structural issues",
        entries.size(),
        index.getStructuralIssues().size());

    String policyStr = properties.getProperty("replay.divergence.policy", "WARN");
    DivergenceDetector.DivergencePolicy policy;
    try {
      policy = DivergenceDetector.DivergencePolicy.valueOf(policyStr);
    } catch (IllegalArgumentException e) {
      logger.warn("Unknown replay divergence policy '{}', defaulting to WARN", policyStr);
      policy = DivergenceDetector.DivergencePolicy.WARN;
    }

    String threading = properties.getProperty("replay.threading", "ordered");
    boolean ordered = !"unordered".equalsIgnoreCase(threading);

    String delayStr = properties.getProperty("replay.delay", "0");
    long operationDelayMs = 0L;
    try {
      operationDelayMs = Long.parseLong(delayStr);
    } catch (NumberFormatException e) {
      logger.warn("Invalid replay.delay value '{}', defaulting to 0 (no delay)", delayStr);
    }

    ReplayPolicy replayPolicy = buildReplayPolicy();

    List<SideEffectAnalyzer.UnsafeStubWarning> warnings =
        new SideEffectAnalyzer().analyze(index, replayPolicy);
    if (!warnings.isEmpty()) {
      boolean forceStub =
          Boolean.parseBoolean(properties.getProperty("replay.force.stub", "false"));
      if (forceStub) {
        logger.warn(
            "Side-effect analysis detected {} unsafe stub(s); proceeding due to --force-stub",
            warnings.size());
      } else {
        throw new IllegalStateException(
            "Side-effect analysis detected "
                + warnings.size()
                + " unsafe stub(s). Use --force-stub to proceed anyway. "
                + "See warnings above for details.");
      }
    }

    return new ReplayContext(
        index,
        replayPolicy,
        new ReplayObjectStore(),
        new DivergenceDetector(policy),
        new ReplayGate(ordered),
        operationDelayMs);
  }

  /**
   * Builds a {@link ReplayPolicy} from the configured properties.
   *
   * <p>Reads the replay policy configuration from properties set by the CLI layer ({@code
   * replay.policy.path}, {@code replay.shield.io}, {@code replay.shield.fx}, {@code
   * replay.re-execute.patterns}, {@code replay.stub.patterns}, {@code replay.stub.all.else}). If no
   * policy-related properties are set, returns a default policy that re-executes all operations.
   *
   * @return the constructed replay policy
   */
  private ReplayPolicy buildReplayPolicy() {
    String policyPath = properties.getProperty("replay.policy.path");
    boolean shieldIo = Boolean.parseBoolean(properties.getProperty("replay.shield.io", "false"));
    boolean shieldFx = Boolean.parseBoolean(properties.getProperty("replay.shield.fx", "false"));
    String reExecStr = properties.getProperty("replay.re-execute.patterns");
    String stubStr = properties.getProperty("replay.stub.patterns");
    boolean stubAllElse =
        Boolean.parseBoolean(properties.getProperty("replay.stub.all.else", "false"));

    boolean hasAnyPolicyConfig =
        policyPath != null
            || shieldIo
            || shieldFx
            || reExecStr != null
            || stubStr != null
            || stubAllElse;

    if (!hasAnyPolicyConfig) {
      return new ReplayPolicy();
    }

    String[] reExecPatterns = reExecStr != null ? reExecStr.split(",") : null;
    String[] stubPatterns = stubStr != null ? stubStr.split(",") : null;

    return ReplayPolicyParser.fromOptions(
        policyPath, shieldIo, shieldFx, reExecPatterns, stubPatterns, stubAllElse);
  }

  /**
   * Provides the singleton {@link RpcPolicy} for the peer.
   *
   * <p>Delegates to {@link #buildRpcPolicy()} to construct the policy from CLI properties ({@code
   * rpc.policy.path}, {@code rpc.policy.presets}, {@code rpc.default_action}).
   *
   * @return the constructed RPC policy
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  RpcPolicy provideRpcPolicy() {
    return buildRpcPolicy();
  }

  /**
   * Provides a nullable {@link RpcPolicyFileWatcher} singleton.
   *
   * <p>Returns {@code null} when no {@code rpc.policy.path} is configured (there is no file to
   * watch) or when the poll interval ({@code rpc.policy.watch.interval.ms}) is set to {@code 0}
   * (watching explicitly disabled). Otherwise, constructs a watcher that will poll the policy file
   * at the configured interval and update the given {@code policyHolder} on changes.
   *
   * @param policyHolder the holder whose policy is updated on successful reload
   * @return a new watcher, or {@code null} if watching is not configured or disabled
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  @Nullable
  RpcPolicyFileWatcher provideRpcPolicyFileWatcher(RpcPolicyHolder policyHolder) {
    String policyPath = properties.getProperty("rpc.policy.path");
    if (policyPath == null) {
      return null;
    }
    long pollInterval =
        Long.parseLong(
            properties.getProperty(
                "rpc.policy.watch.interval.ms",
                String.valueOf(RpcPolicyFileWatcher.DEFAULT_POLL_INTERVAL_MS)));
    if (pollInterval == 0) {
      return null;
    }
    String presets = properties.getProperty("rpc.policy.presets");
    String defaultAction = properties.getProperty("rpc.default_action");
    return new RpcPolicyFileWatcher(
        Path.of(policyPath), presets, defaultAction, policyHolder, pollInterval);
  }

  /**
   * Provides the {@link RecordingScope} for filtering operations from WAL/PUB writes.
   *
   * <p>Reads scope configuration from properties set by the CLI layer:
   *
   * <ul>
   *   <li>{@code scope.patterns} &mdash; comma-separated Ant-style include patterns
   *   <li>{@code scope.exclude.patterns} &mdash; comma-separated Ant-style exclude patterns
   *   <li>{@code scope.io} &mdash; whether to include built-in I/O boundary rules
   *   <li>{@code scope.policy.path} &mdash; path to a YAML scope policy file
   *   <li>{@code scope.default.action} &mdash; default action when no rule matches
   * </ul>
   *
   * <p>When none of these properties are set, returns a permit-all scope (no filtering).
   *
   * @return the recording scope
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  RecordingScope provideRecordingScope() {
    String yamlPath = properties.getProperty("scope.policy.path");
    boolean includeIo = Boolean.parseBoolean(properties.getProperty("scope.io", "false"));
    String includePatterns = properties.getProperty("scope.patterns");
    String excludePatterns = properties.getProperty("scope.exclude.patterns");
    String defaultActionStr = properties.getProperty("scope.default.action");

    return RecordingScopeParser.fromOptions(
        yamlPath,
        includeIo,
        includePatterns != null ? includePatterns.split(",") : null,
        excludePatterns != null ? excludePatterns.split(",") : null,
        defaultActionStr);
  }

  /**
   * Builds an {@link RpcPolicy} from the configured properties.
   *
   * <p>Reads the RPC policy configuration from properties set by the CLI layer ({@code
   * rpc.policy.path}, {@code rpc.policy.presets}, {@code rpc.default_action}). If no policy-related
   * properties are set, returns a deny-all policy with no rules, so that peers deny all RPC
   * operations unless explicitly allowed.
   *
   * @return the constructed RPC policy
   */
  private RpcPolicy buildRpcPolicy() {
    String policyPath = properties.getProperty("rpc.policy.path");
    String presets = properties.getProperty("rpc.policy.presets");
    String defaultAction = properties.getProperty("rpc.default_action");

    boolean hasAnyConfig = policyPath != null || presets != null;

    if (!hasAnyConfig && defaultAction == null) {
      return new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    }

    return RpcPolicyParser.fromOptions(policyPath, presets, defaultAction);
  }

  /**
   * Resolves the replay WAL path from the {@code file:} prefixed specification.
   *
   * <p>Strips the {@code file:} prefix and resolves relative paths against the Chronicle base
   * directory (if configured) or the current working directory.
   *
   * @param walPathStr the WAL path specification (e.g., {@code file:/tmp/my-wal})
   * @return the resolved filesystem path
   */
  private Path resolveReplayWalPath(String walPathStr) {
    String pathPart;
    if (walPathStr.startsWith("file:")) {
      pathPart = walPathStr.substring("file:".length());
    } else {
      pathPart = walPathStr;
    }

    Path path = Paths.get(pathPart);
    if (!path.isAbsolute()) {
      String baseDir = properties.getProperty("wal.chronicle.base_dir");
      if (baseDir != null && !baseDir.isBlank()) {
        path = Paths.get(baseDir).resolve(path);
      }
    }
    return path;
  }
}
