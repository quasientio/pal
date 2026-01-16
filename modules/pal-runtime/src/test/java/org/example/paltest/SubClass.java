/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package org.example.paltest;

public class SubClass extends BaseClass implements BaseIface {
  @SuppressWarnings("HidingField")
  public int a; // shadow field

  @Override
  public int getA() {
    return 2;
  } // override method

  @Override
  public void iMeth() {
    /* no-op */
  }
}
