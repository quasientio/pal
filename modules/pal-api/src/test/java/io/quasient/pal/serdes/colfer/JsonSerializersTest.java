/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.ClInitCall;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.Constructor;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.Context;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.InterceptKeyMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptResponse;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.InternalHeaderType;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;

/**
 * Tests for the serializer and deserializer inner classes defined in {@link JsonSerializers}.
 *
 * <p>Uses {@link ColferUtils#toJson} to serialize Colfer messages, then asserts on the resulting
 * JSON. For adapter classes that also deserialize, round-trip verification is performed using a
 * custom {@link Gson} instance with the adapters registered.
 */
public class JsonSerializersTest {

  /** Tests {@link JsonSerializers.ClInitCallSerializer} with all fields populated. */
  @Test
  public void clInitCallSerializer_serializesAllFields() throws Exception {
    ClInitCall msg = new ClInitCall();
    Class clazz = new Class();
    clazz.name = "com.example.MyClass";
    msg.clazz = clazz;
    msg.modifiers = 1;
    Context ctx = new Context();
    ctx.sourceLocationType = "com.example.Sender";
    msg.context = ctx;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("class"), is(true));
    assertThat(parsed.has("modifiers"), is(true));
    assertThat(parsed.get("modifiers").getAsInt(), is(1));
    assertThat(parsed.has("context"), is(true));
  }

  /**
   * Tests {@link JsonSerializers.InterceptCallbackResponseSerializer} with all fields set on an
   * {@link InterceptCallbackResponseMessage}.
   */
  @Test
  public void interceptCallbackResponseSerializer_serializesAllFields() throws Exception {
    InterceptCallbackResponseMessage msg = new InterceptCallbackResponseMessage();
    msg.callbackId = "cb-123";
    msg.phase = InterceptPhase.BEFORE.toByte();
    Obj arg = new Obj();
    arg.value = "argValue";
    msg.mutatedArgs = new Obj[] {arg};
    msg.shouldProceed = true;
    msg.overrideReturn = true;
    Obj retVal = new Obj();
    retVal.value = "newReturn";
    msg.newReturnValue = retVal;
    msg.newReturnRef = 42;
    msg.throwException = true;
    RaisedThrowable ex = new RaisedThrowable();
    ex.inInitializer = true;
    msg.exception = ex;
    msg.isApiMisuseError = true;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("callback_id").getAsString(), is("cb-123"));
    assertThat(parsed.get("phase").getAsString(), is("BEFORE"));
    assertThat(parsed.has("mutated_args"), is(true));
    assertThat(parsed.get("should_proceed").getAsBoolean(), is(true));
    assertThat(parsed.get("override_return").getAsBoolean(), is(true));
    assertThat(parsed.has("new_return_value"), is(true));
    assertThat(parsed.get("new_return_ref").getAsInt(), is(42));
    assertThat(parsed.get("throw_exception").getAsBoolean(), is(true));
    assertThat(parsed.has("exception"), is(true));
    assertThat(parsed.get("is_api_misuse_error").getAsBoolean(), is(true));
  }

  /**
   * Tests {@link JsonSerializers.InterceptCallbackResponseSerializer} omits fields that are empty
   * or at default values.
   */
  @Test
  public void interceptCallbackResponseSerializer_emptyFields_omitted() throws Exception {
    InterceptCallbackResponseMessage msg = new InterceptCallbackResponseMessage();
    msg.callbackId = "cb-456";

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("callback_id").getAsString(), is("cb-456"));
    assertThat(parsed.has("phase"), is(false));
    assertThat(parsed.has("mutated_args"), is(false));
    assertThat(parsed.has("should_proceed"), is(false));
    assertThat(parsed.has("override_return"), is(false));
    assertThat(parsed.has("new_return_value"), is(false));
    assertThat(parsed.has("new_return_ref"), is(false));
    assertThat(parsed.has("throw_exception"), is(false));
    assertThat(parsed.has("exception"), is(false));
    assertThat(parsed.has("is_api_misuse_error"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.InterceptKeyMessageSerializer} with all fields set on an {@link
   * InterceptKeyMessage}.
   */
  @Test
  public void interceptKeyMessageSerializer_serializesAllFields() throws Exception {
    InterceptKeyMessage msg = new InterceptKeyMessage();
    msg.clazz = "com.example.Target";
    msg.execMsgType = MessageType.EXEC_INSTANCE_METHOD.getId();
    msg.executableName = "doWork";
    msg.parameterTypes = new String[] {"int", "java.lang.String"};

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("class").getAsString(), is("com.example.Target"));
    assertThat(parsed.get("exec_message_type").getAsString(), is("EXEC_INSTANCE_METHOD"));
    assertThat(parsed.get("executable_name").getAsString(), is("doWork"));
    assertThat(parsed.has("parameter_types"), is(true));
    assertThat(parsed.getAsJsonArray("parameter_types").size(), is(2));
  }

  /**
   * Tests {@link JsonSerializers.InterceptKeyMessageSerializer} omits fields that are empty or at
   * default values.
   */
  @Test
  public void interceptKeyMessageSerializer_emptyFields_omitted() throws Exception {
    InterceptKeyMessage msg = new InterceptKeyMessage();
    msg.execMsgType = MessageType.EXEC_CONSTRUCTOR.getId();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("exec_message_type"), is(true));
    assertThat(parsed.get("exec_message_type").getAsString(), is("EXEC_CONSTRUCTOR"));
    assertThat(parsed.has("class"), is(false));
    assertThat(parsed.has("executable_name"), is(false));
    assertThat(parsed.has("parameter_types"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.InterceptResponseSerializer} with all fields set on an {@link
   * InterceptResponse}.
   */
  @Test
  public void interceptResponseSerializer_serializesAllFields() throws Exception {
    InterceptResponse msg = new InterceptResponse();
    msg.peerUuid =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-abc-123".getBytes(StandardCharsets.UTF_8)));
    msg.responseToId = "req-789";
    msg.result = true;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(
        parsed.get("peer_uuid").getAsString(),
        is(UUID.nameUUIDFromBytes("peer-abc-123".getBytes(StandardCharsets.UTF_8)).toString()));
    assertThat(parsed.get("response_to").getAsString(), is("req-789"));
    assertThat(parsed.get("result").getAsBoolean(), is(true));
  }

  /**
   * Tests {@link JsonSerializers.InterceptResponseSerializer} omits fields that are empty or at
   * default values.
   */
  @Test
  public void interceptResponseSerializer_emptyFields_omitted() throws Exception {
    InterceptResponse msg = new InterceptResponse();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("peer_uuid"), is(false));
    assertThat(parsed.has("response_to"), is(false));
    assertThat(parsed.has("result"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.InternalHeaderSerializer} with all fields set on an {@link
   * InternalHeader}.
   */
  @Test
  public void internalHeaderSerializer_serializesAllFields() throws Exception {
    InternalHeader msg = new InternalHeader();
    msg.headerType = InternalHeaderType.WRITE_AHEAD.toByte();
    msg.value = "header-value-123";

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("header_type").getAsString(), is("WRITE_AHEAD"));
    assertThat(parsed.get("value").getAsString(), is("header-value-123"));
  }

  /**
   * Tests {@link JsonSerializers.InternalHeaderSerializer} omits the value field when it is empty.
   */
  @Test
  public void internalHeaderSerializer_emptyValue_omitted() throws Exception {
    InternalHeader msg = new InternalHeader();
    msg.headerType = InternalHeaderType.WRITE_AHEAD.toByte();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("header_type").getAsString(), is("WRITE_AHEAD"));
    assertThat(parsed.has("value"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.MetaMessageSerializer} with all fields set on a {@link
   * MetaMessage}.
   */
  @Test
  public void metaMessageSerializer_serializesAllFields() throws Exception {
    MetaMessage msg = new MetaMessage();
    msg.fromPeer =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-uuid".getBytes(StandardCharsets.UTF_8)));
    msg.messageId = "msg-123";
    msg.responseToId = "resp-456";
    msg.service = MetaServiceType.FETCH_CLASSES_INFO.getId();
    Obj paramValue = new Obj();
    paramValue.value = "com.example.Foo";
    msg.params = new Obj[] {paramValue};
    msg.status = MetaStatusType.OK.getId();
    msg.body = "response body";

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(
        parsed.get("from_peer").getAsString(),
        is(UUID.nameUUIDFromBytes("peer-uuid".getBytes(StandardCharsets.UTF_8)).toString()));
    assertThat(parsed.get("message_id").getAsString(), is("msg-123"));
    assertThat(parsed.get("response_to").getAsString(), is("resp-456"));
    assertThat(parsed.has("service"), is(true));
    assertThat(parsed.has("params"), is(true));
    assertThat(parsed.get("status").getAsString(), is("OK"));
    assertThat(parsed.get("body").getAsString(), is("response body"));
  }

  /**
   * Tests {@link JsonSerializers.MetaMessageSerializer} omits fields that are empty or at default
   * values.
   */
  @Test
  public void metaMessageSerializer_emptyFields_omitted() throws Exception {
    MetaMessage msg = new MetaMessage();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("from_peer"), is(false));
    assertThat(parsed.has("message_id"), is(false));
    assertThat(parsed.has("response_to"), is(false));
    assertThat(parsed.has("service"), is(false));
    assertThat(parsed.has("params"), is(false));
    assertThat(parsed.has("status"), is(false));
    assertThat(parsed.has("body"), is(false));
  }

  /** Tests {@link JsonSerializers.MetaMessageSerializer} serializes the status as an enum name. */
  @Test
  public void metaMessageSerializer_withStatus_serializesStatusName() throws Exception {
    MetaMessage msg = new MetaMessage();
    msg.status = MetaStatusType.OK.getId();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("status").getAsString(), is("OK"));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping a {@link
   * ControlMessage}.
   */
  @Test
  public void messageSerializer_controlMessage_serializesCorrectly() throws Exception {
    ControlMessage ctrl = new ControlMessage();
    ctrl.fromPeer =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-1".getBytes(StandardCharsets.UTF_8)));
    ctrl.messageId = "ctrl-msg-1";
    ctrl.command = ControlCommandType.PING.getId();
    ctrl.status = ControlStatusType.OK.toId();

    Message msg = new Message();
    msg.messageType = MessageType.CONTROL_MESSAGE_REQUEST.getId();
    msg.controlMessage = ctrl;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("CONTROL_MESSAGE_REQUEST"));
    assertThat(parsed.has("control_message"), is(true));
    JsonObject ctrlJson = parsed.getAsJsonObject("control_message");
    assertThat(
        ctrlJson.get("from_peer").getAsString(),
        is(UUID.nameUUIDFromBytes("peer-1".getBytes(StandardCharsets.UTF_8)).toString()));
    assertThat(ctrlJson.get("command").getAsString(), is("PING"));
    assertThat(ctrlJson.get("status").getAsString(), is("OK"));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * ExecMessage}.
   */
  @Test
  public void messageSerializer_execMessage_serializesCorrectly() throws Exception {
    ConstructorCall ctorCall = new ConstructorCall();
    Class clazz = new Class();
    clazz.name = "com.example.Widget";
    ctorCall.clazz = clazz;

    ExecMessage exec = new ExecMessage();
    exec.peerUuid =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-exec-1".getBytes(StandardCharsets.UTF_8)));
    exec.constructorCall = ctorCall;

    Message msg = new Message();
    msg.messageType = MessageType.EXEC_CONSTRUCTOR.getId();
    msg.execMessage = exec;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("EXEC_CONSTRUCTOR"));
    assertThat(parsed.has("exec_message"), is(true));
    JsonObject execJson = parsed.getAsJsonObject("exec_message");
    assertThat(
        execJson.get("peer_uuid").getAsString(),
        is(UUID.nameUUIDFromBytes("peer-exec-1".getBytes(StandardCharsets.UTF_8)).toString()));
    assertThat(execJson.has("constructor_call"), is(true));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * InterceptMessage}.
   */
  @Test
  public void messageSerializer_interceptMessage_serializesCorrectly() throws Exception {
    InterceptMessage intercept = new InterceptMessage();
    intercept.peerUuid =
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("peer-intercept-1".getBytes(StandardCharsets.UTF_8)));
    intercept.messageId = "int-msg-1";
    intercept.interceptType = InterceptType.BEFORE.toByte();
    intercept.clazz = "com.example.Target";

    Message msg = new Message();
    msg.messageType = MessageType.INTERCEPT_MESSAGE.getId();
    msg.interceptMessage = intercept;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("INTERCEPT_MESSAGE"));
    assertThat(parsed.has("intercept_message"), is(true));
    JsonObject intJson = parsed.getAsJsonObject("intercept_message");
    assertThat(
        intJson.get("peer_uuid").getAsString(),
        is(UUID.nameUUIDFromBytes("peer-intercept-1".getBytes(StandardCharsets.UTF_8)).toString()));
    assertThat(intJson.get("intercept_type").getAsString(), is("BEFORE"));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * InterceptKeyMessage}.
   */
  @Test
  public void messageSerializer_interceptKey_serializesCorrectly() throws Exception {
    InterceptKeyMessage key = new InterceptKeyMessage();
    key.clazz = "com.example.Target";
    key.execMsgType = MessageType.EXEC_INSTANCE_METHOD.getId();
    key.executableName = "process";

    Message msg = new Message();
    msg.messageType = MessageType.INTERCEPT_KEY.getId();
    msg.interceptKeyMessage = key;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("INTERCEPT_KEY"));
    assertThat(parsed.has("intercept_key_message"), is(true));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * InterceptResponse}.
   */
  @Test
  public void messageSerializer_interceptResponse_serializesCorrectly() throws Exception {
    InterceptResponse resp = new InterceptResponse();
    resp.peerUuid =
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-resp-1".getBytes(StandardCharsets.UTF_8)));
    resp.responseToId = "req-1";
    resp.result = true;

    Message msg = new Message();
    msg.messageType = MessageType.INTERCEPT_RESPONSE.getId();
    msg.interceptResponse = resp;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("INTERCEPT_RESPONSE"));
    assertThat(parsed.has("intercept_response"), is(true));
    JsonObject respJson = parsed.getAsJsonObject("intercept_response");
    assertThat(respJson.get("result").getAsBoolean(), is(true));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * InterceptCallbackRequestMessage}.
   */
  @Test
  public void messageSerializer_interceptCallbackRequest_serializesCorrectly() throws Exception {
    InterceptCallbackRequestMessage cbReq = new InterceptCallbackRequestMessage();
    cbReq.callbackId = "cb-req-1";
    cbReq.phase = InterceptPhase.BEFORE.toByte();
    cbReq.interceptType = InterceptType.AROUND.toByte();
    cbReq.callbackClass = "com.example.Handler";
    cbReq.callbackMethod = "handle";

    Message msg = new Message();
    msg.messageType = MessageType.INTERCEPT_CALLBACK_REQUEST.getId();
    msg.interceptCallbackRequestMessage = cbReq;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("INTERCEPT_CALLBACK_REQUEST"));
    assertThat(parsed.has("intercept_callback_request"), is(true));
    JsonObject reqJson = parsed.getAsJsonObject("intercept_callback_request");
    assertThat(reqJson.get("callback_id").getAsString(), is("cb-req-1"));
    assertThat(reqJson.get("phase").getAsString(), is("BEFORE"));
    assertThat(reqJson.get("intercept_type").getAsString(), is("AROUND"));
  }

  /**
   * Tests {@link JsonSerializers.MessageSerializer} with a {@link Message} wrapping an {@link
   * InterceptCallbackResponseMessage}.
   */
  @Test
  public void messageSerializer_interceptCallbackResponse_serializesCorrectly() throws Exception {
    InterceptCallbackResponseMessage cbResp = new InterceptCallbackResponseMessage();
    cbResp.callbackId = "cb-resp-1";
    cbResp.shouldProceed = true;

    Message msg = new Message();
    msg.messageType = MessageType.INTERCEPT_CALLBACK_RESPONSE.getId();
    msg.interceptCallbackResponseMessage = cbResp;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("type").getAsString(), is("INTERCEPT_CALLBACK_RESPONSE"));
    assertThat(parsed.has("intercept_callback_response"), is(true));
    JsonObject respJson = parsed.getAsJsonObject("intercept_callback_response");
    assertThat(respJson.get("callback_id").getAsString(), is("cb-resp-1"));
  }

  /**
   * Tests {@link JsonSerializers.InstanceFieldPutDoneAdapter} deserializes JSON with all fields
   * into an {@link InstanceFieldPutDone}.
   */
  @Test
  public void instanceFieldPutDoneAdapter_deserialize_allFields() throws Exception {
    InstanceFieldPutDone original = new InstanceFieldPutDone();
    Field field = new Field();
    field.name = "myField";
    original.field = field;
    original.instanceFieldPutId = "ifp-123";

    String json = ColferUtils.toJson(original);

    Gson gson = createGsonWithAdapters();
    InstanceFieldPutDone deserialized = gson.fromJson(json, InstanceFieldPutDone.class);

    assertThat(deserialized.field, is(not(nullValue())));
    assertThat(deserialized.field.name, is("myField"));
    assertThat(deserialized.instanceFieldPutId, is("ifp-123"));
  }

  /**
   * Tests {@link JsonSerializers.InstanceFieldPutDoneAdapter} handles JSON with missing optional
   * fields.
   */
  @Test
  public void instanceFieldPutDoneAdapter_deserialize_missingFields() throws Exception {
    String json = "{\"instance_field_put_id\":\"ifp-456\"}";

    Gson gson = createGsonWithAdapters();
    InstanceFieldPutDone deserialized = gson.fromJson(json, InstanceFieldPutDone.class);

    assertThat(deserialized.field, is(nullValue()));
    assertThat(deserialized.instanceFieldPutId, is("ifp-456"));
  }

  /**
   * Tests {@link JsonSerializers.StaticFieldPutDoneAdapter} deserializes JSON with all fields into
   * a {@link StaticFieldPutDone}.
   */
  @Test
  public void staticFieldPutDoneAdapter_deserialize_allFields() throws Exception {
    StaticFieldPutDone original = new StaticFieldPutDone();
    Field field = new Field();
    field.name = "CONSTANT";
    original.field = field;
    original.staticFieldPutId = "sfp-789";

    String json = ColferUtils.toJson(original);

    Gson gson = createGsonWithAdapters();
    StaticFieldPutDone deserialized = gson.fromJson(json, StaticFieldPutDone.class);

    assertThat(deserialized.field, is(not(nullValue())));
    assertThat(deserialized.field.name, is("CONSTANT"));
    assertThat(deserialized.staticFieldPutId, is("sfp-789"));
  }

  /**
   * Tests {@link JsonSerializers.StaticFieldPutDoneAdapter} handles JSON with missing optional
   * fields.
   */
  @Test
  public void staticFieldPutDoneAdapter_deserialize_missingFields() throws Exception {
    String json = "{\"static_field_put_id\":\"sfp-000\"}";

    Gson gson = createGsonWithAdapters();
    StaticFieldPutDone deserialized = gson.fromJson(json, StaticFieldPutDone.class);

    assertThat(deserialized.field, is(nullValue()));
    assertThat(deserialized.staticFieldPutId, is("sfp-000"));
  }

  /**
   * Tests {@link JsonSerializers.ReturnValueAdapter} deserializes JSON with all fields into a
   * {@link ReturnValue}.
   */
  @Test
  public void returnValueAdapter_deserialize_allFields() throws Exception {
    ReturnValue original = new ReturnValue();
    Obj obj = new Obj();
    obj.value = "42";
    Class objClazz = new Class();
    objClazz.name = "java.lang.Integer";
    obj.clazz = objClazz;
    original.object = obj;
    Method method = new Method();
    method.name = "compute";
    Reflectable from = new Reflectable();
    from.method = method;
    original.from = from;

    String json = ColferUtils.toJson(original);

    Gson gson = createGsonWithAdapters();
    ReturnValue deserialized = gson.fromJson(json, ReturnValue.class);

    assertThat(deserialized.isVoid, is(false));
    assertThat(deserialized.object, is(not(nullValue())));
    assertThat(deserialized.from, is(not(nullValue())));
  }

  /**
   * Tests {@link JsonSerializers.ReturnValueAdapter} correctly deserializes a void return value.
   */
  @Test
  public void returnValueAdapter_deserialize_voidReturn() throws Exception {
    String json = "{\"void\":true}";

    Gson gson = createGsonWithAdapters();
    ReturnValue deserialized = gson.fromJson(json, ReturnValue.class);

    assertThat(deserialized.isVoid, is(true));
    assertThat(deserialized.object, is(nullValue()));
    assertThat(deserialized.from, is(nullValue()));
  }

  /** Tests {@link JsonSerializers.ExecMessageSerializer} serializes newly added fields. */
  @Test
  public void execMessageSerializer_serializesNewFields() throws Exception {
    ExecMessage exec = new ExecMessage();
    ConstructorCall ctorCall = new ConstructorCall();
    Class clazz = new Class();
    clazz.name = "com.example.Widget";
    ctorCall.clazz = clazz;
    exec.constructorCall = ctorCall;
    exec.declaredExceptions = new String[] {"java.io.IOException", "java.sql.SQLException"};
    exec.threadAffinity = "fx-thread";
    exec.entryPoint = true;

    String json = ColferUtils.toJson(exec);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("declared_exceptions"), is(true));
    assertThat(parsed.getAsJsonArray("declared_exceptions").size(), is(2));
    assertThat(parsed.get("thread_affinity").getAsString(), is("fx-thread"));
    assertThat(parsed.get("entry_point").getAsBoolean(), is(true));
  }

  /**
   * Tests {@link JsonSerializers.ExecMessageSerializer} omits newly added fields when at defaults.
   */
  @Test
  public void execMessageSerializer_newFields_omittedWhenDefault() throws Exception {
    ExecMessage exec = new ExecMessage();
    ConstructorCall ctorCall = new ConstructorCall();
    Class clazz = new Class();
    clazz.name = "com.example.Widget";
    ctorCall.clazz = clazz;
    exec.constructorCall = ctorCall;

    String json = ColferUtils.toJson(exec);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("declared_exceptions"), is(false));
    assertThat(parsed.has("thread_affinity"), is(false));
    assertThat(parsed.has("entry_point"), is(false));
  }

  /** Tests {@link JsonSerializers.InterceptMessageSerializer} serializes newly added fields. */
  @Test
  public void interceptMessageSerializer_serializesNewFields() throws Exception {
    InterceptMessage msg = new InterceptMessage();
    msg.interceptType = InterceptType.BEFORE.toByte();
    msg.forceImmediate = true;
    msg.exceptionPropagationPolicy = ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY.toByte();
    msg.checkedExceptionPolicy = CheckedExceptionPolicy.REJECT.toByte();
    msg.priority = 10;
    msg.ttlSeconds = 3600L;
    msg.callbackTimeoutMs = 5000L;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("force_immediate").getAsBoolean(), is(true));
    assertThat(
        parsed.get("exception_propagation_policy").getAsString(), is("PROPAGATE_EXPLICIT_ONLY"));
    assertThat(parsed.get("checked_exception_policy").getAsString(), is("REJECT"));
    assertThat(parsed.get("priority").getAsInt(), is(10));
    assertThat(parsed.get("ttl_seconds").getAsLong(), is(3600L));
    assertThat(parsed.get("callback_timeout_ms").getAsLong(), is(5000L));
  }

  /**
   * Tests {@link JsonSerializers.InterceptMessageSerializer} omits newly added fields when at
   * defaults.
   */
  @Test
  public void interceptMessageSerializer_newFields_omittedWhenDefault() throws Exception {
    InterceptMessage msg = new InterceptMessage();
    msg.interceptType = InterceptType.AFTER.toByte();

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("force_immediate"), is(false));
    assertThat(parsed.has("exception_propagation_policy"), is(false));
    assertThat(parsed.has("checked_exception_policy"), is(false));
    assertThat(parsed.has("priority"), is(false));
    assertThat(parsed.has("ttl_seconds"), is(false));
    assertThat(parsed.has("callback_timeout_ms"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.InterceptMessageSerializer} omits policy fields when set to the
   * 0xFF null sentinel (255 = defer to global).
   */
  @Test
  public void interceptMessageSerializer_policySentinel_omitted() throws Exception {
    InterceptMessage msg = new InterceptMessage();
    msg.interceptType = InterceptType.BEFORE.toByte();
    msg.exceptionPropagationPolicy = (byte) 0xFF;
    msg.checkedExceptionPolicy = (byte) 0xFF;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("exception_propagation_policy"), is(false));
    assertThat(parsed.has("checked_exception_policy"), is(false));
  }

  /**
   * Tests {@link JsonSerializers.InterceptCallbackRequestSerializer} serializes the
   * proceedTimeoutMs field.
   */
  @Test
  public void interceptCallbackRequestSerializer_serializesProceedTimeoutMs() throws Exception {
    InterceptCallbackRequestMessage msg = new InterceptCallbackRequestMessage();
    msg.callbackId = "cb-timeout-1";
    msg.proceedTimeoutMs = 10000;

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.get("proceed_timeout_ms").getAsInt(), is(10000));
  }

  /**
   * Tests {@link JsonSerializers.InterceptCallbackRequestSerializer} omits proceedTimeoutMs when at
   * default (zero).
   */
  @Test
  public void interceptCallbackRequestSerializer_proceedTimeoutMs_omittedWhenDefault()
      throws Exception {
    InterceptCallbackRequestMessage msg = new InterceptCallbackRequestMessage();
    msg.callbackId = "cb-timeout-2";

    String json = ColferUtils.toJson(msg);
    JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

    assertThat(parsed.has("proceed_timeout_ms"), is(false));
  }

  /**
   * Creates a {@link Gson} instance with adapter classes registered, matching the configuration in
   * {@link ColferUtils}.
   */
  private Gson createGsonWithAdapters() {
    return new GsonBuilder()
        .registerTypeAdapter(
            InstanceFieldPutDone.class, new JsonSerializers.InstanceFieldPutDoneAdapter())
        .registerTypeAdapter(
            StaticFieldPutDone.class, new JsonSerializers.StaticFieldPutDoneAdapter())
        .registerTypeAdapter(ReturnValue.class, new JsonSerializers.ReturnValueAdapter())
        .registerTypeAdapter(Class.class, new JsonSerializers.ClassSerializer())
        .registerTypeAdapter(Field.class, new JsonSerializers.FieldSerializer())
        .registerTypeAdapter(Obj.class, new JsonSerializers.ObjSerializer())
        .registerTypeAdapter(Method.class, new JsonSerializers.MethodSerializer())
        .registerTypeAdapter(Constructor.class, new JsonSerializers.ConstructorSerializer())
        .registerTypeAdapter(Reflectable.class, new JsonSerializers.ReflectableSerializer())
        .create();
  }
}
