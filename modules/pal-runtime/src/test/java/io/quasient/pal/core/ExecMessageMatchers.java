/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core;

import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ExecMessageMatchers {

  public static final class ComesFromClass extends TypeSafeMatcher<Object> {
    private final Class<?> clazz;

    ComesFromClass(Class<?> clazz) {
      this.clazz = clazz;
    }

    @Override
    protected boolean matchesSafely(Object returnValue) {
      if (returnValue instanceof ReturnValue retValue) {
        if (retValue.getFrom().getField() != null) {
          return retValue.getFrom().getField().getClazz().getName().equals(clazz.getName());
        } else if (retValue.getFrom().getConstructor() != null) {
          return retValue.getFrom().getConstructor().getClazz().getName().equals(clazz.getName());
        } else if (retValue.getFrom().getMethod() != null) {
          return retValue.getFrom().getMethod().getClazz().getName().equals(clazz.getName());
        } else {
          return false;
        }
      } else if (returnValue instanceof StaticFieldPutDone stPutDone) {
        return stPutDone.getField().getClazz().getName().equals(clazz.getName());
      } else if (returnValue instanceof InstanceFieldPutDone instPutDone) {
        return instPutDone.getField().getClazz().getName().equals(clazz.getName());
      } else {
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("comes from class: " + clazz.getName());
    }

    public static Matcher<Object> comesFromClass(Class<?> clazz) {
      return new ComesFromClass(clazz);
    }
  }

  public static final class ComesFromReflectable extends TypeSafeMatcher<Object> {
    private final String reflectableName;

    ComesFromReflectable(String reflectableName) {
      this.reflectableName = reflectableName;
    }

    @Override
    protected boolean matchesSafely(Object returnValue) {
      if (returnValue instanceof ReturnValue retValue) {
        if (retValue.getFrom().getField() != null) {
          return retValue.getFrom().getField().getName().equals(reflectableName);
        } else if (retValue.getFrom().getConstructor() != null) {
          return retValue.getFrom().getConstructor().getClazz().getName().equals(reflectableName);
        } else if (retValue.getFrom().getMethod() != null) {
          return retValue.getFrom().getMethod().getName().equals(reflectableName);
        } else {
          return false;
        }
      } else if (returnValue instanceof StaticFieldPutDone stPutDone) {
        return stPutDone.getField().getName().equals(reflectableName);
      } else if (returnValue instanceof InstanceFieldPutDone instPutDone) {
        return instPutDone.getField().getName().equals(reflectableName);
      } else {
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("comes from reflectable: " + reflectableName);
    }

    public static Matcher<Object> comesFrom(String fieldName) {
      return new ComesFromReflectable(fieldName);
    }
  }
}
