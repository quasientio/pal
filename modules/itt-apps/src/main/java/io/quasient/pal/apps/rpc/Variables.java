/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.rpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressWarnings("unused")
@SuppressFBWarnings(
    value = {
      "MS_SHOULD_BE_FINAL",
      "SS_SHOULD_BE_STATIC",
      "URF_UNREAD_FIELD",
      "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
      "UUF_UNUSED_FIELD",
      "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"
    },
    justification =
        "Test fixture - fields intentionally unused and mutable, accessed via RPC for testing")
public class Variables {

  // instance variables
  public Integer anInt = 4;
  Integer anotherInt = 1;
  private Integer myNullInt;
  protected String someString = "I'm not blank";
  public String myNullStr;
  Boolean myNullBool;
  public boolean myBool = true;
  private final short someShort = 233;

  // class variables
  public static String aClassString = "I'm classy";
  public static String aNullStaticStr;
  public static int aStaticPublicInteger = 94734;
  private static Integer aPrivateClassInt = 39328;
  protected static Boolean aProtectedBool;
  static boolean aPackageVisibleBool = true;
  static int aStaticInteger = 3000;
}
