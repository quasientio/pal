/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.Executable;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcErrorData;
import io.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import io.quasient.pal.messages.types.JsonRpcType;
import io.quasient.pal.messages.types.MessageType;
import java.util.Optional;
import org.junit.Test;

public class JsonRpcMessageUtilsTest {

  @Test
  public void parseAndValidate_validRequest() throws Exception {
    Params params = Params.builder().withType("com.acme.Foo").build();
    JsonRpcRequest request =
        JsonRpcRequest.builder().withId("1").withMethod("new").withParams(params).build();
    String json = JsonRpcSerializer.toJson(request);

    JsonRpcRequest parsed = JsonRpcMessageUtils.parseAndValidateJsonRpcMessage(json);
    assertEquals(request, parsed);
  }

  @Test
  public void parseAndValidate_malformedWithId_extractsId() {
    // malformed json, but includes an id field that should be extracted leniently
    String malformed =
        "{"
            + "\"jsonrpc\":\"2.0\",\n"
            + "\"id\":\"abc123\",\n"
            + "\"method\":\"new\",\n"
            + "\"params\": { \"type\": \"com.acme.Foo\" "
            + // missing closing braces
            "";

    JsonRpcParseException ex =
        assertThrows(
            JsonRpcParseException.class,
            () -> JsonRpcMessageUtils.parseAndValidateJsonRpcMessage(malformed));
    assertEquals("abc123", ex.getRequestId());
  }

  @Test
  public void isMethodNotFoundError_knownTypes() {
    assertThat(
        JsonRpcMessageUtils.isMethodNotFoundError("java.lang.NoSuchMethodException"), is(true));
    assertThat(JsonRpcMessageUtils.isMethodNotFoundError("foo.Bar"), is(false));
  }

  @Test
  public void getClassName_request() {
    Params params = Params.builder().withType("com.acme.Foo").withMethod("m").build();
    JsonRpcRequest req =
        JsonRpcRequest.builder().withId("1").withMethod("call").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getClassName(req).get(), is("com.acme.Foo"));

