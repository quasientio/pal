/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Factory for creating and caching {@link MessageStatics} instances.
 *
 * <p>Uses a per-declaring-class cache to avoid recomputing metadata. Thread-safe.
 */
public final class MessageStaticsFactory {

  /** Not instantiable. */
  private MessageStaticsFactory() {}

  /**
   * Per declaring class, a cache of {@link MessageStatics} keyed by the reflective member ({@link
   * Method}, {@link Constructor}, {@link Field}) or the signature's string form when the reflective
   * member is unavailable.
   */
  private static final ClassValue<java.util.concurrent.ConcurrentHashMap<Object, MessageStatics>>
      PER_CLASS =
          new ClassValue<>() {
            @Override
            protected java.util.concurrent.ConcurrentHashMap<Object, MessageStatics> computeValue(
                Class<?> type) {
              return new java.util.concurrent.ConcurrentHashMap<>();
            }
          };

  /**
   * Returns cached or newly created {@link MessageStatics} for a method signature.
   *
   * @param ctx runtime context providing a {@code MethodSignature}
   * @return the corresponding {@code MessageStatics}
   * @implNote Uses the {@link Method} as the cache key if present, otherwise the signature's {@code
   *     toString()}.
   */
  public static MessageStatics forMethod(com.quasient.pal.common.runtime.Context ctx) {
    var ms = (com.quasient.pal.common.lang.reflect.MethodSignature) ctx.getSignature();
    Method m = ms.getMethod();

    Object key = (m != null ? m : ms.toString());
    var map = PER_CLASS.get(ms.getDeclaringType());
    return map.computeIfAbsent(
        key,
        k -> {
          var clazzMsg =
              com.quasient.pal.serdes.colfer.Wrapper.getWrappedClass(ms.getDeclaringTypeName());
          String[] pNames = ms.getParameterNames(); // nullable OK
          Class[] pTypes = ms.getParameterTypes();
          String[] pTypeNames =
              (pTypes == null)
                  ? null
                  : java.util.Arrays.stream(pTypes).map(Class::getName).toArray(String[]::new);
          return new MessageStatics(clazzMsg, ms.getName(), ms.getModifiers(), pNames, pTypeNames);
        });
  }

  /**
   * Returns cached or newly created {@link MessageStatics} for a constructor signature.
   *
   * @param ctx runtime context providing a {@code ConstructorSignature}
   * @return the corresponding {@code MessageStatics}
   * @implNote Uses the {@link Constructor} as the cache key if present, otherwise the signature's
   *     {@code toString()}. The stored name is {@code "<init>"}.
   */
  public static MessageStatics forConstructor(com.quasient.pal.common.runtime.Context ctx) {
    var cs = (com.quasient.pal.common.lang.reflect.ConstructorSignature) ctx.getSignature();
    Constructor<?> c = cs.getConstructor();
    Object key = (c != null ? c : cs.toString());
    var map = PER_CLASS.get(cs.getDeclaringType());
    return map.computeIfAbsent(
        key,
        k -> {
          var clazzMsg =
              com.quasient.pal.serdes.colfer.Wrapper.getWrappedClass(cs.getDeclaringTypeName());
          String[] pNames = cs.getParameterNames();
          Class[] pTypes = cs.getParameterTypes();
          String[] pTypeNames =
              (pTypes == null)
                  ? null
                  : java.util.Arrays.stream(pTypes).map(Class::getName).toArray(String[]::new);
          return new MessageStatics(
              clazzMsg, /*name not used for ctor*/ "<init>", cs.getModifiers(), pNames, pTypeNames);
        });
  }

  /**
   * Returns cached or newly created {@link MessageStatics} for a field signature.
   *
   * @param ctx runtime context providing a {@code FieldSignature}
   * @return the corresponding {@code MessageStatics}
   * @implNote Uses the {@link Field} as the cache key if present, otherwise the signature's {@code
   *     toString()}.
   */
  public static MessageStatics forField(com.quasient.pal.common.runtime.Context ctx) {
    var fs = (com.quasient.pal.common.lang.reflect.FieldSignature) ctx.getSignature();
    Field f = fs.getField();
    Object key = (f != null ? f : fs.toString());
    var map = PER_CLASS.get(fs.getDeclaringType());
    return map.computeIfAbsent(
        key,
        k -> {
          var clazzMsg =
              com.quasient.pal.serdes.colfer.Wrapper.getWrappedClass(fs.getDeclaringTypeName());
          return new MessageStatics(clazzMsg, fs.getName(), fs.getModifiers(), null, null);
        });
  }
}
