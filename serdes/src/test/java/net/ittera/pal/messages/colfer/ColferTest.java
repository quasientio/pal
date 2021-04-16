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

package net.ittera.pal.messages.colfer;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class ColferTest {

  @Test
  public void serdes() {
    Class clazz = new Class();
    clazz.setName("YoyoClass");
    clazz.setUnknown(true);

    int maxSize = clazz.marshalFit();
    byte[] serialized = new byte[maxSize];
    int finalIdx = clazz.marshal(serialized, 0);
    if (finalIdx < maxSize) {
      byte[] trimmed = new byte[finalIdx];
      System.arraycopy(serialized, 0, trimmed, 0, finalIdx);
      serialized = trimmed;
    }

    Class declazz = new Class();
    declazz.unmarshal(serialized, 0);

    assertThat(declazz.getName(), is("YoyoClass"));
    assertThat(declazz.getUnknown(), is(true));
    assertThat(declazz, is(clazz));
  }
}
