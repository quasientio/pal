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

package net.ittera.pal.common.objects;

import java.util.Objects;

public final class ObjectRef {

  private final int ref;

  private ObjectRef(String ref) {
    this.ref = Integer.parseInt(ref);
  }

  public int getRef() {
    return ref;
  }

  public static ObjectRef from(String ref) {
    return new ObjectRef(ref);
  }

  public static ObjectRef randomRef() {
    return new ObjectRef(String.valueOf((int) (Math.random() * 100000)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ObjectRef objectRef = (ObjectRef) o;
    return ref == objectRef.ref;
  }

  public String asString() {
    return String.valueOf(ref);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ref);
  }

  @Override
  public String toString() {
    return "objectRef: {" + ref + '}';
  }
}
