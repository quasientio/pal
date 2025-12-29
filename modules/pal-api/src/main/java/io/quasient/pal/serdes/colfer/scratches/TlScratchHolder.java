/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
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
   * Returns a reusable {@link Obj} for sender data, reset for fresh use.
   *
   * @return cleared sender {@code Obj}
   */
  public static Obj senderObj() {
    final var x = s().senderObj;
    x.reset();
    return x;
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
   * Ensures the thread-local parameter/value pools can hold at least {@code n} items.
   *
   * @param n required capacity
   */
  public static void ensureParamCapacity(int n) {
    s().ensureCapacity(n);
  }

  /**
   * Returns the {@link Parameter} at index {@code i}, reset for fresh use.
   *
   * @param i zero-based index
   * @return cleared {@code Parameter} at the given index
   * @throws IndexOutOfBoundsException if {@code i} is out of range
   */
  public static Parameter paramAt(int i) {
    final Parameter p = s().params.get(i);
    p.reset();
    return p;
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
   * Get an exact-length array of {@link Parameter} elements
   *
   * @param n the desired length
   * @return a new/cached array of {@code n} elements
   */
  public static Parameter[] paramsOut(int n) {
    return s().arrayForLen(n);
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
