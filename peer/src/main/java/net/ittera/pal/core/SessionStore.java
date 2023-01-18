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

package net.ittera.pal.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SessionStore {
  private static final Logger logger = LoggerFactory.getLogger(SessionStore.class);

  // one objectRef -> object map for each peer
  private final Map<UUID, ConcurrentHashMap<ObjectRef, Object>> sessionsMap;

  @Inject
  public SessionStore() {
    sessionsMap = new ConcurrentHashMap<>();
  }

  public final void storeInSession(
      @Nonnull UUID peerUuid, @Nonnull ObjectRef objectRef, @Nonnull Object object) {
    ConcurrentHashMap<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      peerSession = new ConcurrentHashMap<>();
      sessionsMap.put(peerUuid, peerSession);
      logger.info("New session created for peer w/uuid: {}", peerUuid);
    }
    peerSession.put(objectRef, object);
  }

  public final void deleteSession(@Nonnull UUID peerUuid) throws NoSuchSessionException {
    ConcurrentHashMap<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found for peer w/uuid: " + peerUuid);
    }
    final long objectsInSession = peerSession.mappingCount();
    peerSession.clear();
    sessionsMap.remove(peerUuid);
    if (logger.isDebugEnabled()) {
      logger.debug("Session deleted ({} objects) for peer w/uuid: {}", objectsInSession, peerUuid);
    }
  }

  public final boolean deleteObject(@Nonnull UUID peerUuid, @Nonnull ObjectRef objectRef)
      throws NoSuchSessionException {
    Map<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found for peer w/uuid: " + peerUuid);
    }
    boolean deleted = peerSession.remove(objectRef) != null;
    if (logger.isDebugEnabled()) {
      if (deleted) {
        logger.debug(
            "Object w/objectRef: {} cleared from session for peer w/uuid: {}", objectRef, peerUuid);
      } else {
        logger.debug(
            "Object w/objectRef: {} was not in session for peer w/uuid: {}", objectRef, peerUuid);
      }
    }
    return deleted;
  }

  public final List<Object> getObjectsInSession(@Nonnull UUID peerUuid)
      throws NoSuchSessionException {
    final ConcurrentHashMap<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found for peer w/uuid: " + peerUuid);
    }
    return new ArrayList<>(peerSession.values());
  }

  public final Set<Entry<ObjectRef, Object>> getEntriesInSession(@Nonnull UUID peerUuid)
      throws NoSuchSessionException {
    final ConcurrentHashMap<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found for peer w/uuid: " + peerUuid);
    }
    return peerSession.entrySet();
  }

  public final long getObjectCountInSession(@Nonnull UUID peerUuid) throws NoSuchSessionException {
    final ConcurrentHashMap<ObjectRef, Object> peerSession = sessionsMap.get(peerUuid);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found for peer w/uuid: " + peerUuid);
    }

    return peerSession.mappingCount();
  }

  public final boolean sessionExistsFor(@Nonnull UUID peerUuid) {
    return sessionsMap.containsKey(peerUuid);
  }
}
