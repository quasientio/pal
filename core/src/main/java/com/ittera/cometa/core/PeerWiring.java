package com.ittera.cometa.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.ittera.cometa.common.ConcurrentHashMapObjectStore;
import com.ittera.cometa.common.ObjectStore;
import com.ittera.cometa.common.lang.DispatchForwarder;
import com.ittera.cometa.common.lang.ProxyDispatcher;
import com.ittera.cometa.core.exec.java.AspectProxyDispatcher;
import com.ittera.cometa.core.exec.java.CustomClassloader;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

class PeerWiring extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(PeerWiring.class);
  private final Properties properties;
  private final ZContext zContext;
  private final UUID peerUuid;
  private final CustomClassloader customClassloader;
  private final Set<RunOptions> runOptions;
  private final ThreadGroup serviceThreadGroup = new ThreadGroup("service-threads");

  PeerWiring(
      Properties properties,
      Set<RunOptions> runOptions,
      ZContext zContext,
      CustomClassloader customClassloader) {
    if (logger.isInfoEnabled()) {
      logger.info("Created guice module with properties:{}", printedProperties(properties));
    }
    this.properties = properties;
    addServiceNamesToProps();
    this.runOptions = runOptions;
    this.zContext = zContext;
    this.peerUuid = UUID.fromString(properties.getProperty("id"));
    this.customClassloader = customClassloader;
  }

  private static String printedProperties(Properties props) {
    final StringBuilder sb = new StringBuilder();
    final List<String> keys = new ArrayList<>(props.stringPropertyNames());
    Collections.sort(keys);
    for (String key : keys) {
      sb.append("\n").append(key).append(":").append(props.getProperty(key));
    }
    return sb.toString();
  }

  private void addServiceNamesToProps() {
    // use underscore in names to better filter service-related traces
    properties.setProperty("LogReader.service", "Log_Reader");
    properties.setProperty("LogWriter.service", "Log_Writer");
    properties.setProperty("DirectRequestDispatcher.service", "Direct_Request_Dispatcher");
    properties.setProperty("OutgoingMessageDispatcher.service", "Outgoing_Message_Dispatcher");
    properties.setProperty("Intercepts.service", "Intercepts_Processor");
  }

  @Override
  protected void configure() {

    Names.bindProperties(binder(), properties);

    // bind implementations
    bind(ProxyDispatcher.class).to(com.ittera.cometa.core.exec.java.AspectProxyDispatcher.class);

    // common and cxn library classes are not annotated with @Singleton
    bind(ObjectStore.class).to(ConcurrentHashMapObjectStore.class).asEagerSingleton();
    bind(MessageBuilder.class).to(ProtobufMessageBuilder.class).asEagerSingleton();
    bind(PALDirectory.class).asEagerSingleton();

    // AspectProxy and DispatchForwarder's fields are static
    requestStaticInjection(AspectProxyDispatcher.class);
    requestStaticInjection(DispatchForwarder.class);
  }

  @Provides
  ZContext getZmqContext() {
    return zContext;
  }

  @Provides
  UUID getPeerUuid() {
    return peerUuid;
  }

  @Provides
  ThreadGroup getServiceThreadGroup() {
    return serviceThreadGroup;
  }

  @Provides
  CustomClassloader getCustomClassloader() {
    return customClassloader;
  }

  @Provides
  Set<RunOptions> getRunOptions() {
    return runOptions;
  }
}
