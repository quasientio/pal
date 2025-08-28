/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.quasient.pal.common.runtime.DispatchForwarder;
import com.quasient.pal.common.runtime.ProxyDispatcher;
import com.quasient.pal.core.annotations.AnnotationProcessor;
import com.quasient.pal.core.annotations.AnnotationsProcessor;
import com.quasient.pal.core.execution.java.AspectProxyDispatcher;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.internal.concurrent.MpscKind;
import com.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.core.transport.WalType;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.core.transport.chronicle.ChronicleQueueFactory;
import com.quasient.pal.core.transport.chronicle.ChronicleWalWriter;
import com.quasient.pal.core.transport.chronicle.DefaultChronicleQueueFactory;
import com.quasient.pal.core.transport.kafka.KafkaWalWriter;
import com.quasient.pal.core.transport.kafka.ProducerFactory;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import com.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.KafkaProducer;
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

    if (runOptions.contains(RunOptions.WITH_WAL)) {
      WalType walType =
          WalType.valueOf(properties.getProperty("wal.type").toUpperCase(Locale.ENGLISH));
      switch (walType) {
        case KAFKA -> bind(WalWriter.class).to(KafkaWalWriter.class).asEagerSingleton();
        case CHRONICLE -> {
          bind(WalWriter.class).to(ChronicleWalWriter.class).asEagerSingleton();
          bind(ChronicleQueueFactory.class)
              .to(DefaultChronicleQueueFactory.class)
              .asEagerSingleton();
        }
      }

      // Ensure AnnotationsProcessor is a singleton (in addition to any annotation used).
      bind(AnnotationsProcessor.class).asEagerSingleton();

      // Contribute implementations to the AnnotationProcessor set. Example:
      Multibinder<AnnotationProcessor> unused =
          Multibinder.newSetBinder(binder(), AnnotationProcessor.class);
      // Register an existing processor:
      // setBinder.addBinding().to(InterceptAnnotationProcessor.class);
    }

    // common and cxn library classes are not annotated with @Singleton
    bind(ObjectLookupStore.class)
        .toProvider(ConcurrentHashMapObjectLookupStore::createSyncManaged)
        .asEagerSingleton();
    bind(MessageBuilder.class).toProvider(() -> new MessageBuilder(peerUuid)).asEagerSingleton();
    bind(DirectoryConnectionProvider.class).asEagerSingleton();

    // AspectProxy and DispatchForwarder's fields are static
    requestStaticInjection(AspectProxyDispatcher.class);
    requestStaticInjection(DispatchForwarder.class);
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
   * @return base dir path for Chronicle queues
   */
  @SuppressWarnings("unused")
  @Provides
  @Named("chronicleBaseDir")
  Path provideChronicleBaseDir() {
    String pathStr = properties.getProperty("wal.chronicle.base_dir");
    if (pathStr == null) {
      throw new IllegalStateException("Missing property: wal.chronicle.base_dir");
    }
    return Paths.get(pathStr);
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
}
