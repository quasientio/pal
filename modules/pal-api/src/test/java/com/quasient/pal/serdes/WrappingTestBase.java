/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

@SuppressWarnings("JdkObsolete") // silence errorprone warnings about StringBuffer usage
public abstract class WrappingTestBase {

  /** Comprehensive list of all java.lang(8) classes */
  protected static final List<Class<?>> nonWrapperJavaLangClasses =
      Arrays.asList(
          Character.Subset.class,
          Character.UnicodeBlock.class,
          Class.class,
          ClassLoader.class,
          ClassValue.class,
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

  // List of objects that should be wrappable
  @SuppressWarnings("UnnecessaryStringBuilder")
  protected static final List<Object> wrappableObjects =
      Arrays.asList(
          // null and void
          null,
          // primitives
          false,
          Byte.parseByte("0"),
          'c',
          0.43d,
          512.5f,
          Integer.parseInt("4"),
          34L,
          Short.parseShort("10"),
          // char sequences
          "hello",
          // primitive wrappers
          Boolean.TRUE,
          Byte.valueOf("1"),
          'a',
          Double.valueOf("382.03"),
          Float.valueOf("393.4"),
          Integer.valueOf("458"),
          Long.valueOf("348333"),
          Short.valueOf("25"),
          // arrays of primitives
          new boolean[] {true, false},
          new byte[] {1, 2, 3},
          new char[] {'a', 'b', 'c'},
          new double[] {1.0d, 2.0d, 3.0d},
          new float[] {1.0f, 2.0f, 3.0f},
          new int[] {1, 2, 3},
          new long[] {1L, 2L, 3L},
          new short[] {1, 2, 3},
          // arrays of wrappers
          new Boolean[] {true, false},
          new Byte[] {1, 2, 3},
          new Character[] {'a', 'b', 'c'},
          new Double[] {1.0d, 2.0d, 3.0d},
          new Float[] {1.0f, 2.0f, 3.0f},
          new Integer[] {1, 2, 3},
          new Long[] {1L, 2L, 3L},
          new Short[] {1, 2, 3},
          // arrays of char sequences
          new String[] {"hey", "there"},
          new StringBuilder[] {new StringBuilder("hey"), new StringBuilder("there")},
          new StringBuffer[] {new StringBuffer("hey"), new StringBuffer("ya!")});

  // List of some objects that should NOT be wrappable
  // we need to test this class - silence errorprone warnings
  @SuppressWarnings({"JavaUtilDate"})
  protected static final List<Object> someNonWrappableObjects =
      Arrays.asList(Object.class, new Object(), new Date(), new Random());
}
