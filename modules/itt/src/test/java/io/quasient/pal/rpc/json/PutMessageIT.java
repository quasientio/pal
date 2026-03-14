/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class PutMessageIT extends AbstractJsonRpcMessageIT {

  private static final String CLASS_NAME = "io.quasient.foobar.apps.rpc.Variables";

  public PutMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void putStatic_integerNotNull_ok() throws Exception {
    final String fieldName = "aStaticInteger";
    final Integer originalValue = 3000;
    final Integer newValue = 3200;

    try {
      // 1. Get the original value
      String getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, generateId());

      JsonRpcResponse getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, Integer.class, originalValue);

      // 2. Set a new value
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "value": %d
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, newValue, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);

      // 3. Get the field again to verify
      getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, generateId());

      getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, Integer.class, newValue);
    } finally {
      // Reset the field to the original value
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "value": %d
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, originalValue, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);
    }
  }

  @Test
  public void putStatic_stringNotNull_ok() throws Exception {
    String fieldName = "aClassString";
    String originalValue = "I'm classy";
    String newValue = "New dummy str";

    try {
      // 1. Get the original value
      String getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, generateId());

      JsonRpcResponse getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, String.class, originalValue);

      // 2. Set a new value
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "value": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, newValue, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);

      // 3. Get the field again to verify
      getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, generateId());

      getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, String.class, newValue);
    } finally {
      // Reset the field to the original value
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "value": "%s"
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, originalValue, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);
    }
  }

  @Test
  public void putStatic_noSuchField_exThrown() throws Exception {
    String fieldName = "aMadeUpField";
    String value = "whatever";
    String requestId = generateId();

    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "value": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }

  @Test
  public void putStatic_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "io.quasient.foobar.apps.IDontExist";
    String fieldName = "aStaticInteger";
    String value = "someValue";
    String requestId = generateId();

    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "value": "%s"
                },
                "id": %s
              }
              """
            .formatted(nonExistingClass, fieldName, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.ClassNotFoundException",
        nonExistingClass);
  }

  @Test
  public void putField_integer_ok() throws Exception {
    final String fieldName = "anInt";
    final Integer originalValue = 4;
    final Integer newValue = 500;

    // Create new instance
    String newRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "new",
                "params": {
                  "type": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, generateId());

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 1. Get the original value
    String getRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "get",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, generateId());

    JsonRpcResponse getResponse = sendAndReceive(getRequest);
    assertResultEqualsTypeAndValue(getResponse, Integer.class, originalValue);

    // 2. Set a new value
    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d,
                  "value": %d
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, newValue, generateId());

    JsonRpcResponse putResponse = sendAndReceive(putRequest);
    assertPutResultIsVoid(putResponse);

    // 3. Get the field again to verify
    getRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "get",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, generateId());

    getResponse = sendAndReceive(getRequest);
    assertResultEqualsTypeAndValue(getResponse, Integer.class, newValue);
  }

  @Test
  public void putField_integerSetNull_ok() throws Exception {
    String fieldName = "anotherInt";
    Integer originalValue = 1;

    // Create new instance
    String newRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "new",
                "params": {
                  "type": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, generateId());

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    try {
      // 1. Get the original value
      String getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "instance": %d
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, generateId());

      JsonRpcResponse getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, Integer.class, originalValue);

      // 2. Set the field to null
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "instance": %d,
                    "value": null
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);

      // 3. Get the field again to verify
      getRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "get",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "instance": %d
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, generateId());

      getResponse = sendAndReceive(getRequest);
      assertResultEqualsTypeAndValue(getResponse, Integer.class, null);
    } finally {
      // Reset the field to the original value
      String putRequest =
          """
                {
                  "jsonrpc": "2.0",
                  "method": "put",
                  "params": {
                    "type": "%s",
                    "field": "%s",
                    "instance": %d,
                    "value": %d
                  },
                  "id": %s
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, originalValue, generateId());

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);
    }
  }

  @Test
  public void putField_wrongType_exThrown() throws Exception {
    final String fieldName = "anInt";
    final String type = "java.lang.String"; // String instead of expected Integer
    final String value = "500";

    // Create new instance
    String newRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "new",
                "params": {
                  "type": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, generateId());

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the field with wrong type
    String requestId = generateId();
    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d,
                  "value": {
                    "type": "%s",
                    "value": "%s"
                  }
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, type, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId,
        putResponse,
        JsonRpcErrorCode.SERVER_ERROR,
        "java.lang.IllegalArgumentException");
  }

  @Test
  public void putField_noSuchField_exThrown() throws Exception {
    final String fieldName = "aMadeUpField";
    final String value = "500";

    // Create new instance
    String newRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "new",
                "params": {
                  "type": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, generateId());

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the non-existing field
    String requestId = generateId();
    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d,
                  "value": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }

  @Test
  public void putField_noSuchClass_exThrown() throws Exception {
    final String nonExistingClass = "io.quasient.foobar.apps.IDontExist";
    final String fieldName = "anInt";
    final String value = "500";

    // Create new instance (of existing class)
    String newRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "new",
                "params": {
                  "type": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, generateId());

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the field on non-existing class
    String requestId = generateId();
    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d,
                  "value": "%s"
                },
                "id": %s
              }
              """
            .formatted(nonExistingClass, fieldName, instanceRef, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.ClassNotFoundException",
        nonExistingClass);
  }

  @Test
  public void putField_noSuchInstance_npeThrown() throws Exception {
    String fieldName = "anInt";
    String value = "500";
    int fakeInstanceRef = 30482239;

    // Attempt to set the field on a non-existing instance
    String requestId = generateId();
    String putRequest =
        """
              {
                "jsonrpc": "2.0",
                "method": "put",
                "params": {
                  "type": "%s",
                  "field": "%s",
                  "instance": %d,
                  "value": "%s"
                },
                "id": %s
              }
              """
            .formatted(CLASS_NAME, fieldName, fakeInstanceRef, value, requestId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        requestId, putResponse, JsonRpcErrorCode.SERVER_ERROR, "java.lang.NullPointerException");
  }
}
