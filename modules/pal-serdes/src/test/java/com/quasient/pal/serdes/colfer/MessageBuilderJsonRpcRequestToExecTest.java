/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers the private JSON-RPC mapping helpers in MessageBuilder by driving them through the public
 * jsonRpcRequestToExecMessage(...) entry point.
 */
public class MessageBuilderJsonRpcRequestToExecTest {

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder builder;

  @Before
  public void setUp() {
    builder = new MessageBuilder(peerId);
  }

  @Test
  public void jsonRpc_constructor_noArgs_mapsToConstructorCallWithNoParams() {
    String type = "java.lang.String";
    JsonRpcRequest req = JsonRpcMessageFactory.buildConstructorCall("rid-1", type, null);

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_CONSTRUCTOR, getMessageTypeOf(em));
    assertEquals("rid-1", em.getMessageId());
    assertEquals(peerId.toString(), em.getPeerUuid());
    assertEquals(type, em.getConstructorCall().getClazz().getName());
    Parameter[] ps = em.getConstructorCall().getParameters();
    assertNotNull(ps);
    assertEquals(0, ps.length);
  }

  @Test
  public void jsonRpc_instanceMethod_valueAndRefArgs_mapsToInstanceMethodCall() {
    String type = "java.util.List";
    String method = "add";
    ObjectRef instance = ObjectRef.randomRef();
    // two args: by-value then by-ref
    Argument a1 = Argument.builder().withName("e").withType("int").withValue(7).build();
    ObjectRef ref = ObjectRef.randomRef();
    Argument a2 = Argument.builder().withName("o").withRef(ref.getRef()).build();
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            "rid-2", type, method, instance, List.of(a1, a2));

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_INSTANCE_METHOD, getMessageTypeOf(em));
    assertEquals(type, em.getInstanceMethodCall().getClazz().getName());
    assertEquals(method, em.getInstanceMethodCall().getName());
    assertEquals(instance.getRef(), em.getInstanceMethodCall().getObjectRef());
    Parameter[] ps = em.getInstanceMethodCall().getParameters();
    assertEquals(2, ps.length);
    // names preserved
    assertEquals("e", ps[0].getName());
    assertEquals("o", ps[1].getName());
    // first arg by value
    assertEquals("int", ps[0].getValue().getClazz().getName());
    // second arg by ref
    assertEquals(ref.getRef(), ps[1].getValue().getRef());
    // parameter types: by-value retains type, by-ref yields null in utils mapping
    var types = ExecMessageUtils.getParameterTypes(em);
    assertNotNull(types);
    assertEquals(2, types.size());
    // first one is declared type, second one is null (no clazz set for JSON-RPC ref arg)
    assertEquals("int", types.get(0));
    org.junit.Assert.assertNull(types.get(1));
  }

  @Test
  public void jsonRpc_classMethod_valueArgs_mapsToClassMethodCall() {
    String type = "java.util.Arrays";
    String method = "binarySearch";
    Argument a1 =
        Argument.builder().withName("arr").withType("[F").withValue(new float[] {1f}).build();
    Argument a2 = Argument.builder().withName("key").withType("float").withValue(1f).build();
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildClassMethodCall("rid-3", type, method, List.of(a1, a2));

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_CLASS_METHOD, getMessageTypeOf(em));
    assertEquals(type, em.getClassMethodCall().getClazz().getName());
    assertEquals(method, em.getClassMethodCall().getName());
    Parameter[] ps = em.getClassMethodCall().getParameters();
    assertEquals(2, ps.length);
    assertEquals("[F", ps[0].getValue().getClazz().getName());
    assertEquals("float", ps[1].getValue().getClazz().getName());
  }

  @Test
  public void jsonRpc_instanceFieldPut_valueArg_mapsToInstanceFieldPut() {
    String type = "com.example.Foo";
    String field = "bar";
    ObjectRef instance = ObjectRef.randomRef();
    Argument value = Argument.builder().withType("int").withValue(42).build();
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildInstanceFieldPut("rid-4", type, instance, field, value);

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_PUT_FIELD, getMessageTypeOf(em));
    assertEquals(type, em.getInstanceFieldPut().getClazz().getName());
    assertEquals(field, em.getInstanceFieldPut().getField().getName());
    assertEquals(instance.getRef(), em.getInstanceFieldPut().getObjectRef());
    assertEquals("int", em.getInstanceFieldPut().getValueObject().getClazz().getName());
  }

  @Test
  public void jsonRpc_staticFieldPut_refArg_mapsToStaticFieldPut() {
    String type = "com.example.Foo";
    String field = "BAR";
    ObjectRef ref = ObjectRef.randomRef();
    Argument value = Argument.builder().withRef(ref.getRef()).build();
    JsonRpcRequest req = JsonRpcMessageFactory.buildStaticFieldPut("rid-5", type, field, value);

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_PUT_STATIC, getMessageTypeOf(em));
    assertEquals(type, em.getStaticFieldPut().getClazz().getName());
    assertEquals(field, em.getStaticFieldPut().getField().getName());
    assertNull(em.getStaticFieldPut().getValueObject());
    assertEquals(ref.getRef(), em.getStaticFieldPut().getValueObjectRef());
  }

  @Test
  public void jsonRpc_staticFieldGet_mapsToStaticFieldGet() {
    String type = "com.example.Foo";
    String field = "BAR";
    JsonRpcRequest req = JsonRpcMessageFactory.buildStaticFieldGet("rid-6", type, field);

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_GET_STATIC, getMessageTypeOf(em));
    assertEquals(type, em.getStaticFieldGet().getClazz().getName());
    assertEquals(field, em.getStaticFieldGet().getField().getName());
  }

  @Test
  public void jsonRpc_instanceFieldGet_mapsToInstanceFieldGet() {
    String type = "com.example.Foo";
    String field = "bar";
    ObjectRef instance = ObjectRef.randomRef();
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildInstanceFieldGet("rid-7", type, instance, field);

    var msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();
    assertEquals(MessageType.EXEC_GET_FIELD, getMessageTypeOf(em));
    assertEquals(type, em.getInstanceFieldGet().getClazz().getName());
    assertEquals(field, em.getInstanceFieldGet().getField().getName());
    assertEquals(instance.getRef(), em.getInstanceFieldGet().getObjectRef());
  }
}
