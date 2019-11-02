package com.ittera.cometa.core;

import static java.lang.String.format;

import com.ittera.cometa.common.lang.annotation.Before;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterceptProcessor {

  private static final Logger logger = LoggerFactory.getLogger(InterceptProcessor.class);
  private final UUID peerUuid;
  private final MessageBuilder messageBuilder;
  private final DispatcherConnector connector;

  @Inject
  InterceptProcessor(UUID peerUuid, MessageBuilder messageBuilder, DispatcherConnector connector) {
    this.peerUuid = peerUuid;
    this.messageBuilder = messageBuilder;
    this.connector = connector;
  }

  public void process(Class clazz) {
    if (logger.isDebugEnabled()) {
      logger.debug("inspecting class '{}' for annotations", clazz.getName());
    }
    List<Intercepts.InterceptMessage> interceptMessages = new ArrayList<>();

    // collect annotations and batch messages
    for (Method method : clazz.getDeclaredMethods()) {
      // process @Before annotation
      Annotation annotation = method.getDeclaredAnnotation(Before.class);
      if (annotation != null) {
        Class<? extends Annotation> type = annotation.annotationType();
        String className, methodName, fieldName, fieldOpType;
        String[] parameterTypes;
        try {
          className = (String) type.getDeclaredMethod("clazz").invoke(annotation, (Object[]) null);
          methodName =
              (String) type.getDeclaredMethod("method").invoke(annotation, (Object[]) null);
          // parameter types are extracted from the callback signature
          parameterTypes =
              Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new);
          fieldName = (String) type.getDeclaredMethod("field").invoke(annotation, (Object[]) null);
          fieldOpType =
              (String) type.getDeclaredMethod("fieldOpType").invoke(annotation, (Object[]) null);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "className: {}, methodName: {}, parameterTypes: {}, fieldName: {}, fieldOpType: {}",
                className,
                methodName,
                Arrays.toString(parameterTypes),
                fieldName,
                fieldOpType);
          }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
          logger.error(
              format(
                  "Error processing annotation '%s' found in method '%s' of class '%s",
                  annotation, method.getName(), clazz.getName()),
              e);
          continue;
        }

        // build and queue request message
        interceptMessages.add(
            messageBuilder.buildInterceptMessage(
                peerUuid,
                InterceptType.BEFORE,
                className,
                methodName,
                Arrays.asList(parameterTypes),
                clazz.getName(),
                method.getName()));
      }
    }

    // TODO process @After annotation

    // send all messages at once
    interceptMessages.forEach(connector::sendOutInterceptRequest);
  }
}
