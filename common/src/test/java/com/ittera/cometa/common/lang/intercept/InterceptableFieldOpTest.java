package com.ittera.cometa.common.lang.intercept;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.common.lang.FieldOpType;
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
