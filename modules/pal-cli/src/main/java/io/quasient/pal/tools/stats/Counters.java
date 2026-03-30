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
package io.quasient.pal.tools.stats;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Counters class aggregates various statistical metrics based on the Pal's messages, such as
 * requests and replies, object creation, method invocations, and field accesses.
 */
public class Counters {

  /** Tracks the total number of messages processed. */
  private final AtomicLong numberOfMessages = new AtomicLong();

  /** Maps each message type to the number of messages of that type processed. */
  private final Map<String, AtomicLong> messagesByType = new HashMap<>();

  /** Maps each peer identifier to the number of messages received from that peer. */
  private final Map<String, AtomicLong> messagesFromPeer = new HashMap<>();

  /** Tracks the total number of requests made. */
  private final AtomicLong requests = new AtomicLong();

  /** Tracks the total number of replies sent. */
  private final AtomicLong replies = new AtomicLong();

  /** Maps each class name to the number of objects created by that class. */
  private final Map<String, AtomicLong> objectsCreated = new HashMap<>();

  /** Maps each method name to the number of times it has been called. */
  private final Map<String, AtomicLong> methodsCalled = new HashMap<>();

  /** Maps each field name to the number of times it has been read. */
  private final Map<String, AtomicLong> fieldReads = new HashMap<>();

  /** Maps each field name to the number of times it has been written to. */
  private final Map<String, AtomicLong> fieldWrites = new HashMap<>();

  /** Maps each thread name to the number of messages processed by that thread. */
  private final Map<String, AtomicLong> messagesByThread = new HashMap<>();

  /** Maps each thread name to the number of unique dispatches performed by that thread. */
  private final Map<String, AtomicLong> dispatchesByThread = new HashMap<>();

  /** Maps each exception type to the number of times it was thrown. */
  private final Map<String, AtomicLong> exceptionsByType = new HashMap<>();

  /** Maps each method to the number of exceptions thrown from it. */
  private final Map<String, AtomicLong> exceptionsPerMethod = new HashMap<>();

  /** Tracks the total number of entry-point messages (RPC calls). */
  private final AtomicLong entryPointCount = new AtomicLong();

  /** Tracks the earliest message timestamp in nanoseconds. Zero means no messages seen yet. */
  private long firstMessageTimeNanos;

  /** Tracks the latest message timestamp in nanoseconds. */
  private long lastMessageTimeNanos;

  /** Constructs a new Counters instance with all counts initialized to zero. */
  public Counters() {}

  /**
   * Retrieves an unmodifiable view of the mapping of message types to their respective counts.
   *
   * @return an unmodifiable map where keys are message types and values are the number of messages
   *     of that type.
   */
  public Map<String, AtomicLong> getMessagesByType() {
    return Collections.unmodifiableMap(messagesByType);
  }

  /**
   * Retrieves an unmodifiable view of the mapping of peer identifiers to the number of messages
   * received from each.
   *
   * @return an unmodifiable map where keys are peer identifiers and values are the number of
   *     messages received from each peer.
   */
  public Map<String, AtomicLong> getMessagesFromPeer() {
    return Collections.unmodifiableMap(messagesFromPeer);
  }

  /**
   * Retrieves the total number of requests made.
   *
   * @return an AtomicLong representing the total number of requests.
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "AtomicLong intentionally shared for thread-safe counter reads")
  @SuppressWarnings("unused")
  public AtomicLong getRequests() {
    return requests;
  }

  /**
   * Retrieves the total number of replies sent.
   *
   * @return an AtomicLong representing the total number of replies.
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "AtomicLong intentionally shared for thread-safe counter reads")
  @SuppressWarnings("unused")
  public AtomicLong getReplies() {
    return replies;
  }

  /**
   * Retrieves an unmodifiable view of the mapping of class names to the number of objects created
   * by each class.
   *
   * @return an unmodifiable map where keys are class names and values are the number of objects
   *     created by each class.
   */
  public Map<String, AtomicLong> getObjectsCreated() {
    return Collections.unmodifiableMap(objectsCreated);
  }

  /**
   * Retrieves an unmodifiable view of the mapping of thread names to the number of messages
   * processed by each thread.
   *
   * @return an unmodifiable map where keys are thread names and values are the number of messages
   *     processed by each thread.
   */
  public Map<String, AtomicLong> getMessagesByThread() {
    return Collections.unmodifiableMap(messagesByThread);
  }

  /**
   * Retrieves an unmodifiable view of the mapping of thread names to the number of unique
   * dispatches performed by each thread.
   *
   * @return an unmodifiable map where keys are thread names and values are the number of unique
   *     dispatches performed by each thread.
   */
  @SuppressWarnings("unused")
  public Map<String, AtomicLong> getDispatchesByThread() {
    return Collections.unmodifiableMap(dispatchesByThread);
  }

