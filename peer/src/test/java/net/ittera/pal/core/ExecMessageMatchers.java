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

package net.ittera.pal.core;

import net.ittera.pal.messages.protobuf.Fields;
import net.ittera.pal.messages.protobuf.Values.ReturnValue;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ExecMessageMatchers {

  /** Matches if ReturnValue is of DeclaringClass */
  public static final class HasDeclaringClassOf extends TypeSafeMatcher<Object> {

    private Class declaringClass;

    HasDeclaringClassOf(Class declaringClass) {
      this.declaringClass = declaringClass;
    }

    @Override
    protected boolean matchesSafely(Object returnValue) {
      if (returnValue instanceof ReturnValue) {
        return ((ReturnValue) returnValue)
            .getClazz()
            .getName()
            .equalsIgnoreCase(declaringClass.getName());
      } else if (returnValue instanceof Fields.StaticFieldPutDone) {
        return ((Fields.StaticFieldPutDone) returnValue)
            .getClass_()
            .getName()
            .equalsIgnoreCase(declaringClass.getName());
      } else if (returnValue instanceof Fields.InstanceFieldPutDone) {
        return ((Fields.InstanceFieldPutDone) returnValue)
            .getClass_()
            .getName()
            .equalsIgnoreCase(declaringClass.getName());
      } else {
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("has declaring class " + declaringClass.getName());
    }

    public static Matcher<Object> hasDeclaringClass(Class declaringClass) {
      return new HasDeclaringClassOf(declaringClass);
    }
  }

  public static final class ComesFromClass extends TypeSafeMatcher<Object> {
    private Class clazz;

    ComesFromClass(Class clazz) {
      this.clazz = clazz;
    }

    @Override
    protected boolean matchesSafely(Object returnValue) {
      if (returnValue instanceof ReturnValue) {
        if (((ReturnValue) returnValue).getFrom().hasField()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getField()
              .getRepr()
              .contains(clazz.getName());
        } else if (((ReturnValue) returnValue).getFrom().hasConstructor()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getConstructor()
              .getRepr()
              .contains(clazz.getName());
        } else if (((ReturnValue) returnValue).getFrom().hasMethod()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getMethod()
              .getRepr()
              .contains(clazz.getName());
        } else {
          return false;
        }
      } else if (returnValue instanceof Fields.StaticFieldPutDone) {
        return ((Fields.StaticFieldPutDone) returnValue)
            .getField()
            .getRepr()
            .contains(clazz.getName());
      } else if (returnValue instanceof Fields.InstanceFieldPutDone) {
        return ((Fields.InstanceFieldPutDone) returnValue)
            .getField()
            .getRepr()
            .contains(clazz.getName());
      } else {
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("comes from class: " + clazz.getName());
    }

    public static Matcher<Object> comesFromClass(Class clazz) {
      return new ComesFromClass(clazz);
    }
  }

  public static final class ComesFromReflectable extends TypeSafeMatcher<Object> {
    private String reflectableName;

    ComesFromReflectable(String reflectableName) {
      this.reflectableName = reflectableName;
    }

    @Override
    protected boolean matchesSafely(Object returnValue) {
      if (returnValue instanceof ReturnValue) {
        if (((ReturnValue) returnValue).getFrom().hasField()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getField()
              .getRepr()
              .contains(reflectableName);
        } else if (((ReturnValue) returnValue).getFrom().hasConstructor()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getConstructor()
              .getRepr()
              .contains(reflectableName);
        } else if (((ReturnValue) returnValue).getFrom().hasMethod()) {
          return ((ReturnValue) returnValue)
              .getFrom()
              .getMethod()
              .getRepr()
              .contains(reflectableName);
        } else {
          return false;
        }
      } else if (returnValue instanceof Fields.StaticFieldPutDone) {
        return ((Fields.StaticFieldPutDone) returnValue)
            .getField()
            .getRepr()
            .contains(reflectableName);
      } else if (returnValue instanceof Fields.InstanceFieldPutDone) {
        return ((Fields.InstanceFieldPutDone) returnValue)
            .getField()
            .getRepr()
            .contains(reflectableName);
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
