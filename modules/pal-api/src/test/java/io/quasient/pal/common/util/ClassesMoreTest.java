/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClassesMoreTest {

  @Test
  public void simpleToLongName_mapsCommonTypes() {
    assertEquals("java.lang.String", Classes.simpleToLongName("String"));
    assertEquals("java.lang.Integer", Classes.simpleToLongName("Integer"));
    assertEquals("java.lang.Boolean", Classes.simpleToLongName("Boolean"));
  }

  @Test
  public void isValidNonArrayClassName_variants() {
    assertTrue(Classes.isValidNonArrayClassName("java.lang.String"));
    assertTrue(Classes.isValidNonArrayClassName("com.acme._X$1"));
    assertThat(Classes.isValidNonArrayClassName(null), is(false));
    assertThat(Classes.isValidNonArrayClassName(""), is(false));
    assertThat(Classes.isValidNonArrayClassName("1bad"), is(false));
  }

  @Test
  public void mapTypeStringToComponentClass_coversAllMappings_andNull() {
    assertEquals(int.class, Classes.mapTypeStringToComponentClass("[I"));
    assertEquals(boolean.class, Classes.mapTypeStringToComponentClass("[Z"));
    assertEquals(byte.class, Classes.mapTypeStringToComponentClass("[B"));
    assertEquals(short.class, Classes.mapTypeStringToComponentClass("[S"));
    assertEquals(char.class, Classes.mapTypeStringToComponentClass("[C"));
    assertEquals(double.class, Classes.mapTypeStringToComponentClass("[D"));
    assertEquals(float.class, Classes.mapTypeStringToComponentClass("[F"));
    assertEquals(long.class, Classes.mapTypeStringToComponentClass("[J"));

    assertEquals(String.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.String;"));
    assertEquals(Integer.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Integer;"));
    assertEquals(Boolean.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Boolean;"));
    assertEquals(Long.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Long;"));
    assertEquals(Double.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Double;"));
    assertEquals(Float.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Float;"));
    assertEquals(Short.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Short;"));
    assertEquals(Character.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Character;"));
    assertEquals(Byte.class, Classes.mapTypeStringToComponentClass("[Ljava.lang.Byte;"));

    // Aliases with [] notation
    assertEquals(String.class, Classes.mapTypeStringToComponentClass("String[]"));
    assertEquals(Integer.class, Classes.mapTypeStringToComponentClass("Integer[]"));
    assertNull(Classes.mapTypeStringToComponentClass("Unknown[]"));
  }
}
