package com.ittera.cometa.core.exec.java;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntry {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequestEntry.class);
  private final String pattern;
  private final String paramTypes;
  private final int numberOfParams;
  private final boolean isMethod;
  private final Intercepts.InterceptRequest interceptRequestMessage;

  // Not safe-thread, which is fine since we only deal with InterceptRequestEntry objects from a
  // single thread
  private static final AntPathMatcherArrays matcher =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  public InterceptRequestEntry(Intercepts.InterceptRequest interceptRequestMessage) {
    this.isMethod = interceptRequestMessage.hasMethod();
    // create executable pattern to match
    this.pattern =
        format(
            "%s.%s",
            interceptRequestMessage.getClazz(),
            isMethod
                ? interceptRequestMessage.getMethod().getName()
                : interceptRequestMessage.getField().getName());
    // add param info
    if (isMethod) {
      this.numberOfParams = interceptRequestMessage.getMethod().getParameterTypeCount();
      if (numberOfParams > 0) {
        this.paramTypes =
            String.join(",", interceptRequestMessage.getMethod().getParameterTypeList());
      } else {
        this.paramTypes = "";
      }
    } else {
      numberOfParams = 0;
      paramTypes = null;
    }
    this.interceptRequestMessage = interceptRequestMessage;
  }

  private static String[] toNames(Class[] types) {
    return Arrays.stream(types)
        .map(p -> p.getName())
        .collect(toList())
        .toArray(new String[types.length]);
  }

  private static String getClassname(ExecMessage execMessage) {
    switch (execMessage.getMsgType()) {
      case CONSTRUCTOR:
        return execMessage.getConstructorCall().getClass_().getName();
      case INSTANCE_METHOD:
        return execMessage.getInstanceMethodCall().getClass_().getName();
      case CLASS_METHOD:
        return execMessage.getClassMethodCall().getClass_().getName();
      case GET_STATIC:
        return execMessage.getStaticFieldGet().getClass_().getName();
      case GET_FIELD:
        return execMessage.getInstanceFieldGet().getClass_().getName();
      case PUT_STATIC:
        return execMessage.getStaticFieldPut().getClass_().getName();
      case PUT_FIELD:
        return execMessage.getInstanceFieldPut().getClass_().getName();
      default:
        throw new IllegalArgumentException(
            format("Unsupported ExecMessage type: %s", execMessage.getMsgType()));
    }
  }

  private static String getExecutableName(ExecMessage execMessage) {
    switch (execMessage.getMsgType()) {
      case CONSTRUCTOR:
        return "new";
      case INSTANCE_METHOD:
        return execMessage.getInstanceMethodCall().getName();
      case CLASS_METHOD:
        return execMessage.getClassMethodCall().getName();
      case GET_STATIC:
        return execMessage.getStaticFieldGet().getField().getName();
      case GET_FIELD:
        return execMessage.getInstanceFieldGet().getField().getName();
      case PUT_STATIC:
        return execMessage.getStaticFieldPut().getField().getName();
      case PUT_FIELD:
        return execMessage.getInstanceFieldPut().getField().getName();
      default:
        throw new IllegalArgumentException(
            format("Unsupported ExecMessage type: %s", execMessage.getMsgType()));
    }
  }

  /**
   * @param execMessage
   * @return null if not a constructor/method call, list of parameter class names otherwise
   */
  private static List<String> getParameterTypes(ExecMessage execMessage) {
    switch (execMessage.getMsgType()) {
      case CONSTRUCTOR:
        if (execMessage.getConstructorCall().getParameterCount() > 0) {
          return execMessage.getConstructorCall().getParameterList().stream()
              .map(p -> p.getType().getName())
              .collect(Collectors.toList());
        } else {
          return Collections.emptyList();
        }
      case INSTANCE_METHOD:
        if (execMessage.getInstanceMethodCall().getParameterCount() > 0) {
          return execMessage.getInstanceMethodCall().getParameterList().stream()
              .map(p -> p.getType().getName())
              .collect(Collectors.toList());
        } else {
          return Collections.emptyList();
        }
      case CLASS_METHOD:
        if (execMessage.getClassMethodCall().getParameterCount() > 0) {
          return execMessage.getClassMethodCall().getParameterList().stream()
              .map(p -> p.getType().getName())
              .collect(Collectors.toList());
        } else {
          return Collections.emptyList();
        }
      default:
        return null;
    }
  }

  public boolean matches(ExecMessage execMessage) {

    // use matcher on pattern
    final String executablePath =
        format("%s.%s", getClassname(execMessage), getExecutableName(execMessage));
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Matching entry pattern '{}' against execMessage pattern '{}'", pattern, executablePath);
    }
    if (!matcher.isMatch(pattern, executablePath)) {
      return false;
    }

    // match parameter types
    final List<String> execMessageParamTypes = getParameterTypes(execMessage);
    if (paramTypes == null) {
      return execMessageParamTypes == null;
    } else {
      return Objects.equals(paramTypes, String.join(",", execMessageParamTypes));
    }
  }

  public Intercepts.InterceptRequest getInterceptRequestMessage() {
    return interceptRequestMessage;
  }
}
