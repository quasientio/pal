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
package io.quasient.pal.core.transport.gateway;

/**
 * Immutable snapshot of queue backpressure metrics for a single producer thread.
 *
 * @param threadId JVM thread id ({@link Thread#getId()}).
 * @param threadName Human-friendly name of the thread ({@link Thread#getName()}), captured at
 *     snapshot time.
 * @param parkedNanos Total nanoseconds this thread spent parked (via {@code LockSupport.parkNanos})
 *     while retrying offers.
 * @param parks Number of times the thread actually parked (i.e. back-off iterations that called
 *     {@code parkNanos(..)}).
 * @param failedOffers How many {@code walQueue.offer(..)} attempts returned {@code false} before
 *     succeeding.
 */
public record ThreadWaitSnapshot(
    long threadId, String threadName, long parkedNanos, int parks, int failedOffers) {}
