/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;

import com.quasient.pal.common.lang.FieldOpType;
import org.junit.Before;
import org.junit.Test;

public class InterceptableFieldOpTest {

  private FieldOpType fieldOpType;
  private String name;
  private InterceptableFieldOp interceptableFieldOp;

  @Before
  public void setUp() {
    fieldOpType = FieldOpType.GET;
    name = "myField";
    interceptableFieldOp = new InterceptableFieldOp(name, fieldOpType);
  }

  @Test
  public void getFieldOpType() {
    assertThat(interceptableFieldOp.getFieldOpType(), is(fieldOpType));
  }

  @Test
  public void getName() {
    assertThat(interceptableFieldOp.getName(), is(name));
  }

  @Test
  public void equalsContract() {
    InterceptableFieldOp a = new InterceptableFieldOp("myField", FieldOpType.GET);
    InterceptableFieldOp b = new InterceptableFieldOp("myField", FieldOpType.GET);
    InterceptableFieldOp c = new InterceptableFieldOp("myField", FieldOpType.GET);
    InterceptableFieldOp different = new InterceptableFieldOp("otherField", FieldOpType.GET);

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }

  @Test
  public void toAndfromSerializedString() {
    final String fieldName = "out";
    final FieldOpType fieldOpType = FieldOpType.GET;
    InterceptableFieldOp interceptable = new InterceptableFieldOp(fieldName, fieldOpType);

    final String serialized = interceptable.toSerializedString();

    InterceptableFieldOp rebuiltInterceptable =
        InterceptableFieldOp.fromSerializedString(serialized);
    assertThat(rebuiltInterceptable, is(interceptable));
  }
}
