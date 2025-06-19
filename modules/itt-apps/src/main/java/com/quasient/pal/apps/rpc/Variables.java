/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.apps.rpc;

@SuppressWarnings("unused")
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
  private static Integer aPrivateClassInt = 39328;
  protected static Boolean aProtectedBool;
  static boolean aPackageVisibleBool = true;
  static int aStaticInteger = 3000;
}
