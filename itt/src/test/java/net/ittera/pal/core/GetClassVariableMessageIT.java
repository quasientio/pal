package net.ittera.pal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Values.ReturnValue;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: arrays objectrefs rest of primitive types (?)
 */
public class GetClassVariableMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.StaticVars";

  @Test
  public void getClassVariable_publicStringNotNull_varReturned() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aClassString");
    assertValueIsObjectOfType(retValue, "java.lang.String");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals("I'm classy", rawObj);
  }

  @Test
  public void getClassVariable_publicStringNull_nullStringReturned() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullStaticStr");
    assertValueIsNullObjectOfType(retValue, "java.lang.String");
  }

  @Test
  public void getClassVariable_privateIntegerNotNull_intReturned() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aPrivateClassInt");
    assertValueIsObjectOfType(retValue, "java.lang.Integer");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(39328, rawObj);
  }

  @Test
  public void getClassVariable_protectedBoolNull_nullBoolReturned() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aProtectedBool");
    assertValueIsNullObjectOfType(retValue, "java.lang.Boolean");
  }

  @Test
  public void getClassVariable_packageVisibleBoolNotNull_boolReturned() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aPackageVisibleBool");
    assertValueIsObjectOfType(retValue, "boolean");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(true, rawObj);
  }

  @Test
  public void getClassVariable_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    callGetStatic(nonExistingClass, "aProtectedBool", "java.lang.ClassNotFoundException");
  }

  @Test
  public void getClassVariable_noSuchField_exThrown() throws Exception {
    callGetStatic(className, "aMadeUpField", "java.lang.NoSuchFieldException");
  }
}
