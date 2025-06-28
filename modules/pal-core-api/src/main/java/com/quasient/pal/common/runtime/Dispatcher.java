/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.runtime;

/**
 * Represents a dispatcher responsible for executing operations within a specific runtime context.
 *
 * @see Context
 */
public interface Dispatcher {

  /**
   * Dispatches an operation within the given context.
   *
   * @param ctxt the runtime context in which the dispatch is executed
   * @param sender the object initiating the dispatch
   * @param target the object upon which the dispatch is directed
   * @param args the arguments required for the dispatch operation
   * @return the result of the dispatch operation
   * @throws Throwable if an error occurs during the dispatch process
   */
  Object dispatch(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;
}
