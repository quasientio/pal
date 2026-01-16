/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
/**
 * ZeroMQ socket utilities and RPC server infrastructure.
 *
 * <p>PAL uses ZeroMQ for peer-to-peer RPC communication. This package provides:
 *
 * <ul>
 *   <li>{@link ZmqRpcServer} - REQ-REP based RPC server
 *   <li>{@link ZmqUtils} - Socket configuration utilities
 * </ul>
 *
 * @see io.quasient.pal.core.transport.zmq.publish
 */
package io.quasient.pal.core.transport.zmq;
