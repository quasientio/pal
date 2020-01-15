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
