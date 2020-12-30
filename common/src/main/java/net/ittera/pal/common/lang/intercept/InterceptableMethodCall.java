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

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InterceptableMethodCall extends Interceptable {

  private final List<String> parameterTypes;
  private static final String FIELD_SEP = "&&";

  public InterceptableMethodCall(String name, List<String> parameterTypes) {
    super(name, InterceptableType.METHOD_CALL);
    if (parameterTypes == null) {
      this.parameterTypes = Collections.emptyList();
    } else {
      this.parameterTypes = parameterTypes;
    }
  }

  public List<String> getParameterTypes() {
    return parameterTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    InterceptableMethodCall that = (InterceptableMethodCall) o;
    return Objects.equals(parameterTypes, that.parameterTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType(), parameterTypes);
  }

  @Override
  public String toString() {
    return "InterceptableMethodCall{"
        + "parameterTypes="
        + parameterTypes
        + ", name='"
        + getName()
        + '\''
        + '}';
  }

  @Override
  public String toSerializedString() {
    return format("%s" + FIELD_SEP + "%s", getName(), String.join(FIELD_SEP, parameterTypes));
  }

  public static InterceptableMethodCall fromSerializedString(String serialized) {
    final String[] parts = serialized.split(FIELD_SEP);
    final String name = parts[0];
    final List<String> paramTypes = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length));
    return new InterceptableMethodCall(name, paramTypes);
  }
}
