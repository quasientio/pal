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

package net.ittera.pal.core.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

public abstract class ExecThreadFactory implements ThreadFactory {

  private final List<Thread> createdThreads = new ArrayList<>();

  protected ThreadGroup threadGroup;
  protected final AtomicInteger threadCounter = new AtomicInteger(0);

  private static final int THREAD_GROUP_MAX_PRIORITY = Thread.NORM_PRIORITY;
  private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;
  private static final boolean THREAD_GROUP_IS_DAEMON = false;
  private static final boolean THREAD_IS_DAEMON = false;

  private static final Logger logger = LoggerFactory.getLogger(ExecThreadFactory.class);

  private ExecChannelType execChannelType;
  protected MessageBuilder messageBuilder;
  protected DispatcherConnector dispatcherConnector;
  protected IncomingMessageDispatcher incomingMessageDispatcher;

  // zmq stuff
  protected ZContext zmqContext;
  protected UUID peerUuid;
  private ClassLoader classLoader;

  enum ExecChannelType {
    PEER("peer"),
    LOG("log");

    final String name;

    ExecChannelType(String name) {
      this.name = name;
    }
  }

  protected void init(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      ExecChannelType execChannelType,
      ClassLoader classLoader,
      UUID peerUuid) {
    this.execChannelType = execChannelType;
    threadGroup = new ThreadGroup(getThreadGroupName());
    threadGroup.setDaemon(THREAD_GROUP_IS_DAEMON);
    threadGroup.setMaxPriority(THREAD_GROUP_MAX_PRIORITY);
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.dispatcherConnector = dispatcherConnector;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.classLoader = classLoader;
    this.peerUuid = peerUuid;
    logger.info(
        "Initialized exec thread factory with group name: {}, daemon: {}, maxPriority: {}",
        getThreadGroupName(),
        THREAD_GROUP_IS_DAEMON,
        THREAD_GROUP_MAX_PRIORITY);
  }

  protected abstract AbstractMessageInvokerThread createInvokerThread(String threadName);

  @Override
  public Thread newThread(Runnable r) {
    final String newThreadName = getThreadBaseName() + ' ' + threadCounter.getAndIncrement();
    final Thread thread = createInvokerThread(newThreadName);
    thread.setContextClassLoader(classLoader);
    thread.setPriority(THREAD_PRIORITY);
    thread.setDaemon(THREAD_IS_DAEMON);
    thread.setUncaughtExceptionHandler(
        (t, e) ->
            logger.error(
                "Uncaught exception in {} exec thread: {}",
                execChannelType.name,
                newThreadName,
                e));
    addCreatedThread(thread);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Created new {} executor thread with name: '{}' and id: {}",
          execChannelType.name,
          newThreadName,
          thread.getId());
    }
    return thread;
  }

  public List<Thread> getCreatedThreads() {
    return createdThreads;
  }

  private void addCreatedThread(Thread t) {
    createdThreads.add(t);
  }

  protected String getThreadBaseName() {
    return String.format("%s Executor", Strings.capitalize(execChannelType.name));
  }

  private String getThreadGroupName() {
    return String.format("%s Executor Group", Strings.capitalize(execChannelType.name));
  }
}
