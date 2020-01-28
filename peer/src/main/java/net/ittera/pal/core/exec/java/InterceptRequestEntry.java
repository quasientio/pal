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

package net.ittera.pal.core.exec.java;

import static java.lang.String.format;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.List;
import java.util.Objects;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntry {
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequestEntry.class);
  private final String pattern;
  private final String paramTypes;
  private final int numberOfParams;
  private final boolean isMethod;
  private final Intercepts.InterceptMessage interceptMessage;

  // Not safe-thread, which is fine since we only deal with InterceptRequestEntry objects from a
  // single thread
  private static final AntPathMatcherArrays matcher =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  public InterceptRequestEntry(Intercepts.InterceptMessage interceptMessage) {
    this.isMethod = interceptMessage.hasMethod();
    // create executable pattern to match
    this.pattern =
        format(
            "%s.%s",
            interceptMessage.getClazz(),
            isMethod
                ? interceptMessage.getMethod().getName()
                : interceptMessage.getField().getName());
    // add param info
    if (isMethod) {
      this.numberOfParams = interceptMessage.getMethod().getParameterTypeCount();
      if (numberOfParams > 0) {
        this.paramTypes = String.join(",", interceptMessage.getMethod().getParameterTypeList());
      } else {
        this.paramTypes = "";
      }
    } else {
      numberOfParams = 0;
      paramTypes = null;
    }
    this.interceptMessage = interceptMessage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptRequestEntry that = (InterceptRequestEntry) o;
    return numberOfParams == that.numberOfParams
        && isMethod == that.isMethod
        && pattern.equals(that.pattern)
        && paramTypes.equals(that.paramTypes)
        && interceptMessage.equals(that.interceptMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pattern, paramTypes, numberOfParams, isMethod, interceptMessage);
  }

  public boolean matches(InterceptKeyMessage keyMessage) {

    // use matcher on pattern
    final String executablePath =
        format("%s.%s", keyMessage.getClazz(), keyMessage.getExecutableName());
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Matching entry pattern '{}' against execMessage pattern '{}'", pattern, executablePath);
    }
    if (!matcher.isMatch(pattern, executablePath)) {
      return false;
    }

    // match parameter types
    // TODO it won't be this simple; we'll need to take extends and impl into account
    final List<String> execMessageParamTypes = keyMessage.getParameterTypeList();
    if (paramTypes == null) {
      return execMessageParamTypes == null;
    } else {
      return Objects.equals(paramTypes, String.join(",", execMessageParamTypes));
    }
  }

  public Intercepts.InterceptMessage getInterceptMessage() {
    return interceptMessage;
  }
}
