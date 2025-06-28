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
public abstract class AbstractMethodDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_noArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_noArgs_ok() throws Exception;

  public abstract void dispatch_withArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withArgs_ok() throws Exception;

  public abstract void dispatch_withPrimitiveArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withPrimitiveArgs_ok() throws Exception;

  public abstract void dispatchIncoming_withObjectRefArgs_ok() throws Exception;

  public abstract void dispatchIncoming_withNullArgs_ok() throws Exception;

  public abstract void dispatch_varargs_ok() throws Throwable;

  public abstract void dispatchIncoming_varargs_ok() throws Exception;

  public abstract void dispatch_throwsException_exceptionThrown() throws Throwable;

  public abstract void dispatchIncoming_throwsException_exceptionThrown() throws Exception;

  public abstract void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown()
      throws Exception;
}
