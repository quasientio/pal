/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior. This test is not
 * parameterized on TargetType, so it only runs against a Peer via direct socket RPC. That is
 * because Error responses to JsonRpcRequests are not written to the Log if they are parse/invalid
 * params/invalid request errors that are returned before dispatching.
 */
public class JsonRpcResponseErrorIT extends AbstractJsonRpcMessageIT {

  protected final String className = "io.quasient.pal.apps.quantized.rpc.Constructors";
  private static int messageId = 0;

  @Test
  public void constructor_invalidJson_parseErrorThrown() throws Exception {

    // missing comma after "method": "new"
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "method": "new"
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request, String.valueOf(messageId));

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.PARSE_ERROR,
        null,
        "Failed to deserialize JSON-RPC message");
  }

  @Test
  public void constructor_invalidRequestNoMethod_invalidJsonRpcRequestThrown() throws Exception {

    // missing method field
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId, responseMessage, JsonRpcErrorCode.INVALID_REQUEST, null, "Method is missing");
  }

  @Test
  public void constructor_invalidRequestInvalidMethod_invalidJsonRpcRequestThrown()
      throws Exception {

    // missing method field
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "method": "notAMethod",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_REQUEST,
        null,
        "Invalid method: notAMethod");
  }

  @Test
  public void constructor_constructor3DoublesDoesNotExist_noSuchMethodThrown() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "double", "value": 239823d},
                          {"type": "double", "value": 38723d},
                          {"type": "double", "value": 2323d}
                        ]
                      }
                    }
                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchMethodException",
        "No matching constructor found");
  }

  @Test
  public void constructor_noSuchClass_classNotFoundThrown() throws Exception {
    String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "method": "new",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(++messageId, nonExistingClass);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.ClassNotFoundException",
        "io.quasient.pal.apps.IDontExist");
  }

  @Test
  public void constructor_missingParams_invalidParamsThrown() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": "%s",
                      "method": "new"
                    }
                    """
            .formatted(++messageId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId, responseMessage, JsonRpcErrorCode.INVALID_PARAMS, null, "Params are missing");
  }

  @Test
  public void constructor_missingTypeInParams_invalidParamsThrown() throws Exception {
    String request =
            """
                         {
                          "jsonrpc": "2.0",
                          "id": "%s",
                          "method": "new",
                          "params": {
                            "args": [
                              {"type": "double", "value": 2323d}
                            ]
                          }
                        }
                        """
            .formatted(++messageId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Type is missing in params");
  }

  @Test
  public void constructor_invalidCharsInType_invalidParamsThrown() throws Exception {
    String request =
            """
                             {
                              "jsonrpc": "2.0",
                              "id": "%s",
                              "method": "new",
                              "params": {
                                "type": "InvalidClass:Name"
                              }
                            }
                            """
            .formatted(++messageId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Invalid characters in type");
  }

  @Test
  public void constructor_reservedKeywordInType_invalidParamsThrown() throws Exception {
    String request =
            """
                                 {
                                  "jsonrpc": "2.0",
                                  "id": "%s",
                                  "method": "new",
                                  "params": {
                                    "type": "try"
                                  }
                                }
                                """
            .formatted(++messageId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Type name is a Java reserved keyword");
  }

  @Test
  public void constructor_callWithoutMethod_invalidParamsThrown() throws Exception {
    String request =
            """
                                     {
                                      "jsonrpc": "2.0",
                                      "id": "%s",
                                      "method": "call",
                                      "params": {
                                        "type": "%s"
                                      }
                                    }
                                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Method is missing in 'call' request");
  }

  @Test
  public void constructor_getWithoutField_invalidParamsThrown() throws Exception {
    String request =
            """
                                     {
                                      "jsonrpc": "2.0",
                                      "id": "%s",
                                      "method": "get",
                                      "params": {
                                        "type": "%s"
                                      }
                                    }
                                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Field is missing in 'get' request");
  }

  @Test
  public void constructor_putWithoutField_invalidParamsThrown() throws Exception {
    String request =
            """
                                     {
                                      "jsonrpc": "2.0",
                                      "id": "%s",
                                      "method": "put",
                                      "params": {
                                        "type": "%s",
                                        "value": {"type": "double", "value": 232233d}
                                      }
                                    }
                                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Field is missing in 'put' request");
  }

  @Test
  public void constructor_putWithoutValue_invalidParamsThrown() throws Exception {
    String request =
            """
                                     {
                                      "jsonrpc": "2.0",
                                      "id": "%s",
                                      "method": "put",
                                      "params": {
                                        "type": "%s",
                                        "field": "foo"
                                      }
                                    }
                                    """
            .formatted(++messageId, className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        messageId,
        responseMessage,
        JsonRpcErrorCode.INVALID_PARAMS,
        null,
        "Value is missing in 'put' request");
  }
}
