package net.ittera.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.JsonRpcErrorCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class PutMessageIT extends AbstractJsonRpcMessageIT {

  private static int messageId = 0;
  private static final String CLASS_NAME = "net.ittera.pal.apps.rpc.Variables";

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, newValue, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, originalValue, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, newValue, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, originalValue, ++messageId);

      JsonRpcResponse putResponse = sendAndReceive(putRequest);
      assertPutResultIsVoid(putResponse);
    }
  }

  @Test
  public void putStatic_noSuchField_exThrown() throws Exception {
    String fieldName = "aMadeUpField";
    String value = "whatever";

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }

  @Test
  public void putStatic_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    String fieldName = "aStaticInteger";
    String value = "someValue";

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
                "id": %d
              }
              """
            .formatted(nonExistingClass, fieldName, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId,
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, ++messageId);

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, ++messageId);

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, newValue, ++messageId);

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, ++messageId);

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, ++messageId);

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
                  "id": %d
                }
                """
              .formatted(CLASS_NAME, fieldName, instanceRef, originalValue, ++messageId);

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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, ++messageId);

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the field with wrong type
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, type, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId,
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, ++messageId);

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the non-existing field
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, instanceRef, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId,
        putResponse,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        "java.lang.NoSuchFieldException",
        fieldName);
  }

  @Test
  public void putField_noSuchClass_exThrown() throws Exception {
    final String nonExistingClass = "net.ittera.pal.apps.IDontExist";
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, ++messageId);

    JsonRpcResponse newResponse = sendAndReceive(newRequest);
    assertNotNull(newResponse.getResult());
    assertNotNull(newResponse.getResult().getValue());
    Integer instanceRef = newResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // Attempt to set the field on non-existing class
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
                "id": %d
              }
              """
            .formatted(nonExistingClass, fieldName, instanceRef, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId,
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
                "id": %d
              }
              """
            .formatted(CLASS_NAME, fieldName, fakeInstanceRef, value, ++messageId);

    JsonRpcResponse putResponse = sendAndReceive(putRequest);

    assertErrorResponse(
        messageId, putResponse, JsonRpcErrorCode.SERVER_ERROR, "java.lang.NullPointerException");
  }
}
