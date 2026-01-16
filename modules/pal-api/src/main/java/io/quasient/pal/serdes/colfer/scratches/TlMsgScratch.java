/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
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
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Thread-local scratch space for constructing and reusing Colfer message objects.
 *
 * <p>Holds reusable instances of common message types and parameter/value buffers to minimize
 * allocations during message serialization.
 */
public final class TlMsgScratch {

  /** Reusable {@code ExecMessage} buffer. */
  final ExecMessage exec = new ExecMessage();

  /** Reusable {@code InstanceMethodCall} buffer. */
  final InstanceMethodCall imc = new InstanceMethodCall();

  /** Reusable {@code ClassMethodCall} buffer. */
  final ClassMethodCall cmc = new ClassMethodCall();

  /** Reusable {@code ConstructorCall} buffer. */
  final ConstructorCall cc = new ConstructorCall();

  /** Reusable {@code InstanceFieldGet} buffer. */
  final InstanceFieldGet ifg = new InstanceFieldGet();

  /** Reusable {@code InstanceFieldPut} buffer. */
  final InstanceFieldPut ifp = new InstanceFieldPut();

  /** Reusable {@code InstanceFieldPutDone} buffer. */
  final InstanceFieldPutDone ifpd = new InstanceFieldPutDone();

  /** Reusable {@code StaticFieldPutDone} buffer. */
  final StaticFieldPutDone sfpd = new StaticFieldPutDone();

  /** Reusable {@code StaticFieldGet} buffer. */
  final StaticFieldGet sfg = new StaticFieldGet();

  /** Reusable {@code StaticFieldPut} buffer. */
  final StaticFieldPut sfp = new StaticFieldPut();

  /** Reusable {@code ReturnValue} buffer. */
  final ReturnValue rv = new ReturnValue();

  /** Reusable {@code RaisedThrowable} buffer. */
  final RaisedThrowable rt = new RaisedThrowable();

  /** Reusable message {@code Context} buffer. */
  final Context cctx = new Context();

  /** Pool of reusable {@code Parameter} instances. */
  final ArrayList<Parameter> params = new ArrayList<>(8);

  /** Pool of reusable {@link Obj} value holders. */
  final ArrayList<Obj> values = new ArrayList<>(8);

  /** Cache of exact-length Parameter[] arrays per arity */
  final HashMap<Integer, Parameter[]> paramArraysByLen = new HashMap<>();

  // “from” payload pieces for ReturnValue/RaisedThrowable (reused)

  /** Reusable {@link Reflectable} buffer */
  final Reflectable refl = new Reflectable();

  /** Reusable {@link Constructor} for reflectable buffer */
  final Constructor rc = new Constructor();

  /** Reusable {@link Method} for reflectable buffer */
  final Method rm = new Method();

  /** Reusable {@link Field} for reflectable buffer */
  final Field rf = new Field();

  /** Reusable {@link Obj} for sender in context data. */
  final Obj senderObj = new Obj();

  /** Reusable {@link Obj} for return value. */
  final Obj retObj = new Obj();

  /** Reusable {@link Obj} for value. */
  final Obj valObj = new Obj();

  /**
   * Ensures capacity for at least {@code n} parameters/values, growing pools as needed.
   *
   * @param n required capacity
   */
  void ensureCapacity(int n) {
    // grow pools of reusable Parameter and Obj elements
    while (params.size() < n) params.add(new Parameter());
    while (values.size() < n) values.add(new Obj());
  }

  /**
   * Get an {@link Parameter} array of the given length
   *
   * @param n the required length
   * @return a new or cached {@link Parameter} array of {@code n} elements
   */
  Parameter[] arrayForLen(int n) {
    return paramArraysByLen.computeIfAbsent(n, Parameter[]::new);
  }
}
