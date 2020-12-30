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

import java.lang.ref.WeakReference;
import java.util.Objects;

// Wrapper class, used both for storing objects in the map, and looking them up.
class IdentifiableObject {

  private final WeakReference<Object> object;
  private final int hash;

  IdentifiableObject(Object object) {
    this.object = new WeakReference<>(Objects.requireNonNull(object));
    this.hash = System.identityHashCode(object);
  }

  public WeakReference<Object> getObject() {
    return object;
  }

  public int getHash() {
    return hash;
  }

  public final int hashCode() {
    return hash;
  }

  /**
   * IdentifiableObject's equality is based on the identityHashCode of the encapsulated object.
   *
   * @param other
   * @return
   */
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof IdentifiableObject)) {
      return false;
    }
    return other.hashCode() == this.hashCode();
  }

  @Override
  public String toString() {
    return "IdentifiableObject{" + "object=" + object.get() + ", hash=" + hash + '}';
  }
}
