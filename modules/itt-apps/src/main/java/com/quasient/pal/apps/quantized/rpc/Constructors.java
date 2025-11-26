/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.quantized.rpc;

@SuppressWarnings("unused")
public class Constructors {

  private Constructors innerInstance;

  public Constructors() {}

  public Constructors(Integer anInt) {}

  public Constructors(String someString) {
    Integer integer = Integer.parseInt(someString);
  }

  Constructors(String msg, Integer times) {

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) {
      sb.append(msg).append(",");
    }
  }

  private Constructors(String[] myStringArrayParam) {
    StringBuilder sb = new StringBuilder();
    for (String anotherStringArrayParam : myStringArrayParam) {
      sb.append(anotherStringArrayParam).append(",");
    }
  }

  protected Constructors(Constructors myConstructor) {
    this.innerInstance = myConstructor;
  }
}
