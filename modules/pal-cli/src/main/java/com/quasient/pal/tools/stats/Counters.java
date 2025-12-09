/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.stats;

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
}
