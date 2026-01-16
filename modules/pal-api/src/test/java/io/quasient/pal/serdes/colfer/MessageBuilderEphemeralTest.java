/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static io.quasient.pal.messages.types.MessageType.*;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static org.junit.Assert.*;

import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.lang.reflect.MethodSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Additional focused tests for the ephemeral (hot path) builders in MessageBuilder. These use the
 * TlScratchHolder-backed path and validate that message fields are populated correctly without
 * allocating new message beans.
 */
public class MessageBuilderEphemeralTest {

  static class TargetType {
    int f;
    static String SF;

    TargetType() {}

    TargetType(String s, int n) {}

    int add(int a, int b) {
      return a + b;
    }

    static void sm(String a, boolean b) {}
  }

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder builder;

  @Before
  public void setUp() {
    // includeSourceContext=true to exercise context population branches
    builder = new MessageBuilder(peerId, Boolean.toString(true));
  }

  private static Context ctxForMethod(Class<?> c, String name, Class<?>... argTypes)
      throws Exception {
    MethodSignature ms = new MethodSignature(c.getDeclaredMethod(name, argTypes));
    return new Context("MessageBuilderEphemeralTest.java", 2, c, ms);
  }

  private static Context ctxForField(Class<?> c, String field) throws Exception {
    FieldSignature fs = new FieldSignature(c.getDeclaredField(field));
    return new Context("MessageBuilderEphemeralTest.java", 3, c, fs);
  }

