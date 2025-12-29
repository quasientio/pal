/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

/**
 * Lock-free concurrent data structures for high-performance message queuing.
 *
 * <ul>
 *   <li>{@link HwmMessageQueue} - High water mark message queue with backpressure support
 *   <li>{@link AdaptiveSpinParkWaitStrategy} - Adaptive spin-then-park wait strategy
 *   <li>{@link MpscKind} - Multi-producer-single-consumer queue type selection
 * </ul>
 */
package io.quasient.pal.core.internal.concurrent;
