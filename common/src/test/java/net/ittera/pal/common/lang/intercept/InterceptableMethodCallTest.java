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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class InterceptableMethodCallTest {

  @Test
  public void testEquals() {
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall(
            "println", Arrays.asList("java.lang.String", "java.lang.Integer"));
    InterceptableMethodCall interceptable2 =
        new InterceptableMethodCall(
            "println", Arrays.asList("java.lang.String", "java.lang.Integer"));

    // equals
    assertThat(interceptable, is(not(sameInstance(interceptable2))));
    assertThat(interceptable, is(interceptable2));

    // different method name
    assertThat(
        interceptable,
        is(
            not(
                new InterceptableMethodCall(
                    "printf", Arrays.asList("java.lang.String", "java.lang.Integer")))));

    // different parameter types
    assertThat(
        interceptable, is(not(new InterceptableMethodCall("println", Collections.emptyList()))));
    assertThat(
        interceptable,
        is(
            not(
                new InterceptableMethodCall(
                    "println", Collections.singletonList("java.lang.String")))));
    assertThat(
        interceptable,
        is(
            not(
                new InterceptableMethodCall(
                    "println", Arrays.asList("java.lang.String", "java.lang.String")))));
    assertThat(
        interceptable,
        is(
            not(
                new InterceptableMethodCall(
                    "println",
                    Arrays.asList("java.lang.String", "java.lang.Integer", "java.lang.String")))));
  }

  @Test
  public void toAndfromSerializedString() {
    List<InterceptableMethodCall> interceptables = new ArrayList<>();
    interceptables.add(new InterceptableMethodCall("println", Collections.emptyList()));
    interceptables.add(
        new InterceptableMethodCall("println", Collections.singletonList("java.lang.String")));
    interceptables.add(
        new InterceptableMethodCall(
            "println", Arrays.asList("java.lang.String", "java.lang.Boolean")));
    interceptables.forEach(
        interceptableMethodCall -> {
          final String serialized = interceptableMethodCall.toSerializedString();
          final InterceptableMethodCall rebuiltInterceptable =
              InterceptableMethodCall.fromSerializedString(serialized);
          assertThat(rebuiltInterceptable, is(interceptableMethodCall));
        });
  }
}
