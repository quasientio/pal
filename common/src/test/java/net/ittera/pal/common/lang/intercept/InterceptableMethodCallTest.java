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
