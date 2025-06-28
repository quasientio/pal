/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ObjectRefTest {

  @Test
  public void fromAsString() {
    ObjectRef objectRef = ObjectRef.from("23");
    assertEquals(23, objectRef.getRef());
  }

  @Test
  public void fromAsInt() {
    ObjectRef objectRef = ObjectRef.from(23);
    assertEquals(23, objectRef.getRef());
  }

  @Test
  public void testAsString() {
    ObjectRef objectRef = ObjectRef.from(23);
    assertEquals("23", objectRef.asString());
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(ObjectRef.class).usingGetClass().verify();
  }

  @Test
  public void testToString() {
    int ref = 17;
    ObjectRef objectRef = ObjectRef.from(ref);
    assertThat(objectRef.toString(), is("objectRef: {" + ref + '}'));
  }
}