  /**
   * Retrieves the total number of messages processed.
   *
   * @return an AtomicLong representing the total number of messages.
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "AtomicLong intentionally shared for thread-safe counter reads")
  public AtomicLong getNumberOfMessages() {
    return numberOfMessages;
  }

  /**
   * Retrieves an unmodifiable view of the mapping of method names to the number of times each
   * method has been called.
   *
   * @return an unmodifiable map where keys are method names and values are the number of times each
   *     method has been called.
   */
  public Map<String, AtomicLong> getMethodsCalled() {
    return Collections.unmodifiableMap(methodsCalled);
  }

  /**
   * Retrieves an unmodifiable view of the mapping of field names to the number of times each field
   * has been read.
   *
   * @return an unmodifiable map where keys are field names and values are the number of times each
   *     field has been read.
   */
  public Map<String, AtomicLong> getFieldReads() {
    return Collections.unmodifiableMap(fieldReads);
  }

  /**
   * Retrieves an unmodifiable view of the mapping of field names to the number of times each field
   * has been written to.
   *
   * @return an unmodifiable map where keys are field names and values are the number of times each
   *     field has been written to.
   */
  public Map<String, AtomicLong> getFieldWrites() {
    return Collections.unmodifiableMap(fieldWrites);
  }

  /**
   * Increments the counter for a specific message type, creating a new counter if needed.
   *
   * @param type the message type key
   */
  public void incrementMessagesByType(String type) {
    messagesByType.computeIfAbsent(type, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for messages from a specific peer, creating a new counter if needed.
   *
   * @param peerId the peer identifier
   */
  public void incrementMessagesFromPeer(String peerId) {
    messagesFromPeer.computeIfAbsent(peerId, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for messages from a specific thread, creating a new counter if needed.
   *
   * @param threadName the thread name
   */
  public void incrementMessagesByThread(String threadName) {
    messagesByThread.computeIfAbsent(threadName, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for objects created of a specific class, creating a new counter if
   * needed.
   *
   * @param className the class name key
   */
  public void incrementObjectsCreated(String className) {
    objectsCreated.computeIfAbsent(className, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for method calls, creating a new counter if needed.
   *
   * @param methodKey the method key (e.g., "ClassName.methodName()")
   */
  public void incrementMethodsCalled(String methodKey) {
    methodsCalled.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for field reads, creating a new counter if needed.
   *
   * @param fieldKey the field key (e.g., "ClassName.fieldName")
   */
  public void incrementFieldReads(String fieldKey) {
    fieldReads.computeIfAbsent(fieldKey, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Increments the counter for field writes, creating a new counter if needed.
   *
   * @param fieldKey the field key (e.g., "ClassName.fieldName")
   */
  public void incrementFieldWrites(String fieldKey) {
    fieldWrites.computeIfAbsent(fieldKey, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Retrieves an unmodifiable view of the mapping of exception types to their throw counts.
   *
   * @return an unmodifiable map where keys are exception class names and values are throw counts
   */
  public Map<String, AtomicLong> getExceptionsByType() {
    return Collections.unmodifiableMap(exceptionsByType);
  }

  /**
   * Increments the counter for a specific exception type, creating a new counter if needed.
   *
   * @param exceptionType the fully qualified exception class name
   */
  public void incrementExceptionsByType(String exceptionType) {
    exceptionsByType.computeIfAbsent(exceptionType, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Retrieves an unmodifiable view of the mapping of methods to their exception counts.
   *
   * @return an unmodifiable map where keys are method keys and values are exception counts
   */
  public Map<String, AtomicLong> getExceptionsPerMethod() {
    return Collections.unmodifiableMap(exceptionsPerMethod);
  }

  /**
   * Increments the counter for exceptions thrown from a specific method, creating a new counter if
   * needed.
   *
   * @param methodKey the method key (e.g., "ClassName.methodName()")
   */
  public void incrementExceptionsPerMethod(String methodKey) {
    exceptionsPerMethod.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
  }

  /**
   * Retrieves the total number of entry-point messages (RPC calls).
   *
   * @return an AtomicLong representing the entry-point count
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "AtomicLong intentionally shared for thread-safe counter reads")
  public AtomicLong getEntryPointCount() {
    return entryPointCount;
  }

  /**
   * Retrieves the earliest message timestamp in nanoseconds.
   *
   * @return the first message timestamp, or zero if no messages have been processed
   */
  public long getFirstMessageTimeNanos() {
    return firstMessageTimeNanos;
  }

  /**
   * Retrieves the latest message timestamp in nanoseconds.
   *
   * @return the last message timestamp, or zero if no messages have been processed
   */
  public long getLastMessageTimeNanos() {
    return lastMessageTimeNanos;
  }

  /**
   * Updates the time span tracking with the given message timestamp.
   *
   * <p>Maintains the earliest and latest timestamps seen across all processed messages. Timestamps
   * of zero are ignored (they indicate missing timing data).
   *
   * @param timeNanos the message timestamp in nanoseconds
   */
  public void updateTimeSpan(long timeNanos) {
    if (timeNanos == 0) {
      return;
    }
    if (firstMessageTimeNanos == 0 || timeNanos < firstMessageTimeNanos) {
      firstMessageTimeNanos = timeNanos;
    }
    if (timeNanos > lastMessageTimeNanos) {
      lastMessageTimeNanos = timeNanos;
    }
  }
}
