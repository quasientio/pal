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
