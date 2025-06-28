/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

@SuppressWarnings("unused")
public abstract class AbstractFieldOpDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_primitive_ok() throws Throwable;

  public abstract void dispatchIncoming_primitive_ok() throws Exception;

  public abstract void dispatch_primitiveArray_ok() throws Throwable;

  public abstract void dispatchIncoming_primitiveArray_ok() throws Exception;

  public abstract void dispatch_wrapper_ok() throws Throwable;

  public abstract void dispatchIncoming_wrapper_ok() throws Exception;

  public abstract void dispatch_string_ok() throws Throwable;

  public abstract void dispatchIncoming_string_ok() throws Exception;

  public abstract void dispatch_object_ok() throws Throwable;

  public abstract void dispatchIncoming_object_ok() throws Exception;

  public abstract void dispatch_nullObject_ok() throws Throwable;

  public abstract void dispatchIncoming_nullObject_ok() throws Exception;

  public abstract void dispatch_objectArray_ok() throws Throwable;

  public abstract void dispatchIncoming_objectArray_ok() throws Exception;

  public abstract void dispatch_throwable_ok() throws Throwable;

  public abstract void dispatchIncoming_throwable_ok() throws Exception;
}
