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

package net.ittera.pal.core.rpc.exec.java;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.core.rpc.DispatcherConnector;
import net.ittera.pal.core.rpc.exec.java.reflect.ReflectionHelper;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractDispatcher {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  protected UUID peerUuid;
  protected MessageBuilder messageBuilder;
  protected ObjectLookupStore objectLookupStore;
  protected ReflectionHelper reflectionHelper;
  protected DispatcherConnector connector;
  protected boolean allowNonPublicAccess;

  @Inject
  final void setPeerUuid(UUID peerUuid) {
    this.peerUuid = peerUuid;
  }

  @Inject
  final void setMessageBuilder(MessageBuilder messageBuilder) {
    this.messageBuilder = messageBuilder;
  }

  @Inject
  final void setObjectLookupStore(ObjectLookupStore objectLookupStore) {
    this.objectLookupStore = objectLookupStore;
  }

  @Inject
  final void setConnector(DispatcherConnector connector) {
    this.connector = connector;
  }

  @Inject
  final void setReflectionHelper(ReflectionHelper reflectionHelper) {
    this.reflectionHelper = reflectionHelper;
  }

  @Inject
  final void setAllowNonPublicAccess(@Named("rpc.allow_nonpublic") String allowNonPublicAccess) {
    this.allowNonPublicAccess = Boolean.parseBoolean(allowNonPublicAccess);
  }
}
