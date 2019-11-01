package com.ittera.cometa.core;

import com.ittera.cometa.core.exec.java.InterceptRequestEntry;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
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

  List<Intercepts.InterceptRequest> getMatchingIntercepts(Wrappers.ExecMessage execMessage) {
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
        .map(InterceptRequestEntry::getInterceptRequestMessage)
        .collect(Collectors.toList());
  }

  boolean registerInterceptRequest(Intercepts.InterceptRequest interceptRequest) {
    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptRequest);

    if (interceptRequest.hasField()) {
      if (interceptRequest.getField().getType().equals(Intercepts.FieldOpType.GET)) {
        fieldGetIntercepts.add(interceptRequestEntry);
      } else {
        fieldPutIntercepts.add(interceptRequestEntry);
      }
    } else if (interceptRequest.hasMethod()) {
      if (interceptRequest.getMethod().getName().equalsIgnoreCase("new")) {
        constructorIntercepts.add(interceptRequestEntry);
      } else {
        methodIntercepts.add(interceptRequestEntry);
      }
    } else {
      logger.warn("Discarding unsupported intercept request: {}", interceptRequest);
      return false;
    }
    return true;
  }
}
