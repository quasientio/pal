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
package io.quasient.pal.serdes.colfer.scratches;

import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.Constructor;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.Context;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldGet;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldGet;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;

/**
 * Provides a thread-local {@link TlMsgScratch} for reuse within the current thread.
 *
 * <p>Avoids repeated allocation of scratch buffers by keeping one per thread.
 */
public final class TlScratchHolder {

  /** Thread-local holder of {@link TlMsgScratch} instances. */
  static final ThreadLocal<TlMsgScratch> TL = ThreadLocal.withInitial(TlMsgScratch::new);

  /**
   * Returns the current thread’s {@link TlMsgScratch}.
   *
   * @return the thread-local scratch instance
   */
  static TlMsgScratch s() {
    return TL.get();
  }

  /**
   * Returns a reusable {@link ExecMessage}, reset for fresh use.
   *
   * @return cleared {@code ExecMessage}
   */
  public static ExecMessage exec() {
    final ExecMessage x = s().exec;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link ReturnValue}, reset for fresh use.
   *
   * @return cleared {@code ReturnValue}
   */
  public static ReturnValue rv() {
    final ReturnValue x = s().rv;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link RaisedThrowable}, reset for fresh use.
   *
   * @return cleared {@code RaisedThrowable}
   */
  public static RaisedThrowable rt() {
    final RaisedThrowable x = s().rt;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link InstanceFieldGet}, reset for fresh use.
   *
   * @return cleared {@code InstanceFieldGet}
   */
  public static InstanceFieldGet ifg() {
    final InstanceFieldGet x = s().ifg;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link InstanceFieldPut}, reset for fresh use.
   *
   * @return cleared {@code InstanceFieldPut}
   */
  public static InstanceFieldPut ifp() {
    final InstanceFieldPut x = s().ifp;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link StaticFieldGet}, reset for fresh use.
   *
   * @return cleared {@code StaticFieldGet}
   */
  public static StaticFieldGet sfg() {
    final StaticFieldGet x = s().sfg;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link StaticFieldPut}, reset for fresh use.
   *
   * @return cleared {@code StaticFieldPut}
   */
  public static StaticFieldPut sfp() {
    final StaticFieldPut x = s().sfp;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link InstanceFieldPutDone}, reset for fresh use.
   *
   * @return cleared {@code InstanceFieldPutDone}
   */
  public static InstanceFieldPutDone ifpd() {
    final InstanceFieldPutDone x = s().ifpd;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link StaticFieldPutDone}, reset for fresh use.
   *
   * @return cleared {@code StaticFieldPutDone}
   */
  public static StaticFieldPutDone sfpd() {
    final StaticFieldPutDone x = s().sfpd;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link ConstructorCall}, reset for fresh use.
   *
   * @return cleared {@code ConstructorCall}
   */
  public static ConstructorCall cc() {
    final ConstructorCall x = s().cc;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link InstanceMethodCall}, reset for fresh use.
   *
   * @return cleared {@code InstanceMethodCall}
   */
  public static InstanceMethodCall imc() {
    final InstanceMethodCall x = s().imc;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link ClassMethodCall}, reset for fresh use.
   *
   * @return cleared {@code ClassMethodCall}
   */
  public static ClassMethodCall cmc() {
    final ClassMethodCall x = s().cmc;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable message {@link Context}, reset for fresh use.
   *
   * @return cleared {@code Context}
   */
  public static Context cctx() {
    final var x = s().cctx;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link InterceptCallbackRequestMessage}, reset for fresh use.
   *
   * <p><b>Nested dispatch hazard:</b> The returned object is a thread-local singleton. It must be
   * fully consumed (serialized) before any operation that could trigger a nested dispatch (e.g., an
   * intercept callback that itself invokes an intercepted method). A nested dispatch that calls
   * {@code icbr()} will reset the same instance, corrupting the outer caller's reference. This
   * follows the same pattern as {@link #exec()} and {@link #rv()}.
   *
   * @return cleared {@code InterceptCallbackRequestMessage}
   */
  public static InterceptCallbackRequestMessage icbr() {
    final TlMsgScratch s = TL.get();
    s.icbr.reset();
    return s.icbr;
  }

  /**
   * Returns a reusable {@link Obj} for a value, reset for fresh use.
   *
   * @return cleared return {@code Obj}
   */
  public static Obj valObj() {
    final var x = s().valObj;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link Obj} for a return value, reset for fresh use.
   *
   * @return cleared return {@code Obj}
   */
  public static Obj retObj() {
    final var x = s().retObj;
    x.reset();
    return x;
  }

  /**
   * Ensures the thread-local value pool can hold at least {@code n} items.
   *
   * @param n required capacity
   */
  public static void ensureArgCapacity(int n) {
    s().ensureCapacity(n);
  }

  /**
   * Returns the {@link Obj} at index {@code i}, reset for fresh use.
   *
   * @param i zero-based index
   * @return cleared value {@code Obj} at the given index
   * @throws IndexOutOfBoundsException if {@code i} is out of range
   */
  public static Obj valueAt(int i) {
    final var v = s().values.get(i);
    v.reset();
    return v;
  }

  /**
   * Returns a cached or new exact-length {@link Obj} array.
   *
   * @param n the desired length
   * @return an array of {@code n} elements
   */
  public static Obj[] argsOut(int n) {
    return s().argArrayForLen(n);
  }

  /**
   * Returns a reusable {@link Reflectable}, reset for fresh use.
   *
   * @return cleared {@code Reflectable}
   */
  public static Reflectable refl() {
    final var x = s().refl;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link Constructor}, reset for fresh use.
   *
   * @return cleared {@code Constructor}
   */
  public static Constructor rc() {
    final var x = s().rc;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link Method}, reset for fresh use.
   *
   * @return cleared {@code Method}
   */
  public static Method rm() {
    final var x = s().rm;
    x.reset();
    return x;
  }

  /**
   * Returns a reusable {@link Field}, reset for fresh use.
   *
   * @return cleared {@code Field}
   */
  public static Field rf() {
    final var x = s().rf;
    x.reset();
    return x;
  }
}
