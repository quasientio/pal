/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

import com.quasient.pal.core.objects.ObjectLookupStore;
import com.quasient.pal.core.rpc.DispatcherConnector;
import com.quasient.pal.core.rpc.exec.java.reflect.ReflectionHelper;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a foundational implementation for dispatchers by configuring essential components via
 * dependency injection.
 *
 * <p>Subclasses should extend this abstract class to implement specific dispatch logic.
 */
abstract class AbstractDispatcher {
  /** Logger instance used for logging internal events and error messages. */
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** Unique identifier for the peer involved in dispatch operations. */
  protected UUID peerUuid;

  /** Instance used to construct messages. */
  protected MessageBuilder messageBuilder;

  /** Store used for mapping object references. */
  protected ObjectLookupStore objectLookupStore;

  /** Helper instance used to perform reflective operations on classes and methods. */
  protected ReflectionHelper reflectionHelper;

  /** Connector responsible for managing the routing and dispatching of calls. */
  protected DispatcherConnector connector;

  /**
   * Flag indicating whether non-public methods and fields can be accessed during RPC invocation.
   */
  protected boolean allowNonPublicAccess;

  /**
   * Sets the unique identifier (UUID) for the peer.
   *
   * @param peerUuid the unique identifier assigned to the peer; must not be null.
   */
  @Inject
  final void setPeerUuid(UUID peerUuid) {
    this.peerUuid = peerUuid;
  }

  /**
   * Specifies the {@link MessageBuilder} instance used for assembling messages.
   *
   * @param messageBuilder the message builder to use; cannot be null.
   */
  @Inject
  final void setMessageBuilder(MessageBuilder messageBuilder) {
    this.messageBuilder = messageBuilder;
  }

  /**
   * Configures the {@link ObjectLookupStore} for mapping object references.
   *
   * @param objectLookupStore the object lookup store to be used for mapping refs -> objects.
   */
  @Inject
  final void setObjectLookupStore(ObjectLookupStore objectLookupStore) {
    this.objectLookupStore = objectLookupStore;
  }

  /**
   * Assigns the {@link DispatcherConnector} which handles the routing of calls.
   *
   * @param connector the dispatcher connector; must be a valid, initialized instance.
   */
  @Inject
  final void setConnector(DispatcherConnector connector) {
    this.connector = connector;
  }

  /**
   * Injects the {@link ReflectionHelper} instance used for reflective access during RPC processing.
   *
   * @param reflectionHelper the helper instance to use for reflection; expected to be non-null.
   */
  @Inject
  final void setReflectionHelper(ReflectionHelper reflectionHelper) {
    this.reflectionHelper = reflectionHelper;
  }

  /**
   * Configures whether non-public access is allowed during RPC invocations.
   *
   * @param allowNonPublicAccess a String representing a boolean value ("true" or "false") that
   *     enables or disables non-public member access.
   */
  @Inject
  final void setAllowNonPublicAccess(@Named("rpc.allow_nonpublic") String allowNonPublicAccess) {
    this.allowNonPublicAccess = Boolean.parseBoolean(allowNonPublicAccess);
  }
}