  @Test
  public void classMethod_ephemeral_buildsWithContextAndParams() throws Exception {
    Context ctx = ctxForMethod(TargetType.class, "sm", String.class, boolean.class);
    Object[] args = new Object[] {"x", true};
    ObjectRef[] argRefs = new ObjectRef[] {null, null};

    ExecMessage m =
        builder.buildClassMethodMessageEphemeral(ctx, this, ObjectRef.randomRef(), args, argRefs);

    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(m));
    assertEquals(peerId.toString(), m.getPeerUuid());
    assertNotNull(m.getClassMethodCall().getContext());
    assertEquals(TargetType.class.getName(), m.getClassMethodCall().getClazz().getName());
    assertEquals("sm", m.getClassMethodCall().getName());
    Parameter[] ps = m.getClassMethodCall().getParameters();
    assertEquals(2, ps.length);
    assertEquals("java.lang.String", ps[0].getValue().getClazz().getName());
    assertEquals("boolean", ps[1].getValue().getClazz().getName());
  }

  @Test
  public void classMethod_ephemeral_nullArgs_parametersNull() throws Exception {
    Context ctx = ctxForMethod(TargetType.class, "sm", String.class, boolean.class);
    ExecMessage m =
        builder.buildClassMethodMessageEphemeral(ctx, this, ObjectRef.randomRef(), null, null);
    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(m));
    assertNotNull(m.getClassMethodCall().getContext());
    assertNotNull(m.getClassMethodCall().getParameters());
    assertEquals(0, m.getClassMethodCall().getParameters().length);
  }

  @Test
  public void instanceMethod_ephemeral_buildsWithTargetRef() throws Exception {
    Context ctx = ctxForMethod(TargetType.class, "add", int.class, int.class);
    ObjectRef targetRef = ObjectRef.randomRef();
    Object[] args = new Object[] {1, 2};
    ObjectRef[] argRefs = new ObjectRef[] {null, null};

    ExecMessage m =
        builder.buildInstanceMethodMessageEphemeral(
            ctx, this, ObjectRef.randomRef(), targetRef, args, argRefs);

    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(m));
    assertEquals(targetRef.getRef(), m.getInstanceMethodCall().getObjectRef());
    assertEquals("add", m.getInstanceMethodCall().getName());
    assertEquals(TargetType.class.getName(), m.getInstanceMethodCall().getClazz().getName());
  }

  @Test
  public void instanceMethod_ephemeral_nullArgs_parametersNull() throws Exception {
    Context ctx = ctxForMethod(TargetType.class, "add", int.class, int.class);
    ExecMessage m =
        builder.buildInstanceMethodMessageEphemeral(
            ctx, this, ObjectRef.randomRef(), ObjectRef.randomRef(), null, null);
    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(m));
    assertNotNull(m.getInstanceMethodCall().getContext());
    assertNotNull(m.getInstanceMethodCall().getParameters());
    assertEquals(0, m.getInstanceMethodCall().getParameters().length);
  }

  @Test
  public void fieldOps_ephemeral_allFourKinds() throws Exception {
    Context fctx = ctxForField(TargetType.class, "f");
    ObjectRef targetRef = ObjectRef.randomRef();

    // GET_FIELD
    ExecMessage getField =
        builder.buildFieldOpEphemeral(
            fctx, EXEC_GET_FIELD, this, ObjectRef.randomRef(), targetRef, null, null);
    assertEquals(EXEC_GET_FIELD, getMessageTypeOf(getField));
    assertEquals(targetRef.getRef(), getField.getInstanceFieldGet().getObjectRef());

    // PUT_FIELD
    ExecMessage putField =
        builder.buildFieldOpEphemeral(
            fctx, EXEC_PUT_FIELD, this, ObjectRef.randomRef(), targetRef, 7, null);
    assertEquals(EXEC_PUT_FIELD, getMessageTypeOf(putField));
    assertEquals(targetRef.getRef(), putField.getInstanceFieldPut().getObjectRef());
    // PUT_FIELD with objectRef arg
    ObjectRef vRef = ObjectRef.randomRef();
    ExecMessage putFieldRef =
        builder.buildFieldOpEphemeral(
            fctx, EXEC_PUT_FIELD, this, ObjectRef.randomRef(), targetRef, null, vRef);
    assertEquals(EXEC_PUT_FIELD, getMessageTypeOf(putFieldRef));
    assertEquals(vRef.getRef(), putFieldRef.getInstanceFieldPut().getValueObject().getRef());

    // GET_STATIC
    Context sfctx = ctxForField(TargetType.class, "SF");
    ExecMessage getStatic =
        builder.buildFieldOpEphemeral(
            sfctx, EXEC_GET_STATIC, this, ObjectRef.randomRef(), null, null, null);
    assertEquals(EXEC_GET_STATIC, getMessageTypeOf(getStatic));
    assertEquals(TargetType.class.getName(), getStatic.getStaticFieldGet().getClazz().getName());

    // PUT_STATIC
    ExecMessage putStatic =
        builder.buildFieldOpEphemeral(
            sfctx, EXEC_PUT_STATIC, this, ObjectRef.randomRef(), null, "v", null);
    assertEquals(EXEC_PUT_STATIC, getMessageTypeOf(putStatic));
    assertEquals(TargetType.class.getName(), putStatic.getStaticFieldPut().getClazz().getName());
  }

  @Test
  public void returnValue_ephemeral_fromMethod_and_fromField() throws Exception {
    Method m = TargetType.class.getDeclaredMethod("add", int.class, int.class);
    Field f = TargetType.class.getDeclaredField("f");
    ObjectRef ref = ObjectRef.randomRef();
    String rid = UUID.randomUUID().toString();

    ExecMessage fromMethod = builder.buildReturnValueEphemeral(3, m, ref, false, rid);
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(fromMethod));
    assertEquals(rid, fromMethod.getResponseToId());
    assertEquals("int", fromMethod.getReturnValue().getObject().getClazz().getName());
    assertEquals("add", fromMethod.getReturnValue().getFrom().getMethod().getName());

    ExecMessage fromField = builder.buildReturnValueEphemeral(1, f, ref, false, rid);
    assertEquals("int", fromField.getReturnValue().getObject().getClazz().getName());
    assertEquals("f", fromField.getReturnValue().getFrom().getField().getName());
  }

  @Test
  public void returnValue_ephemeral_nullResponseId_keepsDefaultEmpty() throws Exception {
    Method m = TargetType.class.getDeclaredMethod("add", int.class, int.class);
    ObjectRef ref = ObjectRef.randomRef();
    ExecMessage em = builder.buildReturnValueEphemeral(3, m, ref, false, null);
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(em));
    assertEquals("", em.getResponseToId());
  }

  @Test
  public void returnValue_ephemeral_fromConstructor_and_emptyResponseId() throws Exception {
    var ctor = TargetType.class.getDeclaredConstructor(String.class, int.class);
    ObjectRef ref = ObjectRef.randomRef();
    // empty responseToId should not be set on ExecMessage
    ExecMessage m = builder.buildReturnValueEphemeral(new TargetType(), ctor, ref, false, "");
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(m));
    assertEquals("", m.getResponseToId());
    assertEquals(
        TargetType.class.getName(),
        m.getReturnValue().getFrom().getConstructor().getClazz().getName());
  }

  @Test
  public void fieldOps_ephemeral_putStatic_withObjectRefArg() throws Exception {
    Context sfctx = ctxForField(TargetType.class, "SF");
    ObjectRef valRef = ObjectRef.randomRef();
    ExecMessage putStaticRef =
        builder.buildFieldOpEphemeral(
            sfctx, EXEC_PUT_STATIC, this, ObjectRef.randomRef(), null, null, valRef);
    assertEquals(EXEC_PUT_STATIC, getMessageTypeOf(putStaticRef));
    // In the ephemeral path, valueObject is always present; when using a ref, it carries the ref
    assertNotNull(putStaticRef.getStaticFieldPut().getValueObject());
    assertEquals(valRef.getRef(), putStaticRef.getStaticFieldPut().getValueObject().getRef());
  }

  @Test
  public void ephemeral_builders_withZeroArgs_and_withoutContext() throws Exception {
    // use a builder with includeSourceContext=false to exercise that branch
    MessageBuilder bNoCtx = new MessageBuilder(peerId, Boolean.toString(false));
    // class method with zero args
    Context cm = ctxForMethod(TargetType.class, "sm", String.class, boolean.class);
    ExecMessage m1 =
        bNoCtx.buildClassMethodMessageEphemeral(
            cm, this, ObjectRef.randomRef(), new Object[0], new ObjectRef[0]);
    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(m1));
    assertEquals(0, m1.getClassMethodCall().getParameters().length);

    // instance method zero args
    Context im = ctxForMethod(TargetType.class, "add", int.class, int.class);
    ExecMessage m2 =
        bNoCtx.buildInstanceMethodMessageEphemeral(
            im,
            this,
            ObjectRef.randomRef(),
            ObjectRef.randomRef(),
            new Object[0],
            new ObjectRef[0]);
    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(m2));
    assertEquals(0, m2.getInstanceMethodCall().getParameters().length);
  }

  @Test
  public void throwable_ephemeral_fromMethod_setsModifiersAndFrom() throws Exception {
    Method m = TargetType.class.getDeclaredMethod("add", int.class, int.class);
    String rid = UUID.randomUUID().toString();
    RuntimeException ex = new RuntimeException("boom");

    ExecMessage em = builder.buildAccessibleObjectThrowableEphemeral(m, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    assertEquals(rid, em.getResponseToId());
    assertNotNull(em.getRaisedThrowable().getFrom().getMethod());
    assertEquals("add", em.getRaisedThrowable().getFrom().getMethod().getName());
    assertEquals(m.getModifiers(), em.getRaisedThrowable().getModifiers());
  }

  @Test
  public void throwable_ephemeral_fromField_setsModifiersAndFrom() throws Exception {
    Field f = TargetType.class.getDeclaredField("f");
    String rid = UUID.randomUUID().toString();
    IllegalStateException ex = new IllegalStateException("nope");

    ExecMessage em = builder.buildAccessibleObjectThrowableEphemeral(f, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    assertEquals(rid, em.getResponseToId());
    assertNotNull(em.getRaisedThrowable().getFrom().getField());
    assertEquals("f", em.getRaisedThrowable().getFrom().getField().getName());
    assertEquals(f.getModifiers(), em.getRaisedThrowable().getModifiers());
  }

  @Test
  public void throwable_ephemeral_fromConstructor_setsModifiersAndFrom() throws Exception {
    var ctor = TargetType.class.getDeclaredConstructor(String.class, int.class);
    String rid = UUID.randomUUID().toString();
    RuntimeException ex = new RuntimeException("boom");

    ExecMessage em = builder.buildAccessibleObjectThrowableEphemeral(ctor, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    assertEquals(rid, em.getResponseToId());
    assertNotNull(em.getRaisedThrowable().getFrom().getConstructor());
    assertEquals(
        TargetType.class.getName(),
        em.getRaisedThrowable().getFrom().getConstructor().getClazz().getName());
    assertEquals(ctor.getModifiers(), em.getRaisedThrowable().getModifiers());
  }

  @Test
  public void throwable_ephemeral_nullAccessible_setsNoFromAndZeroModifiers() {
    String rid = UUID.randomUUID().toString();
    IllegalArgumentException ex = new IllegalArgumentException("bad");

    ExecMessage em = builder.buildAccessibleObjectThrowableEphemeral(null, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    assertEquals(rid, em.getResponseToId());
    assertNull(em.getRaisedThrowable().getFrom());
    assertEquals(0, em.getRaisedThrowable().getModifiers());
  }

  @Test
  public void fieldOpDone_ephemeral_putFieldAndStaticDone() throws Exception {
    Field f = TargetType.class.getDeclaredField("f");
    Context ctx = ctxForField(TargetType.class, "f");

    ExecMessage ifpd = builder.buildFieldOpDoneEphemeral(f, ctx, EXEC_PUT_FIELD_DONE);
    assertEquals(EXEC_PUT_FIELD_DONE, getMessageTypeOf(ifpd));
    assertEquals("f", ifpd.getInstanceFieldPutDone().getField().getName());

    ExecMessage sfpd = builder.buildFieldOpDoneEphemeral(f, ctx, EXEC_PUT_STATIC_DONE);
    assertEquals(EXEC_PUT_STATIC_DONE, getMessageTypeOf(sfpd));
    assertEquals("f", sfpd.getStaticFieldPutDone().getField().getName());
  }
}
