/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import io.quasient.pal.messages.colfer.Throwable;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import org.junit.Test;

/**
 * Unit tests for {@code WalEntry} — the model that wraps a deserialized {@code ExecMessage} with
 * indexed metadata (offset, messageType, threadName, builderSeq, className, executableName,
 * paramTypes, objectRef, kind).
 *
 * <p>Each test constructs a synthetic {@code ExecMessage} via direct Colfer bean construction,
 * passes it to {@code WalEntry.fromExecMessage(long, ExecMessage)}, and verifies the extracted
 * fields.
 */
public class WalEntryTest {

  /**
   * Verifies that an instance method ExecMessage is correctly parsed into a WalEntry with all
   * fields extracted.
   */
  @Test
  public void fromExecMessage_instanceMethod() {
    // Given: An ExecMessage with instanceMethodCall set
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(42);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("add");
    imc.setObjectRef(7);
    Class clazz = new Class();
    clazz.setName("com.example.Calculator");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    // When: WalEntry.fromExecMessage(0L, msg) is called
    WalEntry entry = WalEntry.fromExecMessage(0L, msg);

    // Then: all fields extracted correctly
    assertThat(entry.getOffset(), is(0L));
    assertThat(entry.getKind(), is(WalEntryKind.OPERATION));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_INSTANCE_METHOD));
    assertThat(entry.getClassName(), is("com.example.Calculator"));
    assertThat(entry.getExecutableName(), is("add"));
    assertThat(entry.getThreadName(), is("self-caller"));
    assertThat(entry.getBuilderSeq(), is(42));
    assertThat(entry.getObjectRef(), is(7));
    assertThat(entry.getParamTypes(), is(notNullValue()));
    assertThat(entry.getParamTypes(), is(Collections.emptyList()));
    assertThat(entry.getRawMessage(), is(sameInstance(msg)));
  }

  /** Verifies that a return value ExecMessage is classified as COMPLETION with correct offset. */
  @Test
  public void fromExecMessage_returnValue() {
    // Given: An ExecMessage with returnValue set
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(10);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    // When: WalEntry.fromExecMessage(5L, msg) is called
    WalEntry entry = WalEntry.fromExecMessage(5L, msg);

    // Then: offset=5, kind=COMPLETION, rawMessage preserved
    assertThat(entry.getOffset(), is(5L));
    assertThat(entry.getKind(), is(WalEntryKind.COMPLETION));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_RETURN_VALUE));
    assertThat(entry.getClassName(), is("void"));
    assertThat(entry.getExecutableName(), is(nullValue()));
    assertThat(entry.getParamTypes(), is(nullValue()));
    assertThat(entry.getObjectRef(), is(0));
    assertThat(entry.getRawMessage(), is(sameInstance(msg)));
  }

  /** Verifies that a constructor ExecMessage yields kind=OPERATION and executableName='new'. */
  @Test
  public void fromExecMessage_constructor() {
    // Given: An ExecMessage with constructorCall set
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    ConstructorCall cc = new ConstructorCall();
    Class clazz = new Class();
    clazz.setName("com.example.Widget");
    cc.setClazz(clazz);
    msg.setConstructorCall(cc);

    // When: WalEntry.fromExecMessage() is called
    WalEntry entry = WalEntry.fromExecMessage(3L, msg);

    // Then: kind=OPERATION, executableName='new'
    assertThat(entry.getKind(), is(WalEntryKind.OPERATION));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_CONSTRUCTOR));
    assertThat(entry.getExecutableName(), is("new"));
    assertThat(entry.getClassName(), is("com.example.Widget"));
    assertThat(entry.getObjectRef(), is(0));
    assertThat(entry.getParamTypes(), is(notNullValue()));
  }

  /** Verifies that a throwable ExecMessage is classified as COMPLETION. */
  @Test
  public void fromExecMessage_throwable() {
    // Given: An ExecMessage with raisedThrowable set
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(5);

    RaisedThrowable rt = new RaisedThrowable();
    Throwable t = new Throwable();
    t.setType("java.lang.RuntimeException");
    t.setMessage("test error");
    rt.setThrowable(t);
    msg.setRaisedThrowable(rt);

    // When: WalEntry.fromExecMessage() is called
    WalEntry entry = WalEntry.fromExecMessage(8L, msg);

    // Then: kind=COMPLETION
    assertThat(entry.getKind(), is(WalEntryKind.COMPLETION));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_THROWABLE));
    assertThat(entry.getClassName(), is("java.lang.RuntimeException"));
    assertThat(entry.getObjectRef(), is(0));
  }

  /** Verifies that a putStaticDone ExecMessage is classified as COMPLETION. */
  @Test
  public void fromExecMessage_putStaticDone() {
    // Given: An ExecMessage with staticFieldPutDone set
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(3);

    StaticFieldPutDone spd = new StaticFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Config");
    spd.setClazz(clazz);
    Field field = new Field();
    field.setName("MAX_SIZE");
    spd.setField(field);
    msg.setStaticFieldPutDone(spd);

    // When: WalEntry.fromExecMessage() is called
    WalEntry entry = WalEntry.fromExecMessage(12L, msg);

    // Then: kind=COMPLETION, executableName from getFromExecutableName
    assertThat(entry.getKind(), is(WalEntryKind.COMPLETION));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_PUT_STATIC_DONE));
    assertThat(entry.getClassName(), is("com.example.Config"));
    assertThat(entry.getExecutableName(), is("MAX_SIZE"));
    assertThat(entry.getObjectRef(), is(0));
  }

  /**
   * Verifies that {@code WalEntry} correctly extracts {@code entryPoint = true} from an {@code
   * ExecMessage} that has the entry-point marker set.
   */
  @Test
  public void fromExecMessage_extractsEntryPointTrue() {
    // Given: ExecMessage with entryPoint = true and an instance method call
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("rpc-worker-1");
    msg.setBuilderSeq(1);
    msg.setEntryPoint(true);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("handle");
    imc.setObjectRef(3);
    Class clazz = new Class();
    clazz.setName("com.example.Handler");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    // When: WalEntry.fromExecMessage(offset, msg) is called
    WalEntry entry = WalEntry.fromExecMessage(10L, msg);

    // Then: entry.isEntryPoint() returns true
    assertThat(entry.isEntryPoint(), is(true));
    assertThat(entry.getKind(), is(WalEntryKind.OPERATION));
  }

  /**
   * Verifies that {@code WalEntry} defaults {@code entryPoint} to {@code false} when the {@code
   * ExecMessage} does not explicitly set it.
   */
  @Test
  public void fromExecMessage_extractsEntryPointFalseByDefault() {
    // Given: ExecMessage with entryPoint not explicitly set (default false)
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("compute");
    Class clazz = new Class();
    clazz.setName("com.example.Service");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    // When: WalEntry.fromExecMessage(offset, msg) is called
    WalEntry entry = WalEntry.fromExecMessage(20L, msg);

    // Then: entry.isEntryPoint() returns false
    assertThat(entry.isEntryPoint(), is(false));
  }

  /**
   * Verifies that the entry-point marker is preserved on COMPLETION kind entries (e.g., return
   * values) so that completions of entry-point operations can be identified.
   */
  @Test
  public void fromExecMessage_entryPointOnCompletion() {
    // Given: ExecMessage with entryPoint = true and a return value (COMPLETION kind)
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("rpc-worker-1");
    msg.setBuilderSeq(2);
    msg.setEntryPoint(true);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    // When: WalEntry.fromExecMessage(offset, msg) is called
    WalEntry entry = WalEntry.fromExecMessage(15L, msg);

    // Then: entry.isEntryPoint() returns true (marker preserved on completions)
    assertThat(entry.isEntryPoint(), is(true));
    assertThat(entry.getKind(), is(WalEntryKind.COMPLETION));
  }
}
