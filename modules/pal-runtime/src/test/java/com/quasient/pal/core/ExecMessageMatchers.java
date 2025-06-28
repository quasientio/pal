/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import com.quasient.pal.messages.colfer.InstanceFieldPutDone;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.colfer.StaticFieldPutDone;
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
      if (returnValue instanceof ReturnValue) {
        if (((ReturnValue) returnValue).getFrom().getField() != null) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getField()
              .getClazz()
              .getName()
              .equals(clazz.getName());
        } else if (((ReturnValue) returnValue).getFrom().getConstructor() != null) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getConstructor()
              .getClazz()
              .getName()
              .equals(clazz.getName());
        } else if (((ReturnValue) returnValue).getFrom().getMethod() != null) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getMethod()
              .getClazz()
              .getName()
              .equals(clazz.getName());
        } else {
          return false;
        }
      } else if (returnValue instanceof StaticFieldPutDone) {
        return ((StaticFieldPutDone) returnValue)
            .getField()
            .getClazz()
            .getName()
            .equals(clazz.getName());
      } else if (returnValue instanceof InstanceFieldPutDone) {
        return ((InstanceFieldPutDone) returnValue)
            .getField()
            .getClazz()
            .getName()
            .equals(clazz.getName());
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
      if (returnValue instanceof ReturnValue) {
        if (((ReturnValue) returnValue).getFrom().getField() != null) {
          return ((ReturnValue) returnValue).getFrom().getField().getName().equals(reflectableName);
        } else if (((ReturnValue) returnValue).getFrom().getConstructor() != null) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getConstructor()
              .getClazz()
              .getName()
              .equals(reflectableName);
        } else if (((ReturnValue) returnValue).getFrom().getMethod() != null) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getMethod()
              .getName()
              .equals(reflectableName);
        } else {
          return false;
        }
      } else if (returnValue instanceof StaticFieldPutDone) {
        return ((StaticFieldPutDone) returnValue).getField().getName().equals(reflectableName);
      } else if (returnValue instanceof InstanceFieldPutDone) {
        return ((InstanceFieldPutDone) returnValue).getField().getName().equals(reflectableName);
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
