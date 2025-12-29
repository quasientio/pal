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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class CallMessageIT extends AbstractJsonRpcMessageIT {

  private static int messageId = 0;
  private static final String CLASS_NAME = "io.quasient.pal.apps.quantized.rpc.Methods";

  public CallMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void callClassMethod_privateWithArg_void() throws Exception {
    String methodName = "testVoidStatic";
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"type": "java.lang.String", "value": "Hello from a unit test"}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callClassMethod_privateWithPrimitiveAndWrapperArgs_void() throws Exception {
    String methodName = "printArg";
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"type": "int", "value": 2},
                  {"type": "java.lang.String", "value": "more than an argument"}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callClassMethod_packageWithNoArgs_void() throws Exception {
    String methodName = "doSomethingStatically";
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callClassMethod_publicStaticVoidMain_void() throws Exception {
    String methodName = "main";
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"type": "[Ljava.lang.String;", "value": []}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callClassMethod_withObjectRefAsArg_void() throws Exception {
    final String methodName = "sumUpList";

    // 1. Create a new ArrayList instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "java.util.ArrayList"
              }
            }
            """
            .formatted(++messageId);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);

    // Assert that the result is not null
    assertNotNull(createResponse.getResult());

    // Get the object reference
    assertNotNull(createResponse.getResult().getValue());
    Integer listRef = createResponse.getResult().getValue().getRef();
    assertNotNull(listRef);

    // 2. Add integers to the list
    int[] someIntegers = {39, 5, 58, 32, 70, 42};
    for (int someInt : someIntegers) {
      String addRequest =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "java.util.ArrayList",
                  "method": "add",
                  "instance": %d,
                  "args": [
                    {"type": "java.lang.Integer", "value": %d}
                  ]
                }
              }
              """
              .formatted(++messageId, listRef, someInt);

      JsonRpcResponse addResponse = sendAndReceive(addRequest);
      // Assert that the result is not null
      assertNotNull(addResponse.getResult());
    }

    // 3. Call the static method with the list as argument
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"ref": %d}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, listRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callClassMethod_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    String methodName = "doSomethingStatically";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, nonExistingClass, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(
        responseMessage.getError().getCode(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getCode()));
    assertThat(
        responseMessage.getError().getMessage(),
        is(JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.ClassNotFoundException"));
    assertThat(responseMessage.getError().getData().getMessage(), is(nonExistingClass));
  }

  @Test
  public void callClassMethod_noSuchMethod_exThrown() throws Exception {
    String methodName = "a_made_up_method";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(
        responseMessage.getError().getCode(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getCode()));
    assertThat(
        responseMessage.getError().getMessage(),
        is(JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.NoSuchMethodException"));
    assertThat(responseMessage.getError().getData().getMessage(), containsString(methodName));
  }

  @Test
  public void callClassMethod_throwsRuntimeEx_exThrown() throws Exception {
    String methodName = "throwRuntimeException";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(), is("java.lang.RuntimeException"));
    assertThat(
        responseMessage.getError().getData().getMessage(),
        containsString("Bastards threw me out!"));
  }

  @Test
  public void callClassMethod_privateWithArg_retValue() throws Exception {
    String methodName = "testNonVoidStatic";
    String param = "GIVE ME THIS IN LOWERCASE";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"type": "java.lang.String", "value": "%s"}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, param);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    String shouldReturn = param.toLowerCase(Locale.getDefault());
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_protectedNoArgs_retValue() throws Exception {
    String methodName = "highFive";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Integer shouldReturn = 5;
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_returnsIntegerSum_retValue() throws Exception {
    final String methodName = "nonVoidSumUpList";

    // 1. Create a new ArrayList instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "java.util.ArrayList"
              }
            }
            """
            .formatted(++messageId);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());

    assertNotNull(createResponse.getResult().getValue());
    Integer listRef = createResponse.getResult().getValue().getRef();
    assertNotNull(listRef);

    // 2. Add integers to the list
    int[] someIntegers = {39, 5, 58, 32, 70, 42};
    for (int someInt : someIntegers) {
      String addRequest =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "java.util.ArrayList",
                  "method": "add",
                  "instance": %d,
                  "args": [
                    {"type": "java.lang.Integer", "value": %d}
                  ]
                }
              }
              """
              .formatted(++messageId, listRef, someInt);

      JsonRpcResponse addResponse = sendAndReceive(addRequest);
      assertNotNull(addResponse.getResult());
    }

    // 3. Call the sum up method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"ref": %d}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, listRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Integer shouldReturn = Arrays.stream(someIntegers).sum();
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_usingCollectionsReturnsIntegerSum_retValue() throws Exception {
    final String methodName = "nonVoidSumUpList";

    // 1. Create a new ArrayList instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "java.util.ArrayList"
              }
            }
            """
            .formatted(++messageId);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());

    assertNotNull(createResponse.getResult().getValue());
    Integer listRef = createResponse.getResult().getValue().getRef();
    assertNotNull(listRef);

    // 2. Add integers to the list as an array using Collections.addAll
    Integer[] someIntegers = {39, 5, 58, 32, 70, 42}; // needs to be Integer[], not int[]
    String addRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "java.util.Collections",
                "method": "addAll",
                "args": [
                  {"ref": %d},
                  {"type": "%s", "value": %s}
                ]
              }
            }
            """
            .formatted(
                ++messageId,
                listRef,
                someIntegers.getClass().getName(),
                arrayToJsonString(someIntegers, false));

    JsonRpcResponse addResponse = sendAndReceive(addRequest);
    assertNotNull(addResponse.getResult());

    // 3. Call the sum up method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"ref": %d}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, listRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Integer shouldReturn = Arrays.stream(someIntegers).mapToInt(Integer::intValue).sum();
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_returningNullObject_nullRetValue() throws Exception {
    String methodName = "giveMeNull";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));

    // Unwrap the returned value
    assertNotNull(responseMessage.getResult().getValue());
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test that the returned value is null
    assertNull(resultValue);
  }

  @Test
  public void callClassMethod_returningCharArray_retValue() throws Exception {
    String methodName = "toCharArray";
    String param = "split me up";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "args": [
                  {"type": "java.lang.String", "value": "%s"}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, param);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    char[] shouldReturn = param.toCharArray();
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_returningEmptyArray_retValue() throws Exception {
    String methodName = "giveMeAnEmptyLongArray";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Long[] shouldReturn = {};
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callClassMethod_returningNullArray_nullRetValue() throws Exception {
    String methodName = "giveMeNullBoolArray";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));

    // Unwrap the returned value
    assertNotNull(responseMessage.getResult().getValue());
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test that the returned value is null
    assertNull(resultValue);
  }

  @Test
  public void callClassMethod_returningObjectRef_refRetValue() throws Exception {
    String methodName = "getThreadSingleton";

    // First call
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());
    Integer firstRef = responseMessage.getResult().getValue().getRef();
    assertNotNull(firstRef);

    // Second call
    request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    responseMessage = sendAndReceive(request);
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());
    Integer secondRef = responseMessage.getResult().getValue().getRef();
    assertNotNull(secondRef);

    // Assert that the references are the same
    assertThat(firstRef, is(secondRef));
  }

  @Test
  @Ignore
  public void callClassMethod_returningObjectRefArray_refRetValue() throws Exception {
    String methodName = "getThreadArray";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Assert that it's an array of object references
    assertTrue(resultValue instanceof Integer[] || resultValue instanceof int[]);
  }

  @Test
  public void callClassMethod_badFormat_exThrown() throws Exception {
    String methodName = "parseInt";
    String param = "not_a_num";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "java.lang.Integer",
                "method": "%s",
                "args": [
                  {"type": "java.lang.String", "value": "%s"}
                ]
              }
            }
            """
            .formatted(++messageId, methodName, param);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.NumberFormatException"));
    assertThat(
        responseMessage.getError().getData().getMessage(), containsString("For input string"));
  }

  @Test
  public void callClassMethod_throwsEx_exThrown() throws Exception {
    String methodName = "throwMeAnException";

    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(), is("java.lang.RuntimeException"));
    assertThat(responseMessage.getError().getData().getMessage(), is("Here you go"));
  }

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_void() throws Exception {
    final String methodName = "doSomething";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callInstanceMethod_privateWithArg_void() throws Exception {
    final String methodName = "testArg";
    final String param = "testing testing 1 2 3";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method with argument
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d,
                "args": [
                  {"type": "java.lang.String", "value": "%s"}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef, param);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callInstanceMethod_protectedNoArgs_void() throws Exception {
    final String methodName = "printDate";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is void
    assertThat(responseMessage.getResult().getIsVoid(), is(true));
    assertNull(responseMessage.getResult().getValue());
  }

  @Test
  public void callInstanceMethod_nullArg_throwsEx() throws Exception {
    final String methodName = "testNonNullArg";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method with null argument
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d,
                "args": [
                  {"type": "java.lang.String", "value": null}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.NullPointerException"));
  }

  @Test
  public void callInstanceMethod_noSuchClass_throwsEx() throws Exception {
    final String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    final String methodName = "testNonNullArg";

    // 1. Create a new instance of CLASS_NAME
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Attempt to call a method on a non-existing class
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d,
                "args": [
                  {"type": "java.lang.String", "value": "some value"}
                ]
              }
            }
            """
            .formatted(++messageId, nonExistingClass, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(
        responseMessage.getError().getCode(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getCode()));
    assertThat(
        responseMessage.getError().getMessage(),
        is(JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.ClassNotFoundException"));
    assertThat(responseMessage.getError().getData().getMessage(), is(nonExistingClass));
  }

  @Test
  public void callInstanceMethod_noSuchMethod_throwsEx() throws Exception {
    final String methodName = "a_made_up_method";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Attempt to call a non-existing method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(
        responseMessage.getError().getCode(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getCode()));
    assertThat(
        responseMessage.getError().getMessage(),
        is(JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.NoSuchMethodException"));
    assertThat(responseMessage.getError().getData().getMessage(), containsString(methodName));
  }

  @Test
  public void callInstanceMethod_noSuchInstance_throwsNullPointerException() throws Exception {
    String methodName = "printDate";

    // Use a fake non-existing instance reference
    int fakeInstanceRef = 2398248;

    // Attempt to call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, fakeInstanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(
        responseMessage.getError().getData().getThrowableType(),
        is("java.lang.NullPointerException"));
  }

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_retValue() throws Exception {
    final String methodName = "giveMeX";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Integer shouldReturn = 4;
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callInstanceMethod_publicReturnsListAsRef_retValue() throws Exception {
    final String methodName = "getListOfStrings";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));

    // The returned value should be an object reference
    assertNotNull(responseMessage.getResult().getValue());
    Integer listRef = responseMessage.getResult().getValue().getRef();
    assertNotNull(listRef);
  }

  @Test
  public void callInstanceMethod_publicReturnsNativelyInitListAsRef_retValue() throws Exception {
    final String methodName = "getListOfStringsShorthand";

    // 1. Create a new instance
    String createRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createResponse = sendAndReceive(createRequest);
    assertNotNull(createResponse.getResult());
    assertNotNull(createResponse.getResult().getValue());
    Integer instanceRef = createResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));

    // The returned value should be an object reference
    assertNotNull(responseMessage.getResult().getValue());
    Integer listRef = responseMessage.getResult().getValue().getRef();
    assertNotNull(listRef);
  }

  @Test
  public void callInstanceMethod_withObjectsAndObjectrefsAsArgs_retValue() throws Exception {
    final String methodName = "addOffsetToListAndSumUp";

    // 1. Create a new ArrayList instance
    String createListRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "java.util.ArrayList"
              }
            }
            """
            .formatted(++messageId);

    JsonRpcResponse createListResponse = sendAndReceive(createListRequest);
    assertNotNull(createListResponse.getResult());
    assertNotNull(createListResponse.getResult().getValue());
    Integer listRef = createListResponse.getResult().getValue().getRef();
    assertNotNull(listRef);

    // 2. Add integers to the list
    int[] someIntegers = {1, 2, 3, 5, 7, 9};
    for (int someInt : someIntegers) {
      String addRequest =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "java.util.ArrayList",
                  "method": "add",
                  "instance": %d,
                  "args": [
                    {"type": "java.lang.Integer", "value": %d}
                  ]
                }
              }
              """
              .formatted(++messageId, listRef, someInt);

      JsonRpcResponse addResponse = sendAndReceive(addRequest);
      assertNotNull(addResponse.getResult());
    }

    // 3. Create an instance of CLASS_NAME
    String createInstanceRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createInstanceResponse = sendAndReceive(createInstanceRequest);
    assertNotNull(createInstanceResponse.getResult());
    assertNotNull(createInstanceResponse.getResult().getValue());
    Integer instanceRef = createInstanceResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 4. Call the method with offset and listRef
    int offsetParam = 10;
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d,
                "args": [
                  {"type": "int", "value": %d},
                  {"ref": %d}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef, offsetParam, listRef);

    JsonRpcResponse responseMessage = sendAndReceive(request);
    assertNotNull(responseMessage.getResult());
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    assertNotNull(responseMessage.getResult().getValue());

    // Unwrap the returned value
    Object resultValue = Unwrapper.unwrapObject(responseMessage.getResult().getValue());

    // Test returned value
    Integer shouldReturn = Arrays.stream(someIntegers).map(i -> i + offsetParam).sum();
    assertThat(resultValue, is(shouldReturn));
  }

  @Test
  public void callInstanceMethod_throwsCheckedException_exThrown() throws Exception {
    final String methodName = "throwsCheckedException";

    // 1. Create a new instance
    String createInstanceRequest =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "new",
              "params": {
                "type": "%s"
              }
            }
            """
            .formatted(++messageId, CLASS_NAME);

    JsonRpcResponse createInstanceResponse = sendAndReceive(createInstanceRequest);
    assertNotNull(createInstanceResponse.getResult());
    assertNotNull(createInstanceResponse.getResult().getValue());
    Integer instanceRef = createInstanceResponse.getResult().getValue().getRef();
    assertNotNull(instanceRef);

    // 2. Prepare parameter
    long param = (long) Integer.MAX_VALUE + 1;

    // 3. Call the instance method
    String request =
            """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "method": "call",
              "params": {
                "type": "%s",
                "method": "%s",
                "instance": %d,
                "args": [
                  {"type": "long", "value": %d}
                ]
              }
            }
            """
            .formatted(++messageId, CLASS_NAME, methodName, instanceRef, param);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is null
    assertNull(responseMessage.getResult());

    // Verify error code and message
    assertNotNull(responseMessage.getError());
    assertThat(responseMessage.getError().getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(
        responseMessage.getError().getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));

    // Assert errorData
    assertNotNull(responseMessage.getError().getData());
    assertThat(responseMessage.getError().getData().getThrowableType(), is("java.lang.Exception"));
  }
}
