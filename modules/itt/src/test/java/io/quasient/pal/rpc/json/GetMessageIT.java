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
public class GetMessageIT extends AbstractJsonRpcMessageIT {

  private static final String CLASS_NAME = "io.quasient.pal.apps.rpc.Variables";

  public GetMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void getClassVariable_publicStringNotNull_varReturned() throws Exception {
    String fieldName = "aClassString";
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, String.class, "I'm classy");
  }

  @Test
  public void getClassVariable_publicStringNull_nullStringReturned() throws Exception {
    String fieldName = "aNullStaticStr";
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, String.class, null);
  }

  @Test
  public void getClassVariable_privateIntegerNotNull_intReturned() throws Exception {
    String fieldName = "aPrivateClassInt";
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Integer.class, 39328);
  }

  @Test
  public void getClassVariable_protectedBoolNull_nullBoolReturned() throws Exception {
    String fieldName = "aProtectedBool";
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Boolean.class, null);
  }

  @Test
  public void getClassVariable_packageVisibleBoolNotNull_boolReturned() throws Exception {
    String fieldName = "aPackageVisibleBool";
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Boolean.class, true);
  }

  @Test
  public void getClassVariable_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    String fieldName = "aProtectedBool";
    String requestId = generateId();
    String request =
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
            .formatted(nonExistingClass, fieldName, requestId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        requestId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.ClassNotFoundException",
        nonExistingClass);
  }

  @Test
  public void getClassVariable_noSuchField_exThrown() throws Exception {
    String fieldName = "aMadeUpField";
    String requestId = generateId();
    String request =
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
            .formatted(CLASS_NAME, fieldName, requestId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        requestId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }

  @Test
  public void getInstanceVariable_publicIntegerNotNull_intReturned() throws Exception {
    final String fieldName = "anInt";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Integer.class, 4);
  }

  @Test
  public void getInstanceVariable_privateNullInteger_nullIntReturned() throws Exception {
    final String fieldName = "myNullInt";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Integer.class, null);
  }

  @Test
  public void getInstanceVariable_protectedStringNotNull_stringReturned() throws Exception {
    final String fieldName = "someString";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, String.class, "I'm not blank");
  }

  @Test
  public void getInstanceVariable_getPublicStringNull_nullStringReturned() throws Exception {
    final String fieldName = "myNullStr";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, String.class, null);
  }

  @Test
  public void getInstanceVariable_packageVisibleBooleanNull_nullBoolReturned() throws Exception {
    final String fieldName = "myNullBool";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Boolean.class, null);
  }

  @Test
  public void getInstanceVariable_publicBoolNotNull_boolReturned() throws Exception {
    final String fieldName = "myBool";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Boolean.class, true);
  }

  @Test
  public void getInstanceVariable_privateShortNotZero_shortReturned() throws Exception {
    final String fieldName = "someShort";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the instance was created
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Get the instance variable
    String request =
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

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertResultEqualsTypeAndValue(responseMessage, Short.class, (short) 233);
  }

  @Test
  public void getInstanceVariable_noSuchClass_exThrown() throws Exception {
    final String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    final String fieldName = "someShort";

    // Create a new instance of CLASS_NAME
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to get a field from a non-existing class
    String requestId = generateId();
    String request =
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
            .formatted(nonExistingClass, fieldName, instanceRef, requestId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        requestId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.ClassNotFoundException",
        nonExistingClass);
  }

  @Test
  public void getInstanceVariable_noSuchInstance_npeThrown() throws Exception {
    String fieldName = "someShort";

    // Use a fake non-existing instance reference
    int fakeInstanceRef = 38923;

    // Attempt to get the field
    String requestId = generateId();
    String request =
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
            .formatted(CLASS_NAME, fieldName, fakeInstanceRef, requestId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        requestId,
        responseMessage,
        JsonRpcErrorCode.SERVER_ERROR,
        "java.lang.NullPointerException",
        "No object found with objRef: " + fakeInstanceRef);
  }

  @Test
  public void getInstanceVariable_noSuchField_exThrown() throws Exception {
    final String fieldName = "aMadeUpField";

    // Create a new instance
    String createRequest =
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

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to get a non-existing field
    String requestId = generateId();
    String request =
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
            .formatted(CLASS_NAME, fieldName, instanceRef, requestId);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    assertErrorResponse(
        requestId,
        responseMessage,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }
}