    JsonRpcRequest unknown = new JsonRpcRequest();
    unknown.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
    unknown.setId("2");
    unknown.setMethod("noop");
    unknown.setParams(params);
    assertThat(JsonRpcMessageUtils.getClassName(unknown), is(Optional.empty()));
  }

  @Test
  public void getClassName_response_variants() {
    // Throwable case
    JsonRpcErrorData data =
        JsonRpcErrorData.builder().withThrowableType("java.lang.NoSuchFieldException").build();
    JsonRpcError error =
        JsonRpcError.builder().withCode(-32601).withMessage("nf").withData(data).build();
    JsonRpcResponse resp = JsonRpcResponse.builder().withId("1").withError(error).build();
    assertThat(JsonRpcMessageUtils.getClassName(resp).get(), is("java.lang.NoSuchFieldException"));

    // PUT field done (void + field op)
    Executable from =
        Executable.builder()
            .withClassName("com.acme.Foo")
            .withFieldName("bar")
            .withModifiers(0)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(true).withFrom(from).build();
    resp = JsonRpcResponse.builder().withId("2").withResult(rv).build();
    assertThat(JsonRpcMessageUtils.getClassName(resp).get(), is("com.acme.Foo"));

    // Return value case
    ResponseObject value =
        ResponseObject.builder().withType("java.lang.String").withValue("x").build();
    rv =
        JsonRpcResponseReturnValue.builder()
            .withIsVoid(false)
            .withValue(value)
            .withFrom(from)
            .build();
    resp = JsonRpcResponse.builder().withId("3").withResult(rv).build();
    assertThat(JsonRpcMessageUtils.getClassName(resp).get(), is("java.lang.String"));

    // Unsupported when both result and error are null
    JsonRpcResponse unsupported = JsonRpcResponse.builder().withId("4").build();
    assertThrows(
        IllegalArgumentException.class, () -> JsonRpcMessageUtils.getClassName(unsupported));
  }

  @Test
  public void getFieldName_response_and_request() {
    // response: put field done with field name present
    Executable from =
        Executable.builder()
            .withClassName("com.acme.Foo")
            .withFieldName("bar")
            .withModifiers(0)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(true).withFrom(from).build();
    JsonRpcResponse resp = JsonRpcResponse.builder().withId("1").withResult(rv).build();
    assertThat(JsonRpcMessageUtils.getFieldName(resp).get(), is("bar"));

    // response: non field operation -> empty
    ResponseObject value =
        ResponseObject.builder().withType("java.lang.String").withValue("x").build();
    rv =
        JsonRpcResponseReturnValue.builder()
            .withIsVoid(false)
            .withValue(value)
            .withFrom(from)
            .build();
    resp = JsonRpcResponse.builder().withId("2").withResult(rv).build();
    assertThat(JsonRpcMessageUtils.getFieldName(resp), is(Optional.empty()));

    // request: get with field set
    Params params = Params.builder().withType("com.acme.Foo").withField("bar").build();
    JsonRpcRequest req =
        JsonRpcRequest.builder().withId("3").withMethod("get").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getFieldName(req).get(), is("bar"));

    // request: non field method -> empty
    JsonRpcRequest reqNonField = new JsonRpcRequest();
    reqNonField.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
    reqNonField.setId("4");
    reqNonField.setMethod("new");
    Params weird = Params.builder().withType("com.acme.Foo").withField("bar").build();
    reqNonField.setParams(weird);
    assertThat(JsonRpcMessageUtils.getFieldName(reqNonField), is(Optional.empty()));
  }

  @Test
  public void getMessageType_overloads_and_errors() {
    // Request variants
    Params params = Params.builder().withType("T").build();
    JsonRpcRequest req =
        JsonRpcRequest.builder().withId("1").withMethod("new").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_CONSTRUCTOR));

    params = Params.builder().withType("T").withMethod("m").build();
    req = JsonRpcRequest.builder().withId("2").withMethod("call").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_CLASS_METHOD));
    params = Params.builder().withType("T").withMethod("m").withInstance(1).build();
    req = JsonRpcRequest.builder().withId("3").withMethod("call").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_INSTANCE_METHOD));

    params = Params.builder().withType("T").withField("f").build();
    req = JsonRpcRequest.builder().withId("4").withMethod("get").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_GET_STATIC));
    params = Params.builder().withType("T").withField("f").withInstance(1).build();
    req = JsonRpcRequest.builder().withId("5").withMethod("get").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_GET_FIELD));

    params =
        Params.builder()
            .withType("T")
            .withField("f")
            .withValue(Argument.builder().withValue("x").withType("java.lang.String").build())
            .build();
    req = JsonRpcRequest.builder().withId("6").withMethod("put").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_PUT_STATIC));
    params =
        Params.builder()
            .withType("T")
            .withField("f")
            .withInstance(1)
            .withValue(Argument.builder().withValue("x").withType("java.lang.String").build())
            .build();
    req = JsonRpcRequest.builder().withId("7").withMethod("put").withParams(params).build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.EXEC_PUT_FIELD));

    req =
        JsonRpcRequest.builder()
            .withId("8")
            .withMethod("meta")
            .withParams(Params.builder().withType("T").withMethod("fetch_classes_info").build())
            .build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.META_MESSAGE_REQUEST));
    req =
        JsonRpcRequest.builder()
            .withId("9")
            .withMethod("control")
            .withParams(Params.builder().withType("T").withMethod("ping").build())
            .build();
    assertThat(JsonRpcMessageUtils.getMessageType(req), is(MessageType.CONTROL_MESSAGE_REQUEST));

    JsonRpcRequest bad = new JsonRpcRequest();
    bad.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
    bad.setId("x");
    bad.setMethod("noop");
    bad.setParams(Params.builder().withType("T").build());
    assertThrows(IllegalArgumentException.class, () -> JsonRpcMessageUtils.getMessageType(bad));

    // getMessageType(JsonRpcMessage) unsupported subtype
    JsonRpcMessage unknown = new JsonRpcMessage() {};
    assertThrows(IllegalArgumentException.class, () -> JsonRpcMessageUtils.getMessageType(unknown));
  }

  @Test
  public void getJsonRpcType_overloads_and_error() {
    // request -> REQUEST
    JsonRpcRequest req =
        JsonRpcRequest.builder()
            .withId("1")
            .withMethod("new")
            .withParams(Params.builder().withType("T").build())
            .build();
    assertThat(JsonRpcMessageUtils.getJsonRpcType(req), is(JsonRpcType.REQUEST));

    // mapping by MessageType
    assertThat(
        JsonRpcMessageUtils.getJsonRpcType(MessageType.EXEC_RETURN_VALUE),
        is(JsonRpcType.RESPONSE));
    assertThat(
        JsonRpcMessageUtils.getJsonRpcType(MessageType.EXEC_PUT_FIELD_DONE),
        is(JsonRpcType.RESPONSE));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonRpcMessageUtils.getJsonRpcType(MessageType.META_MESSAGE_RESPONSE));
  }
}
