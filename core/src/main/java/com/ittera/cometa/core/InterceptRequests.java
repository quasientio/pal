package com.ittera.cometa.core;

import static java.lang.String.format;

import com.ittera.cometa.core.exec.DuplicateInterceptException;
import com.ittera.cometa.core.exec.java.InterceptRequestEntry;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Intercepts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InterceptRequests {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequests.class);

  private final List<InterceptRequestEntry> constructorIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> methodIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> fieldGetIntercepts = new ArrayList<>();
  private final List<InterceptRequestEntry> fieldPutIntercepts = new ArrayList<>();

  List<Intercepts.InterceptMessage> getMatchingIntercepts(ExecMessage execMessage) {
    final List<InterceptRequestEntry> interceptEntriesToSearch;
    switch (execMessage.getMsgType()) {
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
        .filter(i -> i.matches(execMessage))
        .map(InterceptRequestEntry::getInterceptMessage)
        .collect(Collectors.toList());
  }

  void registerInterceptRequest(Intercepts.InterceptMessage interceptMessage)
      throws DuplicateInterceptException, IllegalArgumentException {
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
}
