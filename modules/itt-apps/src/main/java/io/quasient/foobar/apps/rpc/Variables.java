/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.foobar.apps.rpc;

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
