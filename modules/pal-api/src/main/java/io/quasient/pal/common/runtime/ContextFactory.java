/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;

/** Provides per-type and per-member caching of built Context instances. */
public final class ContextFactory {

  /** Avoid direct instantiation */
  private ContextFactory() {}

  /** Member-bound cache: per declaring class, keyed by reflective member */
  private static final ClassValue<ConcurrentHashMap<Object, Context>> PER_DECLARING_CLASS =
      new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Object, Context> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  /** Call-site cache: per withinType (lexical holder of the call site), keyed by StaticPart */
  private static final ClassValue<ConcurrentHashMap<JoinPoint.StaticPart, Context>>
      PER_WITHIN_CLASS =
          new ClassValue<>() {
            @Override
            protected ConcurrentHashMap<JoinPoint.StaticPart, Context> computeValue(
                Class<?> within) {
              return new ConcurrentHashMap<>();
            }
          };

  /**
   * Public caching factory method
   *
   * @param sp the joinpoint static part
   * @return the newly created or cached Context instance filled with the static part info
   */
  public static Context forJoinPoint(JoinPoint.StaticPart sp) {
    final String kind = sp.getKind();
    switch (kind) {
        // Member-bound kinds => safe to cache per reflective member
      case JoinPoint.METHOD_EXECUTION:
      case JoinPoint.CONSTRUCTOR_EXECUTION:
      case JoinPoint.FIELD_GET:
      case JoinPoint.FIELD_SET:
      case JoinPoint.STATICINITIALIZATION:
      case JoinPoint.INITIALIZATION:
      case JoinPoint.PREINITIALIZATION:
        return perMember(sp);

        // Call-site-bound kinds
      case JoinPoint.METHOD_CALL:
      case JoinPoint.CONSTRUCTOR_CALL:
      default:
        return perCallSite(sp);
    }
  }

  /**
   * Computes (if absent) and returns a Context for a member static part
   *
   * @param sp static part
   * @return the per-member Context
   */
  private static Context perMember(JoinPoint.StaticPart sp) {
    Signature sig = sp.getSignature();
    final Class<?> declaring = sig.getDeclaringType();
    final Object key = memberKey(sig);
    // Build once, then reuse.
    return PER_DECLARING_CLASS.get(declaring).computeIfAbsent(key, k -> Context.parseFrom(sp));
  }

  /**
   * Creates a unique key from a member signature
   *
   * @param sig the member Signature
   * @return a unique identifier
   */
  private static Object memberKey(Signature sig) {
    if (sig instanceof MethodSignature ms) {
      Method m = ms.getMethod();
      if (m != null) {
        return m; // ideal: identity is the method object
      }
      // Fallback if weaving stripped reflective Method:
      return ms.toLongString(); // stable, includes name + params + return
    }
    if (sig instanceof ConstructorSignature cs) {
      Constructor<?> c = cs.getConstructor();
      return (c != null) ? c : cs.toLongString();
    }
    if (sig instanceof FieldSignature fs) {
      Field f = fs.getField();
      return (f != null) ? f : fs.toLongString();
    }
    // Extremely rare: unknown signature type
    return sig.toLongString();
  }

  /**
   * Computes (if absent) and returns a Context for a call site's static part
   *
   * @param sp static part
   * @return the call site Context
   */
  private static Context perCallSite(JoinPoint.StaticPart sp) {
    final Class<?> within = sp.getSourceLocation().getWithinType();
    // No global lock; concurrent map per withinType, GC-safe on class unload
    return PER_WITHIN_CLASS.get(within).computeIfAbsent(sp, Context::parseFrom);
  }
}
