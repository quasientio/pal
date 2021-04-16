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

package net.ittera.pal.serdes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class WrappingTestBase {
  protected static final List<Class> primitiveClasses =
      Arrays.asList(
          boolean.class,
          byte.class,
          char.class,
          double.class,
          float.class,
          int.class,
          long.class,
          short.class);

  protected static final List<Class> primitiveWrapperClasses =
      Arrays.asList(
          Boolean.class,
          Byte.class,
          Character.class,
          Double.class,
          Float.class,
          Integer.class,
          Long.class,
          Short.class);

  /** Comprehensive list of all java.lang(8) classes */
  protected static final List<Class> nonWrapperJavaLangClasses =
      Arrays.asList(
          Character.Subset.class,
          Character.UnicodeBlock.class,
          Class.class,
          ClassLoader.class,
          ClassValue.class,
          Compiler.class,
          Enum.class,
          InheritableThreadLocal.class,
          Math.class,
          Number.class,
          Object.class,
          Package.class,
          Process.class,
          ProcessBuilder.class,
          ProcessBuilder.Redirect.class,
          Runtime.class,
          RuntimePermission.class,
          SecurityManager.class,
          StackTraceElement.class,
          StrictMath.class,
          StringBuffer.class,
          StringBuilder.class,
          System.class,
          Thread.class,
          ThreadGroup.class,
          ThreadLocal.class,
          Throwable.class,
          Void.class);

  /** List of objects that should be wrappable */
  protected static final List<Object> wrappableObjects =
      Arrays.asList(
          /** null and void * */
          null,
          Void.class,
          void.class,
          /** primitives * */
          false,
          Byte.parseByte("0"),
          'c',
          0.43d,
          512.5f,
          Integer.parseInt("4"),
          34L,
          Short.parseShort("10"),
          /** char sequences * */
          String.valueOf("hello"),
          new StringBuilder("world"),
          new StringBuffer("!!"),
          /** primitive wrappers * */
          Boolean.TRUE,
          Byte.valueOf("1"),
          Character.valueOf('a'),
          Double.valueOf("382.03"),
          Float.valueOf("393.4"),
          Integer.valueOf("458"),
          Long.valueOf("348333"),
          Short.valueOf("25"));

  /** List of some objects that should NOT be wrappable */
  protected static final List<Object> someNonWrappableObjects =
      Arrays.asList(
          Object.class,
          new Object(),
          new java.util.Date(),
          new ArrayList(),
          new java.util.HashSet(),
          new java.util.HashMap(),
          new java.util.Stack(),
          new java.util.Random(),
          new Object[1],
          new Class[1]);
}
