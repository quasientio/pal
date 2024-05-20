package net.ittera.pal.messages.jsonrpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.ittera.pal.messages.types.ExecMessageType;
import org.junit.Test;

public class JsonRpcRequestTest {

  @Test
  public void testConstructorCall() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "new:net.ittera.pal.core.exec.RPCMessageInvoker";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals(ExecMessageType.CONSTRUCTOR, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testConstructorCallUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "new:RPCMessageInvoker";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals(ExecMessageType.CONSTRUCTOR, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testStaticCall() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.RPCMessageInvoker.getPeerUuid";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.CLASS_METHOD, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testStaticCallUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "RPCMessageInvoker.getPeerUuid";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.CLASS_METHOD, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testNonStaticCall() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.RPCMessageInvoker.1234.getPeerUuid";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.INSTANCE_METHOD, request.getExecMessageType());
    assertEquals("1234", request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testNonStaticCallUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "RPCMessageInvoker.1234.getPeerUuid";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.INSTANCE_METHOD, request.getExecMessageType());
    assertEquals("1234", request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertNull(request.getFieldName());
  }

  @Test
  public void testStaticFieldGet() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "get:net.ittera.pal.core.exec.RPCMessageInvoker.myClassField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.GET_STATIC, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myClassField", request.getFieldName());
  }

  @Test
  public void testStaticFieldGetUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "get:RPCMessageInvoker.myClassField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.GET_STATIC, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myClassField", request.getFieldName());
  }

  @Test
  public void testInstanceFieldGet() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "get:net.ittera.pal.core.exec.RPCMessageInvoker.738476.myField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.GET_FIELD, request.getExecMessageType());
    assertEquals("738476", request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myField", request.getFieldName());
  }

  @Test
  public void testInstanceFieldGetUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "get:RPCMessageInvoker.738476.myField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.GET_FIELD, request.getExecMessageType());
    assertEquals("738476", request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myField", request.getFieldName());
  }

  @Test
  public void testStaticFieldPut() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "put:net.ittera.pal.core.exec.RPCMessageInvoker.myClassField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.PUT_STATIC, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myClassField", request.getFieldName());
  }

  @Test
  public void testStaticFieldPutUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "put:RPCMessageInvoker.myClassField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.PUT_STATIC, request.getExecMessageType());
    assertNull(request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myClassField", request.getFieldName());
  }

  @Test
  public void testInstanceFieldPut() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "put:net.ittera.pal.core.exec.RPCMessageInvoker.738476.myField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals(
        "net.ittera.pal.core.exec.RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.PUT_FIELD, request.getExecMessageType());
    assertEquals("738476", request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myField", request.getFieldName());
  }

  @Test
  public void testInstanceFieldPutUnnamedPackage() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "put:RPCMessageInvoker.738476.myField";
    request.setMethod(method);
    request.processMethodParts();
    assertEquals("RPCMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("RPCMessageInvoker", request.getClassName());
    assertEquals(ExecMessageType.PUT_FIELD, request.getExecMessageType());
    assertEquals("738476", request.getObjectRef());
    assertNull(request.getMethodName());
    assertEquals("myField", request.getFieldName());
  }
}
