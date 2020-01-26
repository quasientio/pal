package net.ittera.pal.core;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ittera.pal.core.exec.DuplicateInterceptException;
import net.ittera.pal.core.exec.java.InterceptRequestEntry;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InterceptRequests {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequests.class);

  private final List<InterceptRequestEntry> constructorIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> methodIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> fieldGetIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> fieldPutIntercepts = new ArrayList<>();

  List<Intercepts.InterceptMessage> getMatchingIntercepts(InterceptKeyMessage execKeyMessage) {
    final List<InterceptRequestEntry> interceptEntriesToSearch;
    switch (execKeyMessage.getMsgType()) {
      case CONSTRUCTOR:
        interceptEntriesToSearch = constructorIntercepts;
        break;
      case INSTANCE_METHOD:
      case CLASS_METHOD:
        interceptEntriesToSearch = methodIntercepts;
        break;
      case GET_STATIC:
      case GET_FIELD:
        interceptEntriesToSearch = fieldGetIntercepts;
        break;
      case PUT_STATIC:
      case PUT_FIELD:
        interceptEntriesToSearch = fieldPutIntercepts;
        break;
      default:
        interceptEntriesToSearch = null;
    }
    if (interceptEntriesToSearch == null) {
      return Collections.emptyList();
    }
    return interceptEntriesToSearch.stream()
        .filter(i -> i.matches(execKeyMessage))
        .map(InterceptRequestEntry::getInterceptMessage)
        .collect(Collectors.toList());
  }

  void registerInterceptRequest(Intercepts.InterceptMessage interceptMessage)
      throws DuplicateInterceptException {
    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);

    if (!interceptMessage.hasField() && !interceptMessage.hasMethod()) {
      throw new IllegalArgumentException(
          format("Unsupported intercept request message:%n%s", interceptMessage));
    }

    final List<InterceptRequestEntry> targetList;

    if (interceptMessage.hasField()) {
      if (interceptMessage.getField().getType().equals(Intercepts.FieldOpType.GET)) {
        targetList = fieldGetIntercepts;
      } else {
        targetList = fieldPutIntercepts;
      }
    } else {
      if (interceptMessage.getMethod().getName().equalsIgnoreCase("new")) {
        targetList = constructorIntercepts;
      } else {
        targetList = methodIntercepts;
      }
    }

    if (targetList.contains(interceptRequestEntry)) {
      throw new DuplicateInterceptException(
          format("InterceptMessage is already registered: %s", interceptMessage));
    } else {
      targetList.add(interceptRequestEntry);
    }
  }

  // TODO optimize: removing shouldn't take O(n)
  void unregisterInterceptRequest(String interceptMessageUUID) {
    final Map<List, Integer> occurrences = new HashMap<>();
    Stream.of(constructorIntercepts, methodIntercepts, fieldGetIntercepts, fieldPutIntercepts)
        .forEach(
            list -> {
              for (int i = 0; i < list.size(); i++) {
                InterceptRequestEntry requestEntry = list.get(i);
                if (interceptMessageUUID.equalsIgnoreCase(
                    requestEntry.getInterceptMessage().getMessageUuid())) {
                  occurrences.put(list, i);
                }
              }
            });
    // delete all list entries of given msg uuid
    occurrences.forEach((list, integer) -> list.remove(integer.intValue()));
  }

  int getRegisteredRequestsSize() {
    return constructorIntercepts.size()
        + methodIntercepts.size()
        + fieldGetIntercepts.size()
        + fieldPutIntercepts.size();
  }
}
