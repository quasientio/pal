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

package net.ittera.pal.tools.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Counters {

  private AtomicLong numberOfMessages = new AtomicLong();

  // types of
  private Map<String, AtomicLong> messagesByType = new HashMap<>();

  // messages by peer
  private Map<String, AtomicLong> messagesFromPeer = new HashMap<>();

  // requests
  private AtomicLong requests = new AtomicLong();

  // replies
  private AtomicLong replies = new AtomicLong();

  // objects created by class
  private Map<String, AtomicLong> objectsCreated = new HashMap<>();

  // static/instance methods called
  private Map<String, AtomicLong> methodsCalled = new HashMap<>();

  // static/instance field reads
  private Map<String, AtomicLong> fieldReads = new HashMap<>();

  // static/instance field writes
  private Map<String, AtomicLong> fieldWrites = new HashMap<>();

  // messages by threadName
  private Map<String, AtomicLong> messagesByThread = new HashMap<>();

  // unique dispatches by thread(Name)
  private Map<String, AtomicLong> dispatchesByThread = new HashMap<>();

  /*
  TODO:
  - msgs / sec(min,hour)
  - calls by visibility (public, private, etc. ie, modifiers)
   */

  public Counters() {}

  public Map<String, AtomicLong> getMessagesByType() {
    return messagesByType;
  }

  public Map<String, AtomicLong> getMessagesFromPeer() {
    return messagesFromPeer;
  }

  public AtomicLong getRequests() {
    return requests;
  }

  public AtomicLong getReplies() {
    return replies;
  }

  public Map<String, AtomicLong> getObjectsCreated() {
    return objectsCreated;
  }

  public Map<String, AtomicLong> getMessagesByThread() {
    return messagesByThread;
  }

  public Map<String, AtomicLong> getDispatchesByThread() {
    return dispatchesByThread;
  }

  public AtomicLong getNumberOfMessages() {
    return numberOfMessages;
  }

  public Map<String, AtomicLong> getMethodsCalled() {
    return methodsCalled;
  }

  public Map<String, AtomicLong> getFieldReads() {
    return fieldReads;
  }

  public Map<String, AtomicLong> getFieldWrites() {
    return fieldWrites;
  }
}
