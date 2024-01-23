package net.ittera.pal.serdes.colfer;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class JsonRpcToExecMessageConverterTest {

  @Test
  public void parseJsonRpcMessage_illegalCharactersInClassName_illegalArgumentException() {
    JsonRpcToExecMessageConverter converter = new JsonRpcToExecMessageConverter();
    Arrays.asList(
            "net.ittera.pal.core.exec.3DModel.1234.getPeerUuid", // starts with a digit
            "net.ittera.pal.core.exec.My-Class.getPeerUuid", // contains a hyphen
            "new:net.ittera.pal.core.exec.#Settings", // contains a hash
            "new:net.ittera.pal.core.exec.PeerMessage Invoker", // contains a space
            "get:net.ittera.pal.core.exec.PeerMessage/Invoker.peerUuid", // contains a slash
            "get:net.ittera.pal.core.exec.Peer*MessageInvoker.peerUuid" // contains an asterisk
            )
        .stream()
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                converter.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected IllegalArgumentException");
              } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Invalid characters in class name"));
              }
            });
  }

  @Test
  public void
      parseJsonRpcMessage_illegalUseOfReservedKeywordInClassName_illegalArgumentException() {
    JsonRpcToExecMessageConverter converter = new JsonRpcToExecMessageConverter();
    Arrays.asList(
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
        .stream()
        .forEach(
            classname -> {
              String method = String.format("get:net.ittera.pal.core.exec.%s.peerUuid", classname);
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                converter.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected IllegalArgumentException");
              } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Class name is a Java reserved keyword"));
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_validClassNamesAndMethod_noException() {
    JsonRpcToExecMessageConverter converter = new JsonRpcToExecMessageConverter();
    Arrays.asList(
            "com.example.MyClass",
            "com.example._MyClass",
            "com.example.$MyClass",
            "com.example.MyClass84732",
            "com.example.MyCla__ss",
            "com.example.$MyCla$$")
        .stream()
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              converter.parseAndValidateJsonRpcMessage(jsonRpcMessage);
            });
  }

  @Test
  public void parseJsonRpcMessage_noParametersGivenForPut_illegalArgumentException() {
    JsonRpcToExecMessageConverter converter = new JsonRpcToExecMessageConverter();
    Arrays.asList(
            "put:net.ittera.pal.core.exec.PeerMessageInvoker.peerUuid", // static put
            "put:net.ittera.pal.core.exec.PeerMessageInvoker.479345.peerUuid" // instance put
            )
        .stream()
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", method);
              try {
                converter.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected IllegalArgumentException");
              } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Field put must have exactly one parameter"));
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_parametersGivenForGet_illegalArgumentException() {
    JsonRpcToExecMessageConverter converter = new JsonRpcToExecMessageConverter();
    Arrays.asList(
            "get:net.ittera.pal.core.exec.PeerMessageInvoker.peerUuid", // static get
            "get:net.ittera.pal.core.exec.PeerMessageInvoker.479345.peerUuid" // instance get
            )
        .stream()
        .forEach(
            method -> {
              String jsonRpcMessage =
                  String.format(
                      "{\"jsonrpc\": \"2.0\", \"method\": \"%s\", \"params\": [{\"value\": %d}], \"id\": 1}",
                      method, 123);
              try {
                converter.parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected IllegalArgumentException");
              } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Field get cannot have any parameter"));
              }
            });
  }
}
