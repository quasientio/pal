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

package net.ittera.pal.core.intercepts;

import static java.lang.String.format;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.core.rpc.exec.java.InterceptRequestEntry;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptableField;
import net.ittera.pal.messages.colfer.InterceptableMethod;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.ExecMessageUtils;
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
public class InterceptRequests {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequests.class);

  private volatile List<InterceptRequestEntry> constructorIntercepts = new ArrayList<>();
  private volatile List<InterceptRequestEntry> methodIntercepts = new ArrayList<>();
  private volatile List<InterceptRequestEntry> fieldGetIntercepts = new ArrayList<>();
  private volatile List<InterceptRequestEntry> fieldPutIntercepts = new ArrayList<>();

  private List<InterceptMessage> getMatchingIntercepts(
      ExecMessage execMessage, List<InterceptRequestEntry> interceptRequestEntries) {
    final String classname = ExecMessageUtils.getClassname(execMessage);
    final String executableName = ExecMessageUtils.getExecutableName(execMessage);
    final List<String> paramTypesList = getParameterTypes(execMessage);
    final String[] parameterTypes =
        paramTypesList == null ? null : paramTypesList.toArray(new String[0]);

    return interceptRequestEntries.stream()
        .filter(i -> i.matches(classname, executableName, parameterTypes))
        .map(InterceptRequestEntry::getInterceptMessage)
        .collect(Collectors.toList());
  }

  public List<InterceptMessage> getMatchingIntercepts(
      ExecMessage execMessage, MessageType messageType) {
    return switch (messageType) {
      case EXEC_CONSTRUCTOR -> getMatchingIntercepts(execMessage, constructorIntercepts);
      case EXEC_INSTANCE_METHOD, EXEC_CLASS_METHOD ->
          getMatchingIntercepts(execMessage, methodIntercepts);
      case EXEC_GET_STATIC, EXEC_GET_FIELD ->
          getMatchingIntercepts(execMessage, fieldGetIntercepts);
      case EXEC_PUT_STATIC, EXEC_PUT_FIELD ->
          getMatchingIntercepts(execMessage, fieldPutIntercepts);
      default -> Collections.emptyList();
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<InterceptRequestEntry> cloneListWithNewRequest(
      List<InterceptRequestEntry> list, InterceptRequestEntry newRequest)
      throws DuplicateInterceptException {

    var newRequestList = (ArrayList<InterceptRequestEntry>) ((ArrayList) list).clone();

    if (newRequestList.contains(newRequest)) {
      throw new DuplicateInterceptException(
          format("InterceptMessage is already registered: %s", newRequest.getInterceptMessage()));
    } else {
      newRequestList.add(newRequest);
    }

    return newRequestList;
  }

  // This method is called by the InterceptMatcher.run() thread only
  @SuppressWarnings("NonAtomicOperationOnVolatileField")
  public void registerInterceptRequest(InterceptMessage interceptMessage)
      throws DuplicateInterceptException {
    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    InterceptableMethod interceptableMethod = interceptMessage.getMethod();
    InterceptableField interceptableField = interceptMessage.getField();

    if (interceptableField == null && interceptableMethod == null) {
      throw new IllegalArgumentException(
          format("Unsupported intercept request message:%n%s", interceptMessage));
    }

    if (interceptableField != null) {
      FieldOpType fieldOpType = FieldOpType.fromByte(interceptableField.getFieldOpType());
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<InterceptRequestEntry> cloneListWithDeletedRequest(
      List<InterceptRequestEntry> list, String interceptMessageId) {

    final List<Integer> occurrences = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      InterceptRequestEntry requestEntry = list.get(i);
      if (interceptMessageId.equalsIgnoreCase(requestEntry.getInterceptMessage().getMessageId())) {
        occurrences.add(i);
      }
    }
    if (occurrences.isEmpty()) {
      return list;
    }

    @SuppressWarnings("unchecked")
    var clonedList = (ArrayList<InterceptRequestEntry>) ((ArrayList) list).clone();
    occurrences.forEach(i -> clonedList.remove(i.intValue()));
    return clonedList;
  }

  // This method is called by the InterceptMatcher.run() thread only
  @SuppressWarnings("NonAtomicOperationOnVolatileField")
  public void unregisterInterceptRequest(String interceptMessageId) {
    constructorIntercepts = cloneListWithDeletedRequest(constructorIntercepts, interceptMessageId);
    methodIntercepts = cloneListWithDeletedRequest(methodIntercepts, interceptMessageId);
    fieldGetIntercepts = cloneListWithDeletedRequest(fieldGetIntercepts, interceptMessageId);
    fieldPutIntercepts = cloneListWithDeletedRequest(fieldPutIntercepts, interceptMessageId);
  }

  int getRegisteredRequestsSize() {
    return constructorIntercepts.size()
        + methodIntercepts.size()
        + fieldGetIntercepts.size()
        + fieldPutIntercepts.size();
  }
}
