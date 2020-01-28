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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import net.ittera.pal.common.lang.FieldOpType;
import org.junit.Test;

public class InterceptableFieldOpTest {

  @Test
  public void equals() {
    InterceptableFieldOp interceptable = new InterceptableFieldOp("out", FieldOpType.GET);
    InterceptableFieldOp interceptable2 = new InterceptableFieldOp("out", FieldOpType.GET);

    // equals
    assertThat(interceptable, is(not(sameInstance(interceptable2))));
    assertThat(interceptable, is(interceptable2));

    // different field name
    assertThat(interceptable, is(not(new InterceptableFieldOp("in", FieldOpType.GET))));

    // different fieldop type
    assertThat(interceptable, is(not(new InterceptableFieldOp("out", FieldOpType.PUT))));
  }

  @Test
  public void toAndfromSerializedString() {
    final String fieldName = "out";
    final FieldOpType fieldOpType = FieldOpType.GET;
    InterceptableFieldOp interceptable = new InterceptableFieldOp(fieldName, fieldOpType);

    final String serialized = interceptable.toSerializedString();

    InterceptableFieldOp rebuiltInterceptable =
        InterceptableFieldOp.fromSerializedString(serialized);
    assertThat(rebuiltInterceptable, is(interceptable));
  }
}
