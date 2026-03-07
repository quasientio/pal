/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import io.quasient.pal.core.transport.MessageChannelType;

/**
 * Thrown when an incoming RPC operation is denied by the {@link RpcPolicy}.
 *
 * <p>Contains the class name, member name, and channel that triggered the denial, formatted in the
 * message as {@code "RPC access denied: {className}.{memberName} via {channel}"}.
 */
public class RpcAccessDeniedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The fully-qualified class name of the denied operation's target. */
  private final String className;

  /** The method or field name of the denied operation. */
  private final String memberName;

  /** The message channel on which the denied operation arrived. */
  private final MessageChannelType channel;

  /**
   * Creates a new access-denied exception.
   *
   * @param className the fully-qualified class name of the target
   * @param memberName the method or field name being accessed
   * @param channel the message channel the operation arrived on
   */
  public RpcAccessDeniedException(String className, String memberName, MessageChannelType channel) {
    super("RPC access denied: " + className + "." + memberName + " via " + channel);
    this.className = className;
    this.memberName = memberName;
    this.channel = channel;
  }

  /**
   * Returns the class name of the denied operation's target.
   *
   * @return the fully-qualified class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Returns the member name of the denied operation.
   *
   * @return the method or field name
   */
  public String getMemberName() {
    return memberName;
  }

  /**
   * Returns the channel on which the denied operation arrived.
   *
   * @return the message channel type
   */
  public MessageChannelType getChannel() {
    return channel;
  }
}
