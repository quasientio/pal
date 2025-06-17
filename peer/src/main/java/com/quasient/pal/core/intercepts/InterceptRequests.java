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

package com.quasient.pal.core.intercepts;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static java.lang.String.format;

import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.core.rpc.exec.java.InterceptRequestEntry;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.InterceptableField;
import com.quasient.pal.messages.colfer.InterceptableMethod;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.ExecMessageUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages registration, matching, and unregistration of intercept requests for execution events,
 * including constructor invocations, method calls, and field accesses. Instances of this class
 * maintain separate lists for each intercept type, and updates are performed by cloning the
 * underlying lists to avoid synchronization overhead. Thread-safety for read operations is ensured;
 * however, registration and unregistration must be performed exclusively from the designated thread
 * (typically the InterceptMatcher thread) to guarantee safe modifications.
 */
public class InterceptRequests {
  /** Logger instance used to log debug messages for intercept request operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequests.class);

  /** Holds intercept request entries corresponding to constructor execution events. */
  private volatile List<InterceptRequestEntry> constructorIntercepts = new ArrayList<>();

  /** Holds intercept request entries corresponding to instance or class method execution events. */
  private volatile List<InterceptRequestEntry> methodIntercepts = new ArrayList<>();

  /** Holds intercept request entries corresponding to field get operations. */
  private volatile List<InterceptRequestEntry> fieldGetIntercepts = new ArrayList<>();

  /** Holds intercept request entries corresponding to field put operations. */
  private volatile List<InterceptRequestEntry> fieldPutIntercepts = new ArrayList<>();

  /**
   * Filters the provided list of intercept request entries to find those matching the criteria
   * extracted from the given execution message.
   *
   * @param execMessage the execution message containing context such as classname, executable name,
   *     and parameter types
   * @param interceptRequestEntries the list of intercept request entries to be filtered
   * @return a list of intercept messages corresponding to entries that match the execution message
   *     criteria; never null but may be empty if no matches are found
   */
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

  /**
   * Retrieves a list of intercept messages that match the provided execution message and message
   * type. Depending on the message type, the search is performed within the corresponding intercept
   * request list.
   *
   * @param execMessage the execution message providing context for matching intercept requests
   * @param messageType the type of execution event (e.g., constructor, method, or field access)
   *     used to select the appropriate list
   * @return a list of matching intercept messages; if no entries match, an empty list is returned
   */
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

  /**
   * Creates a new list by cloning the provided {@code list} and adding the new intercept request.
   * If an identical request is already present in the list, a {@code DuplicateInterceptException}
   * is thrown.
   *
   * @param list the original list of intercept request entries
   * @param newRequest the new intercept request entry to add
   * @return a new list that contains all entries from the original list plus the new request
   * @throws DuplicateInterceptException if the new request is already registered in the list
   */
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

  /**
   * Registers the specified intercept message by adding it to the appropriate intercept list based
   * on its type. The intercept message must contain either method or field information. This method
   * should be called exclusively from the InterceptMatcher thread.
   *
   * @param interceptMessage the intercept message to register; it must specify either an
   *     interceptable method or field
   * @throws DuplicateInterceptException if an identical intercept request has already been
   *     registered
   * @throws IllegalArgumentException if the intercept message lacks both method and field
   *     information
   */
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

  /**
   * Creates a new list by cloning the provided {@code list} and removing any intercept request
   * entries whose message identifier matches the specified {@code interceptMessageId} (comparison
   * is case-insensitive).
   *
   * @param list the original list of intercept request entries
   * @param interceptMessageId the identifier of the intercept request to remove
   * @return a new list which excludes any requests with the given message identifier; if none
   *     match, the original list is returned unmodified
   */
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

  /**
   * Unregisters intercept requests by removing all entries with the specified message identifier
   * from every intercept list (constructor, method, field get, and field put). This method is
   * intended to be called exclusively from the InterceptMatcher thread.
   *
   * @param interceptMessageId the identifier of the intercept message to unregister
   */
  @SuppressWarnings("NonAtomicOperationOnVolatileField")
  public void unregisterInterceptRequest(String interceptMessageId) {
    constructorIntercepts = cloneListWithDeletedRequest(constructorIntercepts, interceptMessageId);
    methodIntercepts = cloneListWithDeletedRequest(methodIntercepts, interceptMessageId);
    fieldGetIntercepts = cloneListWithDeletedRequest(fieldGetIntercepts, interceptMessageId);
    fieldPutIntercepts = cloneListWithDeletedRequest(fieldPutIntercepts, interceptMessageId);
  }

  /**
   * Returns the total number of registered intercept requests across all categories.
   *
   * @return the aggregate count of intercept request entries registered in this instance
   */
  int getRegisteredRequestsSize() {
    return constructorIntercepts.size()
        + methodIntercepts.size()
        + fieldGetIntercepts.size()
        + fieldPutIntercepts.size();
  }
}
