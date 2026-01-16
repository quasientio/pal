/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import io.quasient.pal.common.lang.reflect.ConstructorSignature;
import io.quasient.pal.common.lang.reflect.MethodSignature;
import io.quasient.pal.common.runtime.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

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
  private static final ClassValue<ConcurrentHashMap<Object, MessageStatics>> PER_CLASS =
      new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Object, MessageStatics> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
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
  public static MessageStatics forMethod(Context ctx) {
    var ms = (MethodSignature) ctx.getSignature();

    Object key = ms.getMethod();
    var map = PER_CLASS.get(ms.getDeclaringType());
    return map.computeIfAbsent(
        key,
        k -> {
          var clazzMsg = Wrapper.getWrappedClass(ms.getDeclaringTypeName());
          String[] pNames = ms.getParameterNames(); // nullable OK
          Class<?>[] pTypes = ms.getParameterTypes();
          String[] pTypeNames =
              (pTypes == null)
                  ? null
                  : Arrays.stream(pTypes).map(Class::getName).toArray(String[]::new);
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
  public static MessageStatics forConstructor(Context ctx) {
    var cs = (ConstructorSignature) ctx.getSignature();
    Object key = (Constructor<?>) cs.getConstructor();
    var map = PER_CLASS.get(cs.getDeclaringType());
    return map.computeIfAbsent(
        key,
        k -> {
          var clazzMsg = Wrapper.getWrappedClass(cs.getDeclaringTypeName());
          String[] pNames = cs.getParameterNames();
          Class<?>[] pTypes = cs.getParameterTypes();
          String[] pTypeNames =
              (pTypes == null)
                  ? null
                  : Arrays.stream(pTypes).map(Class::getName).toArray(String[]::new);
          return new MessageStatics(
              clazzMsg, /*name not used for ctor*/ "<init>", cs.getModifiers(), pNames, pTypeNames);
        });
  }
}
