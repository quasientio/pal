package net.ittera.pal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Values.ReturnValue;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: - null value - private, protected, package-visible - primitives - arrays - objectrefs
 */
public class SetClassVariableMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.StaticVars";

  @Test
  public void putStatic_integerNotNull_ok() throws Exception {

    String fieldName = "aStaticInteger";
    String fieldClassName = "int";

    Integer originalValue = 3000;
    Integer newValue = 3200;

    try {
      // get field
      ReturnValue retValue = callGetStatic(className, fieldName);

      // test returned (original) value
      Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof Integer);
      assertEquals(originalValue, rawObj);

      // set a new value
      callPutStatic(className, fieldName, fieldClassName, newValue);

      // get again and test
      retValue = callGetStatic(className, fieldName);
      assertValueIsObjectOfType(retValue, fieldClassName);
      rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof Integer);
      assertEquals(newValue, rawObj);
    } finally {
      // now revert changed value to original (otherwise other tests may fail after a 1st run)
      callPutStatic(className, fieldName, fieldClassName, originalValue);
    }
  }

  @Test
  public void putStatic_stringNotNull_ok() throws Exception {

    // test with a non null String
    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";

    String originalValue = "I'm classy";
    String newValue = "New dummy str";

    try {
      // get field
      ReturnValue retValue = callGetStatic(className, fieldName);

      // test returned (original) value
      Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof String);
      assertEquals(originalValue, rawObj);

      // set a new value
      callPutStatic(className, fieldName, fieldClassName, newValue);

      // test that the field has now the new value
      retValue = callGetStatic(className, fieldName);
      assertValueIsObjectOfType(retValue, fieldClassName);
      rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof String);
      assertEquals(newValue, rawObj);
    } finally {
      // now revert changed value to original (otherwise other tests may fail after a 1st run)
      callPutStatic(className, fieldName, fieldClassName, originalValue);
    }
  }

  @Test
  public void putStatic_noSuchField_exThrown() throws Exception {

    String fieldName = "aMadeUpField";
    String fieldClassName = "java.lang.String";

    callPutStatic(
        className, fieldName, fieldClassName, "whatever", "java.lang.NoSuchFieldException");
  }

  @Test
  public void putStatic_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    callGetStatic(nonExistingClass, "aStaticInteger", "java.lang.ClassNotFoundException");
  }
}
