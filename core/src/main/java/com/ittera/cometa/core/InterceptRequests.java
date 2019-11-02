package com.ittera.cometa.core;

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

  boolean registerInterceptRequest(Intercepts.InterceptMessage interceptMessage) {
    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);

    if (interceptMessage.hasField()) {
      if (interceptMessage.getField().getType().equals(Intercepts.FieldOpType.GET)) {
        fieldGetIntercepts.add(interceptRequestEntry);
      } else {
        fieldPutIntercepts.add(interceptRequestEntry);
      }
    } else if (interceptMessage.hasMethod()) {
      if (interceptMessage.getMethod().getName().equalsIgnoreCase("new")) {
        constructorIntercepts.add(interceptRequestEntry);
      } else {
        methodIntercepts.add(interceptRequestEntry);
      }
    } else {
      logger.warn("Discarding unsupported intercept request: {}", interceptMessage);
      return false;
    }
    return true;
  }
}
