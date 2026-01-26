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
import io.quasient.pal.common.runtime.DispatchForwarder;
import io.quasient.pal.common.runtime.ProxyDispatcher;
import io.quasient.pal.core.annotations.AnnotationProcessor;
import io.quasient.pal.core.annotations.AnnotationsProcessor;
import io.quasient.pal.core.dispatcher.InterceptAsyncThreadFactory;
import io.quasient.pal.core.execution.java.AspectProxyDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.intercept.ExceptionPolicyConfig;
import io.quasient.pal.core.intercept.ExceptionPolicyResolver;
import io.quasient.pal.core.intercept.InFlightDispatchTracker;
import io.quasient.pal.core.intercept.InterceptActivationCoordinator;
import io.quasient.pal.core.intercept.PendingInterceptActivation;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.concurrent.MpscKind;
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
import java.util.concurrent.Executors;
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
    return Long.parseLong(properties.getProperty("intercept.drain.timeout.ms", "5000"));
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
   * background without blocking the main execution thread. Uses a cached thread pool for efficient
   * resource utilization with varying callback loads.
   *
   * <p>The executor uses {@link InterceptAsyncThreadFactory} which ensures threads inherit the
   * {@link CustomClassloader} for proper class resolution during callback execution.
   *
   * @return an ExecutorService for async intercept callbacks
   */
  @SuppressWarnings({"unused", "CloseableProvides"})
  @Provides
  @Singleton
  @Named("intercept.async.executor")
  public ExecutorService provideInterceptAsyncExecutor() {
    return Executors.newCachedThreadPool(
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
}
