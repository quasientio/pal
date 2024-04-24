package net.ittera.pal.serdes.jsonrpc;

import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;
import static org.junit.Assert.*;

import java.util.stream.Stream;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcMessageUtilsTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  // <editor-fold desc="illegal characters and words">
  @Test
  public void parseJsonRpcMessage_illegalCharactersInClassName_invalidJsonRpcRequestException() {
    Stream.of(
            "net.ittera.pal.core.exec.3DModel.1234.getPeerUuid", // starts with a digit
            "net.ittera.pal.core.exec.My-Class.getPeerUuid", // contains a hyphen
            "new:net.ittera.pal.core.exec.#Settings", // contains a hash
            "new:net.ittera.pal.core.exec.RPCMessage Invoker", // contains a space
            "get:net.ittera.pal.core.exec.RPCMessage/Invoker.peerUuid", // contains a slash
            "get:net.ittera.pal.core.exec.Peer*MessageInvoker.peerUuid" // contains an asterisk
            )
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Invalid characters in class name"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  @Test
  public void
      parseJsonRpcMessage_illegalUseOfReservedKeywordInClassName_invalidJsonRpcRequestException() {
    Stream.of(
            "class",
            "null",
            "true",
            "false",
            "final",
            "public",
            "private",
            "protected",
            "static",
            "void",
            "int",
            "long",
            "float",
            "double",
            "byte",
            "short",
            "char",
            "boolean",
            "if",
            "else",
            "while",
            "for",
            "do",
            "switch",
            "case",
            "default",
            "break",
            "continue",
            "return",
            "try",
            "catch",
            "finally",
            "throw",
            "throws",
            "new",
            "this",
            "super",
            "extends",
            "implements",
            "interface",
            "package",
            "import",
            "instanceof",
            "enum",
            "assert",
            "abstract",
            "const",
            "goto",
            "native",
            "synchronized",
            "transient",
            "volatile")
        .forEach(
            classname -> {
              String method = String.format("get:net.ittera.pal.core.exec.%s.peerUuid", classname);
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Class name is a Java reserved keyword"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_validClassNamesAndMethod_noException() {
    Stream.of(
            "com.example.MyClass",
            "com.example._MyClass",
            "com.example.$MyClass",
            "com.example.MyClass84732",
            "com.example.MyCla__ss",
            "com.example.$MyCla$$")
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[{\"value\": %s},{\"value\": %d}],\"id\":1}",
                      method, "myParam1", 12345);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
              } catch (InvalidJsonRpcRequestException e) {
                throw new RuntimeException(e);
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="number of params in field ops">
  @Test
  public void parseJsonRpcMessage_noParametersGivenForPut_invalidJsonRpcRequestException() {
    Stream.of(
            "put:net.ittera.pal.core.exec.RPCMessageInvoker.peerUuid", // static put
            "put:net.ittera.pal.core.exec.RPCMessageInvoker.479345.peerUuid" // instance put
            )
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field put must have exactly one parameter"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_putWithParam_ok() throws InvalidJsonRpcRequestException {
    String method = "put:net.ittera.pal.core.exec.RPCMessageInvoker.peerUuid";
    String jsonRpcMessage =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[{\"value\": 50}],\"id\":1}",
            method);
    parseAndValidateJsonRpcMessage(jsonRpcMessage);
  }

  @Test
  public void parseJsonRpcMessage_twoParametersGivenForPut_invalidJsonRpcRequestException() {
    Stream.of(
            "put:net.ittera.pal.core.exec.RPCMessageInvoker.peerUuid", // static put
            "put:net.ittera.pal.core.exec.RPCMessageInvoker.479345.peerUuid" // instance put
            )
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[{\"value\": %s},{\"value\": %d}],\"id\":1}",
                      method, "myParam1", 12345);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field put must have exactly one parameter"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_parametersGivenForGet_invalidJsonRpcRequestException() {
    Stream.of(
            "get:net.ittera.pal.core.exec.RPCMessageInvoker.peerUuid", // static get
            "get:net.ittera.pal.core.exec.RPCMessageInvoker.479345.peerUuid" // instance get
            )
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\": \"2.0\", \"method\": \"%s\", \"params\": [{\"value\": %d}], \"id\": 1}",
                      method, 123);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field get cannot have any parameter"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="missing required elements">
  @Test
  public void parseJsonRpcMessage_missingId_invalidJsonRpcRequestException() {
    Stream.of(
            "{\"jsonrpc\": \"2.0\", \"method\": \"print\"}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"print\", \"_id\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"print\", \"id\": \"\" }",
            "{\"jsonrpc\": \"2.0\", \"method\": \"print\", \"id\": null }")
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertNull(e.getRequestId());
                assertTrue(e.getMessage().contains("Missing or blank id"));
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_missingOrInvalidJsonRpcVersion_invalidJsonRpcRequestException() {
    Stream.of(
            "{\"method\": \"com.example.MyClass.print\", \"id\": 1}",
            "{\"jsonrpc\": \"\", \"method\": \"com.example.MyClass.print\", \"id\": 1}",
            "{\"jsonrpc\": null, \"method\": \"com.example.MyClass.print\", \"id\": 1}",
            "{\"jsonrpc\": \"1.0\", \"method\": \"com.example.MyClass.print\", \"id\": 1}",
            "{\"jsonrpc\": \"3.0\", \"method\": \"com.example.MyClass.print\", \"id\": 1}")
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertNotNull(e.getRequestId());
                assertTrue(
                    e.getMessage().contains("Invalid JSON-RPC version")
                        || e.getMessage().contains("Missing required element: jsonrpc"));
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_missingMethod_invalidJsonRpcRequestException() {
    Stream.of(
            "{\"jsonrpc\": \"2.0\", \"id\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"\", \"id\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": null, \"id\": 1}")
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertNotNull(e.getRequestId());
                assertTrue(e.getMessage().contains("Missing required element: method"));
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="unknown elements">
  @Test
  public void parseJsonRpcMessage_unknownElement_invalidJsonRpcRequestException() {
    Stream.of(
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"id\": 1, \"unknown\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"id\": 1, \"class\": \"SomeClass\"}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"id\": 1, \"parameters\": null}")
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertNotNull(e.getRequestId());
                assertTrue(e.getMessage().contains("Unexpected element:"));
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="type and value of params">
  @Test
  public void parseJsonRpcMessage_paramWithStringValueAndNoType_ok() {
    Stream.of(String.format("\"%s\"", "Hello, World!"), "{\"value\": \"Hello, World!\"}")
        .forEach(
            params -> {
              JsonRpcRequest jsonRpcRequest;
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [%s], \"id\": 1}",
                      params);
              logger.debug(jsonRpcMessage);
              try {
                jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
              } catch (InvalidJsonRpcRequestException e) {
                throw new RuntimeException(e);
              }
              assertNotNull(jsonRpcRequest.getParams());
              assertEquals(jsonRpcRequest.getParams().size(), 1);
              assertEquals(jsonRpcRequest.getParams().get(0).getValue(), "Hello, World!");
              assertNull(jsonRpcRequest.getParams().get(0).getType());
            });
  }

  @Test
  public void parseJsonRpcMessage_nullValueParams_ok() {
    Stream.of("null", "{\"value\": null}", "{}")
        .forEach(
            params -> {
              JsonRpcRequest jsonRpcRequest;
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [%s], \"id\": 1}",
                      params);
              logger.debug(jsonRpcMessage);
              try {
                jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
              } catch (InvalidJsonRpcRequestException e) {
                throw new RuntimeException(e);
              }
              assertNotNull(jsonRpcRequest.getParams());
              assertEquals(jsonRpcRequest.getParams().size(), 1);
              assertNull(jsonRpcRequest.getParams().get(0).getValue());
              assertNull(jsonRpcRequest.getParams().get(0).getType());
            });
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefButNoValueGiven_invalidJsonRpcRequestException() {
    String jsonRpcMessage =
        "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"type\": \"ref\"}], \"id\": 1}";
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected InvalidJsonRpcParamsException");
    } catch (InvalidJsonRpcParamsException e) {
      assertNotNull(e.getRequestId());
    } catch (InvalidJsonRpcRequestException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefButValueIsBoolean_invalidJsonRpcRequestException() {
    String jsonRpcMessage =
        "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"type\": \"ref\", \"value\":false}], \"id\": 1}";
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected InvalidJsonRpcParamsException");
    } catch (InvalidJsonRpcParamsException e) {
      assertNotNull(e.getRequestId());
    } catch (InvalidJsonRpcRequestException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void
      parseJsonRpcMessage_typeIsRefButValueIsDoubleAsString_invalidJsonRpcRequestException() {
    String jsonRpcMessage =
        "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"type\": \"ref\", \"value\":\"23255.44\"}], \"id\": 1}";

    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected InvalidJsonRpcParamsException");
    } catch (InvalidJsonRpcParamsException e) {
      assertNotNull(e.getRequestId());
    } catch (InvalidJsonRpcRequestException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefAndValueIsInt_ok()
      throws InvalidJsonRpcRequestException {
    String jsonRpcMessage =
        "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"type\": \"ref\", \"value\":23255}], \"id\": 1}";
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
    assertNotNull(jsonRpcRequest.getParams());
    assertEquals(jsonRpcRequest.getParams().size(), 1);
    assertTrue(jsonRpcRequest.getParams().get(0).isRef());
    assertEquals(jsonRpcRequest.getParams().get(0).getValue(), 23255);

    // type is NOT in Param when its value == "ref"
    assertNull(jsonRpcRequest.getParams().get(0).getType());
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefAndValueIsIntAsString_ok()
      throws InvalidJsonRpcRequestException {
    String jsonRpcMessage =
        "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"type\": \"ref\", \"value\":\"23255\"}], \"id\": 1}";

    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
    assertNotNull(jsonRpcRequest.getParams());
    assertEquals(jsonRpcRequest.getParams().size(), 1);
    assertTrue(jsonRpcRequest.getParams().get(0).isRef());
    assertEquals(jsonRpcRequest.getParams().get(0).getValue(), 23255);

    // type is NOT in Param when its value == "ref"
    assertNull(jsonRpcRequest.getParams().get(0).getType());
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefAndValueIsString_ok()
      throws InvalidJsonRpcRequestException {
    String jsonRpcMessage =
        "{\"jsonrpc\":\"2.0\", \"method\":\"net.ittera.pal.examples.HelloPeople.main\", \"id\":\"53eeb24c-f058-49e5-aa92-8e10ac049368\", \"params\":[{\"value\":[\"Ma\",\"Na\"], \"type\":\"[Ljava.lang.String;\"}]}";
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
    assertNotNull(jsonRpcRequest.getParams());
    assertEquals(jsonRpcRequest.getParams().size(), 1);
    assertFalse(jsonRpcRequest.getParams().get(0).isRef());
  }

  // </editor-fold>

  // <editor-fold desc="unexpected elements in params">
  @Test
  public void parseJsonRpcMessage_unexpectedElementInParams_invalidJsonRpcParamsException() {
    Stream.of(
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"values\": 23255}], \"id\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"val\": 23255}], \"id\": 1}",
            "{\"jsonrpc\": \"2.0\", \"method\": \"com.example.MyClass.print\", \"params\": [{\"is_ref\": true}], \"id\": 1}")
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcParamsException");
              } catch (InvalidJsonRpcParamsException e) {
                assertNotNull(e.getRequestId());
              }
            });
  }

  // </editor-fold>
}
