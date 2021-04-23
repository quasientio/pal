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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.core.exec.java.CustomClassloader;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

@Singleton
public class LogMessageExecutor extends ExtendedThreadPoolExecutor {

  protected static final Logger logger = LoggerFactory.getLogger(LogMessageExecutor.class);

  @Inject
  public LogMessageExecutor(
      @Named("log.corePoolSize") String corePoolSize,
      @Named("log.maximumPoolSize") String maximumPoolSize,
      @Named("log.keepAliveSeconds") String keepAliveSeconds,
      ZContext zmqContext,
      @Named("in.log") String zmqSocketAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      CustomClassloader customClassloader,
      UUID peerUuid) {

    super(
        Integer.parseInt(corePoolSize),
        Integer.parseInt(maximumPoolSize),
        Integer.parseInt(keepAliveSeconds),
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ExecThreadFactory(
            zmqContext,
            zmqSocketAddress,
            messageBuilder,
            incomingMessageDispatcher,
            dispatcherConnector,
            ExecThreadFactory.ExecChannelType.LOG,
            customClassloader,
            peerUuid));
  }
}
