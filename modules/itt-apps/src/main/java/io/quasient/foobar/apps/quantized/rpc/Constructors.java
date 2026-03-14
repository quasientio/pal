/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.quantized.rpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressWarnings("unused")
@SuppressFBWarnings(
    value = {"CT_CONSTRUCTOR_THROW", "URF_UNREAD_FIELD"},
    justification = "Test app - constructor exception and unused field part of test scenarios")
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
