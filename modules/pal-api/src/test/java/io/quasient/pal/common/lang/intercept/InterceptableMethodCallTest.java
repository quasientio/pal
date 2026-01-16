/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class InterceptableMethodCallTest {

  private String name;
  private List<String> parameterTypes;
  private InterceptableMethodCall interceptableMethodCall;

  @Before
  public void setUp() {
    name = "myMethod";
    parameterTypes = Arrays.asList("String.class", "long.class");
    interceptableMethodCall = new InterceptableMethodCall(name, parameterTypes);
  }

  @Test
  public void getName() {
    assertThat(interceptableMethodCall.getName(), is(name));
  }

  @Test
  public void getParameterTypes() {
    assertThat(interceptableMethodCall.getParameterTypes(), is(parameterTypes));
  }

  @Test
  public void getParameterTypes_null() {
    assertThat(new InterceptableMethodCall(name, null).getParameterTypes(), is(empty()));
  }

  @Test
  public void equalsContract() {
    InterceptableMethodCall a = new InterceptableMethodCall("myMethod", parameterTypes);
    InterceptableMethodCall b = new InterceptableMethodCall("myMethod", parameterTypes);
    InterceptableMethodCall c = new InterceptableMethodCall("myMethod", parameterTypes);
    InterceptableMethodCall different = new InterceptableMethodCall("otherMethod", parameterTypes);

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
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
