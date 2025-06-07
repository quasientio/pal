/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.runtime.DispatchForwarder;
import net.ittera.pal.common.runtime.ProxyDispatcher;
import net.ittera.pal.core.rpc.exec.java.AspectProxyDispatcher;
import net.ittera.pal.core.rpc.exec.java.CustomClassloader;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * Guice module for wiring peer components within the Pal runtime.
 *
 * <p>This module sets up dependency injection bindings for key runtime components, such as the
 * ZeroMQ context, custom class loader, and service thread management. It also initializes
 * configuration properties, including setting service-specific names and the unique peer
 * identifier.
 */
class PeerWiring extends AbstractModule {

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
  PeerWiring(
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
    properties.setProperty("LogWriter.service", "Log_Writer");
    properties.setProperty("ZmqRpcRequestDispatcher.service", "RPC_Request_Dispatcher");
    properties.setProperty("JsonRpcRequestDispatcher.service", "JSONRPC_Request_Dispatcher");
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

    // common and cxn library classes are not annotated with @Singleton
    bind(ObjectLookupStore.class).to(ConcurrentHashMapObjectLookupStore.class).asEagerSingleton();
    bind(MessageBuilder.class).asEagerSingleton();
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
  ZContext getZmqContext() {
    return zmqContext;
  }

  /**
   * Provides the unique peer identifier.
   *
   * @return the UUID associated with this peer, as derived from the configuration properties.
   */
  @Provides
  @SuppressWarnings("unused")
  UUID getPeerUuid() {
    return peerUuid;
  }

  /**
   * Provides the thread group for service-related threads.
   *
   * @return the ThreadGroup designated for organizing service threads.
   */
  @Provides
  @SuppressWarnings("unused")
  ThreadGroup getServiceThreadGroup() {
    return serviceThreadGroup;
  }

  /**
   * Provides the custom class loader for dynamic class loading.
   *
   * @return the CustomClassloader used for loading classes at runtime.
   */
  @Provides
  @SuppressWarnings({"unused", "CloseableProvides"})
  CustomClassloader getCustomClassloader() {
    return customClassloader;
  }

  /**
   * Provides the set of runtime options for the peer.
   *
   * @return a Set of RunOptions that dictate the peer's runtime behavior.
   */
  @Provides
  @SuppressWarnings("unused")
  Set<RunOptions> getRunOptions() {
    return runOptions;
  }
}
