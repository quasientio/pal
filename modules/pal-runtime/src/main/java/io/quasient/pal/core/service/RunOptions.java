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
package io.quasient.pal.core.service;

/**
 * Enumerates the available runtime configuration options for the Pal runtime.
 *
 * <p>Each constant represents a specific feature that can be enabled to modify system behavior,
 * such as configuring directory connectivity or enabling communication interfaces.
 */
public enum RunOptions {

  /**
   * Enables connection to the PAL directory.
   *
   * <p>When activated, this option allows the system to interface with the PAL directory.
   */
  WITH_PALDIR,

  /**
   * Enables handling of BIN-RPC requests.
   *
   * <p>This option configures the system to listen for and process remote procedure call requests
   * over a ZMQ socket.
   */
  WITH_ZMQ_RPC,

  /**
   * Enables handling of JSON-RPC requests.
   *
   * <p>Activating this option allows the system to receive and process JSON-formatted RPC requests
   * over Websocket.
   */
  WITH_JSON_RPC,

  /**
   * Enables publishing messages via TCP.
   *
   * <p>When selected, this option configures the system to publish messages over a ZMQ TCP PUB
   * socket
   */
  WITH_TCP_PUB,

  /** Allows interception of messages. */
  WITH_INTERCEPTS,

  /**
   * Enables reading and dispatching messages from the configured source Log (event-sourcing,
   * WAL-replay).
   */
  WITH_SOURCE_LOG,

  /** Enables write-ahead publishing of messages to the Log. */
  WITH_WAL,

  /**
   * Enables write-ahead logging of incoming RPC calls.
   *
   * <p>When enabled, incoming messages from {@code ZMQ_SOCKET_RPC} and {@code WEBSOCKET_RPC}
   * channels are written to WAL/PUB in both BEFORE and AFTER phases, consistent with the hot-path
   * {@code dispatch()} behavior. The {@code CLI_RPC} channel (used by {@link
   * io.quasient.pal.core.service.SelfBootstrapInvoker}) is independently controlled by {@link
   * #WITH_WAL_INCOMING_CLI}. Messages arriving via {@code LOG_RPC} are excluded to prevent circular
   * writes; use {@link #WITH_WAL_ALL_INCOMING_RPC} to include those.
   */
  WITH_WAL_INCOMING_RPC,

  /**
   * Enables write-ahead logging of incoming CLI bootstrap calls.
   *
   * <p>When enabled, incoming messages from the {@code CLI_RPC} channel (used by {@link
   * io.quasient.pal.core.service.SelfBootstrapInvoker}) are written to WAL/PUB in both BEFORE and
   * AFTER phases. This is independent of {@link #WITH_WAL_INCOMING_RPC}, which controls {@code
   * ZMQ_SOCKET_RPC} and {@code WEBSOCKET_RPC} channels.
   */
  WITH_WAL_INCOMING_CLI,

  /**
   * Extends {@link #WITH_WAL_INCOMING_RPC} to also include {@code LOG_RPC} channel messages.
   *
   * <p>This is intended for scenarios where the source log and WAL are different (e.g., consuming
   * from one Kafka topic while writing WAL to another). When the source log and WAL are the same
   * log, this option is overridden by the circularity guard to prevent infinite feedback loops.
   */
  WITH_WAL_ALL_INCOMING_RPC,

  /** Enables sessions - automatically set if any RPC interface is enabled */
  WITH_SESSIONS,

  /**
   * Enables in-flight dispatch tracking for intercept coordination.
   *
   * <p>When activated, this option enables tracking of method calls that are currently being
   * executed (in-flight dispatches). This is used to coordinate intercept registration with ongoing
   * method executions, ensuring that intercepts can wait for in-flight calls to complete before
   * activation (guaranteed quiescence).
   *
   * <p>This option controls whether new method calls are temporarily blocked (fenced) while waiting
   * for existing in-flight calls to drain, allowing safe intercept activation without race
   * conditions.
   */
  WITH_IN_FLIGHT_TRACKING,

  /**
   * Enables WAL-guided deterministic replay mode.
   *
   * <p>When activated, the peer re-executes the application from {@code main()} while verifying
   * each operation against a pre-recorded WAL oracle. Mutually exclusive with {@link #WITH_WAL},
   * {@link #WITH_SOURCE_LOG}, and the {@code --log} shorthand.
   */
  WITH_REPLAY
}
