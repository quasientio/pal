/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class ObjectNotFoundExceptionTest {

  @Test
  public void constructor() {
    String message = "the exception msg";
    Exception onfe = new ObjectNotFoundException(message);
    assertThat(onfe.getMessage(), is(message));
  }
}
