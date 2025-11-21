/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.weave;

/** Functional interface which provides a callback to an advice's void proceed() */
@FunctionalInterface
public interface VoidProceed extends Proceed<Void> {

  /** the functional-interface method for lambdas */
  void callVoid() throws Throwable;

  /** default implementation that satisfies Proceed<Void> */
  @Override
  default Void call() throws Throwable {
    callVoid();
    return null;
  }
}
