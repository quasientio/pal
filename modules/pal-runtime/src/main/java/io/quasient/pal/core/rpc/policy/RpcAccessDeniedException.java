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
