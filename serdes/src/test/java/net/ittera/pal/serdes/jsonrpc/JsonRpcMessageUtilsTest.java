package net.ittera.pal.serdes.jsonrpc;

import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.stream.Stream;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.Params;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcMessageUtilsTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  // <editor-fold desc="illegal characters and words">
  @Test
  public void parseJsonRpcMessage_illegalCharactersInClassName_invalidJsonRpcParamsException() {
    Stream.of(
            "net.ittera.pal.core.exec.3DModel.1234GetPeerUuid", // starts with a digit
            "net.ittera.pal.core.exec.My-Class", // contains a hyphen
            "net.ittera.pal.core.exec.#Settings", // contains a hash
            "net.ittera.pal.core.exec.RPCMessage Invoker", // contains a space
            "net.ittera.pal.core.exec.RPCMessage/Invoker", // contains a slash
            "net.ittera.pal.core.exec.Peer*MessageInvoker" // contains an asterisk
            )
        .forEach(
            className -> {
              String jsonRpcMessage =
                      """
                      {
                        "jsonrpc": "2.0",
                        "method": "call",
                        "params": {
                          "type": "%s",
                          "method": "readPage",
                          "instance": 1234,
                          "args": [
                            {
                              "value": 4
                            }
                          ]
                        },
                        "id": 1
                      }
                      """
                      .formatted(className);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcParamsException");
              } catch (InvalidJsonRpcParamsException e) {
                assertTrue(e.getMessage().contains("Invalid characters in type"));
                assertNotNull(e.getRequestId());
              }
            });
  }

  @Test
  public void
      parseJsonRpcMessage_illegalUseOfReservedKeywordInClassName_invalidJsonRpcParamsException() {
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
            keyword -> {
              String jsonRpcMessage =
                      """
                     {
                       "jsonrpc": "2.0",
                       "method": "call",
                       "params": {
                         "type": "%s",
                         "method": "readPage",
                         "instance": 1234,
                         "args": [
                           {
                             "value": 4
                           }
                         ]
                       },
                       "id": 1
                     }
                     """
                      .formatted(keyword);
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcParamsException");
              } catch (InvalidJsonRpcParamsException e) {
                assertTrue(e.getMessage().contains("Type name is a Java reserved keyword"));
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
            validClassName -> {
              String jsonRpcMessage =
                      """
                    {
                      "jsonrpc": "2.0",
                      "method": "call",
                      "params": {
                        "type": "%s",
                        "method": "readPage",
                        "instance": 1234,
                        "args": [
                          {
                            "value": 4
                          }
                        ]
                      },
                      "id": 1
                    }
                    """
                      .formatted(validClassName);
              parseAndValidateJsonRpcMessage(jsonRpcMessage);
            });
  }

  // </editor-fold>

  // <editor-fold desc="values in field ops">
  @Test
  public void parseJsonRpcMessage_noValueGivenForPut_invalidJsonRpcParamsException() {
    String jsonRpcMessage =
        """
           {
            "jsonrpc": "2.0",
            "method": "put",
            "params": {
              "type": "SomeClass",
              "field": "page",
              "instance": 1234
            },
            "id": 1
          }
          """;
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected InvalidJsonRpcParamsException");
    } catch (InvalidJsonRpcParamsException e) {
      assertTrue(e.getMessage().contains("Value is missing"));
      assertNotNull(e.getRequestId());
    }
  }

  @Test
  public void parseJsonRpcMessage_putWithValue_ok() {
    String jsonRpcMessage =
        """
           {
            "jsonrpc": "2.0",
            "method": "put",
            "params": {
              "type": "SomeClass",
              "field": "page",
              "instance": 1234,
              "value": 4
            },
            "id": 1
          }
          """;
    parseAndValidateJsonRpcMessage(jsonRpcMessage);
  }

  @Test
  public void parseJsonRpcMessage_valueGivenForGet_invalidJsonRpcParamsException() {
    String jsonRpcMessage =
        """
           {
            "jsonrpc": "2.0",
            "method": "get",
            "params": {
              "type": "SomeClass",
              "field": "page",
              "instance": 1234,
              "value": 4
            },
            "id": 1
          }
          """;
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected InvalidJsonRpcParamsException");
    } catch (InvalidJsonRpcParamsException e) {
      assertTrue(e.getMessage().contains("Value should be null"));
      assertNotNull(e.getRequestId());
    }
  }

  // </editor-fold>

  // <editor-fold desc="missing required elements">
  @Test
  public void parseJsonRpcMessage_missingOrInvalidId_invalidJsonRpcRequestException() {
    Stream.of(
            """
          {
           "jsonrpc": "2.0",
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
         {
          "jsonrpc": "2.0",
          "method": "new",
          "params": {
            "type": "SomeClass"
          },
          "_id": 1
         }
         """,
            """
         {
          "jsonrpc": "2.0",
          "method": "new",
          "params": {
           "type": "SomeClass"
          },
          "id": ""
         }
         """,
            """
         {
          "jsonrpc": "2.0",
          "method": "new",
          "params": {
           "type": "SomeClass"
          },
          "id": null
         }
         """,
            """
         {
          "jsonrpc": "2.0",
          "method": "new",
          "params": {
            "type": "SomeClass"
          },
          "id": true
         }
         """)
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected JsonRpcParseException | InvalidJsonRpcRequestException");
              } catch (JsonRpcParseException e) {
                assertNull(e.getRequestId());
                assertEquals(
                    "id must be a number or string",
                    e.getJsonParsingException().getCause().getMessage());
              } catch (InvalidJsonRpcRequestException e) {
                assertNull(e.getRequestId());
                assertEquals("Request Id is missing", e.getMessage());
              }
            });
  }

  @Test
  public void parseJsonRpcMessage_missingOrInvalidJsonRpcVersion_invalidJsonRpcRequestException() {
    Stream.of(
            """
          {
            "id": 1,
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": "",
            "id": 1,
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": null,
            "id": 1,
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": "1.0",
            "id": 1,
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": "3.0",
            "id": 1,
           "method": "new",
           "params": {
             "type": "SomeClass"
           }
         }
         """)
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
            """
          {
           "jsonrpc": "2.0",
            "id": 1,
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": "2.0",
            "id": 1,
           "method": "",
           "params": {
             "type": "SomeClass"
           }
         }
         """,
            """
          {
           "jsonrpc": "2.0",
            "id": 1,
           "method": null,
           "params": {
             "type": "SomeClass"
           }
         }
         """)
        .forEach(
            jsonRpcMessage -> {
              try {
                parseAndValidateJsonRpcMessage(jsonRpcMessage);
                fail("Expected InvalidJsonRpcRequestException");
              } catch (InvalidJsonRpcRequestException e) {
                assertNotNull(e.getRequestId());
                assertTrue(e.getMessage().contains("Method"));
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="type and value of args">
  @Test
  public void parseJsonRpcMessage_argWithStringValueAndNoType_ok() {
    Stream.of(
            """
                      "Hello, World!"
                      """,
            """
                      {"value": "Hello, World!"}
                      """)
        .forEach(
            params -> {
              JsonRpcRequest jsonRpcRequest;
              String jsonRpcMessage =
                      """
                       {
                        "jsonrpc": "2.0",
                        "method": "call",
                        "params": {
                          "type": "com.example.MyClass",
                          "method": "print",
                          "args": [%s]
                        },
                        "id": 1
                      }
                      """
                      .formatted(params);
              logger.debug(jsonRpcMessage);
              jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
              assertNotNull(jsonRpcRequest.getParams());
              Params callParams = jsonRpcRequest.getParams();
              assertEquals(1, callParams.getArgs().size());
              assertEquals("Hello, World!", callParams.getArgs().get(0).getValue());
              assertNull(callParams.getArgs().get(0).getType());
            });
  }

  @Test
  public void parseJsonRpcMessage_nullValueParams_ok() {
    Stream.of(
            "null",
            """
               {"value": null}
               """,
            "{}")
        .forEach(
            params -> {
              JsonRpcRequest jsonRpcRequest;
              String jsonRpcMessage =
                      """
                      {
                        "jsonrpc": "2.0",
                        "method": "call",
                        "params": {
                          "type": "com.example.MyClass",
                          "method": "print",
                          "args": [%s]
                        },
                        "id": 1
                      }
                      """
                      .formatted(params);
              logger.debug(jsonRpcMessage);
              jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
              assertNotNull(jsonRpcRequest.getParams());
              Params callParams = jsonRpcRequest.getParams();
              assertThat(callParams.getArgs().size(), is(1));
              assertNull(callParams.getArgs().get(0).getValue());
              assertNull(callParams.getArgs().get(0).getType());
            });
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefButNotInt_jsonRpcParseException() {
    String jsonRpcMessage =
        """
         {
          "jsonrpc": "2.0",
          "method": "call",
          "params": {
            "type": "com.example.MyClass",
            "method": "print",
            "args": [
              {"ref": "helol world"}
            ]
          },
          "id": 1
        }
        """;
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected JsonRpcParseException");
    } catch (JsonRpcParseException e) {
      assertNotNull(e.getRequestId()); // ensure that the request id is not null
    }
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefButValueIsBoolean_jsonRpcParseException() {
    String jsonRpcMessage =
        """
             {
              "jsonrpc": "2.0",
              "method": "call",
              "params": {
                "type": "com.example.MyClass",
                "method": "print",
                "args": [
                  {"ref": true}
                ]
              },
              "id": 1
            }
            """;
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected JsonRpcParseException");
    } catch (JsonRpcParseException e) {
      assertNotNull(e.getRequestId()); // ensure that the request id is not null
    }
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefButValueIsDoubleAsString_jsonRpcParseException() {
    String jsonRpcMessage =
        """
             {
              "jsonrpc": "2.0",
              "method": "call",
              "params": {
                "type": "com.example.MyClass",
                "method": "print",
                "args": [
                  {"ref": "123.45"}
                ]
              },
              "id": 1
            }
            """;
    try {
      parseAndValidateJsonRpcMessage(jsonRpcMessage);
      fail("Expected JsonRpcParseException");
    } catch (JsonRpcParseException e) {
      assertNotNull(e.getRequestId());
    }
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefAndValueIsInt_ok() {
    String jsonRpcMessage =
        """
             {
              "jsonrpc": "2.0",
              "method": "call",
              "params": {
                "type": "com.example.MyClass",
                "method": "print",
                "args": [
                  {"ref": 123}
                ]
              },
              "id": 1
            }
            """;
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
    assertNotNull(jsonRpcRequest.getParams());
    Params callParams = jsonRpcRequest.getParams();
    assertThat(callParams.getArgs().size(), is(1));
    assertNotNull(callParams.getArgs().get(0).getRef());
    assertThat(callParams.getArgs().get(0).getRef(), is(123));

    // type is NOT in arg when is a ref
    assertNull(callParams.getArgs().get(0).getType());
  }

  @Test
  public void parseJsonRpcMessage_typeIsRefAndValueIsIntAsString_ok()
      throws InvalidJsonRpcRequestException {
    String jsonRpcMessage =
        """
          {
          "jsonrpc": "2.0",
          "method": "call",
          "params": {
            "type": "com.example.MyClass",
            "method": "print",
            "args": [
              {"ref": "123"}
            ]
          },
          "id": 1
          }
        """;
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);
    assertNotNull(jsonRpcRequest.getParams());
    Params callParams = jsonRpcRequest.getParams();
    assertThat(callParams.getArgs().size(), is(1));
    assertNotNull(callParams.getArgs().get(0).getRef());
    assertThat(callParams.getArgs().get(0).getRef(), is(123));

    assertNull(callParams.getArgs().get(0).getType());
    assertNull(callParams.getArgs().get(0).getValue());
  }

  // </editor-fold>
}
