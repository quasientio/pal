package net.ittera.pal.messages.jsonrpc;

import static org.junit.Assert.*;

import org.junit.Test;

public class JsonRpcRequestTest {

  @Test
  public void testConstructorCallWithNewSuffix() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.PeerMessageInvoker.new";
    request.setMethod(method);
    assertEquals(
        "net.ittera.pal.core.exec.PeerMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("PeerMessageInvoker", request.getClassName());
    assertNull(request.getObjectRef());
    assertEquals("new", request.getMethodName());
    assertTrue(request.isConstructorCall());
  }

  @Test
  public void testConstructorCallWithoutNewSuffix() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.PeerMessageInvoker";
    request.setMethod(method);
    assertEquals(
        "net.ittera.pal.core.exec.PeerMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("PeerMessageInvoker", request.getClassName());
    assertNull(request.getObjectRef());
    assertEquals("new", request.getMethodName());
    assertTrue(request.isConstructorCall());
  }

  @Test
  public void testStaticCall() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.PeerMessageInvoker.getPeerUuid";
    request.setMethod(method);
    assertEquals(
        "net.ittera.pal.core.exec.PeerMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("PeerMessageInvoker", request.getClassName());
    assertNull(request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertFalse(request.isConstructorCall());
  }

  @Test
  public void testNonStaticCall() {
    JsonRpcRequest request = new JsonRpcRequest();
    String method = "net.ittera.pal.core.exec.PeerMessageInvoker.1234.getPeerUuid";
    request.setMethod(method);
    assertEquals(
        "net.ittera.pal.core.exec.PeerMessageInvoker", request.getFullyQualifiedClassName());
    assertEquals("PeerMessageInvoker", request.getClassName());
    assertEquals("1234", request.getObjectRef());
    assertEquals("getPeerUuid", request.getMethodName());
    assertFalse(request.isConstructorCall());
  }
}
