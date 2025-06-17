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
