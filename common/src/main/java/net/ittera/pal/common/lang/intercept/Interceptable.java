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

package net.ittera.pal.common.lang.intercept;

public abstract class Interceptable {

  public enum InterceptableType {
    METHOD_CALL,
    FIELD_OP;

    public static final InterceptableType[] values = InterceptableType.values();
  }

  protected final String name;
  protected final InterceptableType type;

  protected Interceptable(String name, InterceptableType type) {
    this.name = name;
    this.type = type;
  }

  public abstract String toSerializedString();

  public String getName() {
    return name;
  }

  public InterceptableType getType() {
    return type;
  }
}
