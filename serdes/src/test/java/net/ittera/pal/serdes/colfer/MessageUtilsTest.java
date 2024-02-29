package net.ittera.pal.serdes.colfer;

import static org.junit.Assert.*;

import java.util.stream.Stream;
import net.ittera.pal.messages.jsonrpc.InvalidJsonRpcRequestException;
import org.junit.Test;

public class MessageUtilsTest {

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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Invalid characters in class name"));
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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Class name is a Java reserved keyword"));
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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
              } catch (InvalidJsonRpcRequestException e) {
                throw new RuntimeException(e);
              }
            });
  }

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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field put must have exactly one parameter"));
              }
            });
  }

  @Test
  public void justatest() throws InvalidJsonRpcRequestException {
    String method = "put:net.ittera.pal.core.exec.RPCMessageInvoker.peerUuid";
    String jsonRpcMessage =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[{\"value\": 50}],\"id\":1}",
            method);
    MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field put must have exactly one parameter"));
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
                MessageUtils.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertTrue(e.getMessage().contains("Field get cannot have any parameter"));
              }
            });
  }
}
