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

import java.util.List;

public class GenericMethods {
  public <T extends CharSequence> T echo(T t, List<String> xs) {
    return t;
  }
}
