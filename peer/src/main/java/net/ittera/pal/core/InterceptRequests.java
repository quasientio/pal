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

import static java.lang.String.format;
import static net.ittera.pal.serdes.colfer.MessageUtils.getParameterTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.core.exec.DuplicateInterceptException;
import net.ittera.pal.core.exec.java.InterceptRequestEntry;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptableField;
import net.ittera.pal.messages.colfer.InterceptableMethod;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safety is guaranteed as long as writing operations (i.e. register and unregister methods)
 * are called exclusively by the InterceptMatcher main and only thread. Reads are thread-safe.
 *
 * <p>We avoid all locking by creating new modified lists. Since we don't anticipate a huge number
 * of intercept events (i.e. register & unregister messages), the cost of creating new lists for
 * each modification should still be lower than matching every message over a zmq channel, or using
 * a locking collection like ConcurrentHashMap.
 */
class InterceptRequests {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequests.class);

  private volatile ArrayList<InterceptRequestEntry> constructorIntercepts = new ArrayList<>();
  private volatile ArrayList<InterceptRequestEntry> methodIntercepts = new ArrayList<>();
  private volatile ArrayList<InterceptRequestEntry> fieldGetIntercepts = new ArrayList<>();
  private volatile ArrayList<InterceptRequestEntry> fieldPutIntercepts = new ArrayList<>();

  private List<InterceptMessage> getMatchingIntercepts(
      ExecMessage execMessage, List<InterceptRequestEntry> interceptRequestEntries) {
    final String classname = MessageUtils.getClassname(execMessage);
    final String executableName = MessageUtils.getExecutableName(execMessage);
    final List<String> paramTypesList = getParameterTypes(execMessage);
    final String[] parameterTypes =
        paramTypesList == null ? null : paramTypesList.toArray(new String[0]);

    return interceptRequestEntries.stream()
        .filter(i -> i.matches(classname, executableName, parameterTypes))
        .map(InterceptRequestEntry::getInterceptMessage)
        .collect(Collectors.toList());
  }

  List<InterceptMessage> getMatchingIntercepts(ExecMessage execMessage) {
    final ExecMessageType execMessageType =
        ExecMessageType.values()[execMessage.getExecMessageType()];
    switch (execMessageType) {
      case CONSTRUCTOR:
        return getMatchingIntercepts(execMessage, constructorIntercepts);
      case INSTANCE_METHOD:
      case CLASS_METHOD:
        return getMatchingIntercepts(execMessage, methodIntercepts);
      case GET_STATIC:
      case GET_FIELD:
        return getMatchingIntercepts(execMessage, fieldGetIntercepts);
      case PUT_STATIC:
      case PUT_FIELD:
        return getMatchingIntercepts(execMessage, fieldPutIntercepts);
      default:
        return Collections.emptyList();
    }
  }

  private ArrayList<InterceptRequestEntry> cloneListWithNewRequest(
      ArrayList<InterceptRequestEntry> list, InterceptRequestEntry newRequest)
      throws DuplicateInterceptException {
    ArrayList<InterceptRequestEntry> newRequestList =
        (ArrayList<InterceptRequestEntry>) list.clone();

    if (newRequestList.contains(newRequest)) {
      throw new DuplicateInterceptException(
          format("InterceptMessage is already registered: %s", newRequest.getInterceptMessage()));
    } else {
      newRequestList.add(newRequest);
    }

    return newRequestList;
  }

  void registerInterceptRequest(InterceptMessage interceptMessage)
      throws DuplicateInterceptException {
    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    InterceptableMethod interceptableMethod = interceptMessage.getMethod();
    InterceptableField interceptableField = interceptMessage.getField();

    if (interceptableField == null && interceptableMethod == null) {
      throw new IllegalArgumentException(
          format("Unsupported intercept request message:%n%s", interceptMessage));
    }

    if (interceptableField != null) {
      FieldOpType fieldOpType = FieldOpType.values()[interceptableField.getFieldOpType()];
      if (fieldOpType.equals(FieldOpType.GET)) {
        fieldGetIntercepts = cloneListWithNewRequest(fieldGetIntercepts, interceptRequestEntry);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Added new intercept request: {} to fieldGet intercept list", interceptRequestEntry);
        }
      } else {
        fieldPutIntercepts = cloneListWithNewRequest(fieldPutIntercepts, interceptRequestEntry);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Added new intercept request: {} to fieldPut intercept list", interceptRequestEntry);
        }
      }
    } else {
      if (interceptableMethod.getName().equalsIgnoreCase("new")) {
        constructorIntercepts =
            cloneListWithNewRequest(constructorIntercepts, interceptRequestEntry);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Added new intercept request: {} to constructor intercept list",
              interceptRequestEntry);
        }
      } else {
        methodIntercepts = cloneListWithNewRequest(methodIntercepts, interceptRequestEntry);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Added new intercept request: {} to method intercept list", interceptRequestEntry);
        }
      }
    }
  }

  private ArrayList<InterceptRequestEntry> cloneListWithDeletedRequest(
      ArrayList<InterceptRequestEntry> list, String interceptMessageUUID) {

    final List<Integer> occurrences = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      InterceptRequestEntry requestEntry = list.get(i);
      if (interceptMessageUUID.equalsIgnoreCase(
          requestEntry.getInterceptMessage().getMessageUuid())) {
        occurrences.add(i);
      }
    }
    if (occurrences.isEmpty()) {
      return list;
    }

    ArrayList<InterceptRequestEntry> clonedList = (ArrayList<InterceptRequestEntry>) list.clone();
    occurrences.forEach(i -> clonedList.remove(i.intValue()));
    return clonedList;
  }

  void unregisterInterceptRequest(String interceptMessageUUID) {
    constructorIntercepts =
        cloneListWithDeletedRequest(constructorIntercepts, interceptMessageUUID);
    methodIntercepts = cloneListWithDeletedRequest(methodIntercepts, interceptMessageUUID);
    fieldGetIntercepts = cloneListWithDeletedRequest(fieldGetIntercepts, interceptMessageUUID);
    fieldPutIntercepts = cloneListWithDeletedRequest(fieldPutIntercepts, interceptMessageUUID);
  }

  int getRegisteredRequestsSize() {
    return constructorIntercepts.size()
        + methodIntercepts.size()
        + fieldGetIntercepts.size()
        + fieldPutIntercepts.size();
  }
}
