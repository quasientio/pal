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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import net.ittera.pal.common.lang.FieldOpType;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class InterceptableFieldOpTest {

  private FieldOpType fieldOpType;
  private String name;
  private InterceptableFieldOp interceptableFieldOp;

  @Before
  public void setUp() {
    fieldOpType = FieldOpType.GET;
    name = "myField";
    interceptableFieldOp = new InterceptableFieldOp(name, fieldOpType);
  }

  @Test
  public void getFieldOpType() {
    assertThat(interceptableFieldOp.getFieldOpType(), is(fieldOpType));
  }

  @Test
  public void getName() {
    assertThat(interceptableFieldOp.getName(), is(name));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(InterceptableFieldOp.class).usingGetClass().verify();
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
