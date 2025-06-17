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

package com.quasient.pal.common.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;
import org.junit.Test;

public class UuidUtilsTest {

  @Test
  public void toBytesFromBytes() {
    UUID uuid = UUID.randomUUID();
    // 8 + 8 bytes
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid);

    assertThat(uuidAsBytes.length, is(16));
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }

  @Test
  public void toBytes() {
    UUID uuid = UUID.randomUUID();
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid.toString());
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }
}
