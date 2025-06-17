/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.tools.stats;

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

  /*
  TODO:
  - messages / sec(min,hour)
  - calls by visibility (public, private, etc. ie, modifiers)
   */

  /** Constructs a new Counters instance with all counts initialized to zero. */
  public Counters() {}

  /**
   * Retrieves the mapping of message types to their respective counts.
   *
   * @return a map where keys are message types and values are the number of messages of that type.
   */
  public Map<String, AtomicLong> getMessagesByType() {
    return messagesByType;
  }

  /**
   * Retrieves the mapping of peer identifiers to the number of messages received from each.
   *
   * @return a map where keys are peer identifiers and values are the number of messages received
   *     from each peer.
   */
  public Map<String, AtomicLong> getMessagesFromPeer() {
    return messagesFromPeer;
  }

  /**
   * Retrieves the total number of requests made.
   *
   * @return an AtomicLong representing the total number of requests.
   */
  @SuppressWarnings("unused")
  public AtomicLong getRequests() {
    return requests;
  }

  /**
   * Retrieves the total number of replies sent.
   *
   * @return an AtomicLong representing the total number of replies.
   */
  @SuppressWarnings("unused")
  public AtomicLong getReplies() {
    return replies;
  }

  /**
   * Retrieves the mapping of class names to the number of objects created by each class.
   *
   * @return a map where keys are class names and values are the number of objects created by each
   *     class.
   */
  public Map<String, AtomicLong> getObjectsCreated() {
    return objectsCreated;
  }

  /**
   * Retrieves the mapping of thread names to the number of messages processed by each thread.
   *
   * @return a map where keys are thread names and values are the number of messages processed by
   *     each thread.
   */
  public Map<String, AtomicLong> getMessagesByThread() {
    return messagesByThread;
  }

  /**
   * Retrieves the mapping of thread names to the number of unique dispatches performed by each
   * thread.
   *
   * @return a map where keys are thread names and values are the number of unique dispatches
   *     performed by each thread.
   */
  @SuppressWarnings("unused")
  public Map<String, AtomicLong> getDispatchesByThread() {
    return dispatchesByThread;
  }

  /**
   * Retrieves the total number of messages processed.
   *
   * @return an AtomicLong representing the total number of messages.
   */
  public AtomicLong getNumberOfMessages() {
    return numberOfMessages;
  }

  /**
   * Retrieves the mapping of method names to the number of times each method has been called.
   *
   * @return a map where keys are method names and values are the number of times each method has
   *     been called.
   */
  public Map<String, AtomicLong> getMethodsCalled() {
    return methodsCalled;
  }

  /**
   * Retrieves the mapping of field names to the number of times each field has been read.
   *
   * @return a map where keys are field names and values are the number of times each field has been
   *     read.
   */
  public Map<String, AtomicLong> getFieldReads() {
    return fieldReads;
  }

  /**
   * Retrieves the mapping of field names to the number of times each field has been written to.
   *
   * @return a map where keys are field names and values are the number of times each field has been
   *     written to.
   */
  public Map<String, AtomicLong> getFieldWrites() {
    return fieldWrites;
  }
}
