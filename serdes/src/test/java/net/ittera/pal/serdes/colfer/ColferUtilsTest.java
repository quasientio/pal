package net.ittera.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.Marshallable;
import net.ittera.pal.messages.colfer.InstanceFieldGet;
import org.junit.Test;

public class ColferUtilsTest {

  static class ClassForColferUtilsTest {
    public int field;
  }

  private Marshallable createMarshallable() throws NoSuchFieldException {
    // create signature
    FieldSignature signature =
        new FieldSignature(ClassForColferUtilsTest.class.getDeclaredField("field"));
    String sourceFile = "ColferUtilsTest.java";
    int lineNumber = 17;
    Class<?> withinType = ClassForColferUtilsTest.class;
    // create context
    Context context = new Context(sourceFile, lineNumber, withinType, signature);
    net.ittera.pal.messages.colfer.Class clazz =
        Wrapper.getWrappedClass(signature.getDeclaringType());

    // create marshallable message
    net.ittera.pal.messages.colfer.Field field =
        Wrapper.getWrappedField(signature.getFieldType(), signature.getName());
    int modifiers = signature.getModifiers();
    net.ittera.pal.messages.colfer.Context ctxt =
        Wrapper.getWrappedContext(context, this, ObjectRef.randomRef());
    return new InstanceFieldGet()
        .withClazz(clazz)
        .withField(field)
        .withModifiers(modifiers)
        .withContext(ctxt);
  }

  @Test
  public void toBytes_InstanceFieldGet_byteArray() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    byte[] result = ColferUtils.toBytes(message);
    assertNotNull(result);
    assertThat(result.length, greaterThan(0));
  }

  @Test
  public void toBytes_NullInstanceFieldGet_null() {
    assertNull(ColferUtils.toBytes(null));
  }

  @Test
  public void toJson_Marshallable_NonPrettyJsonString() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    String json = ColferUtils.toJson(message);
    assertNotNull(json);
    assertFalse(json.contains("\n"));
    assertFalse(json.contains(" "));
  }

  @Test
  public void toJson_MarshallableWithPrettyPrint_PrettyJsonString() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    String prettyJson = ColferUtils.toJson(message, true);
    assertNotNull(prettyJson);
    assertTrue(prettyJson.contains("\n"));
    assertTrue(prettyJson.contains(" "));
  }

  @Test
  public void format_Marshallable_FormattedObject() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    Object formatted = ColferUtils.format(message);
    assertNotNull(formatted);
    assertEquals(ColferUtils.toJson(message, false), formatted.toString());
  }
}
