/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import java.util.Arrays;
import org.junit.Test;

/**
 * Unit tests for {@code OperationSignature} — the value type used to match live execution against
 * the WAL oracle by comparing class name, method name, parameter types, and message type.
 */
public class OperationSignatureTest {

  /** Verifies that two signatures with identical fields match. */
  @Test
  public void matchesSameSignature() {
    OperationSignature sig1 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature sig2 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);

    assertThat(sig1.matches(sig2), is(true));
  }

  /** Verifies that signatures differing only in className do not match. */
  @Test
  public void doesNotMatchDifferentClass() {
    OperationSignature sig1 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature sig2 =
        new OperationSignature(
            "com.example.OtherClass",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);

    assertThat(sig1.matches(sig2), is(false));
  }

  /** Verifies that signatures differing only in executableName do not match. */
  @Test
  public void doesNotMatchDifferentMethod() {
    OperationSignature sig1 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature sig2 =
        new OperationSignature(
            "com.example.Calculator",
            "subtract",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);

    assertThat(sig1.matches(sig2), is(false));
  }

  /** Verifies that signatures differing only in paramTypes do not match. */
  @Test
  public void doesNotMatchDifferentParams() {
    OperationSignature sig1 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("int", "int"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature sig2 =
        new OperationSignature(
            "com.example.Calculator",
            "add",
            Arrays.asList("double", "double"),
            MessageType.EXEC_INSTANCE_METHOD);

    assertThat(sig1.matches(sig2), is(false));
  }

  /** Verifies that {@code fromWalEntry} correctly extracts all fields from a WalEntry. */
  @Test
  public void fromWalEntryExtractsCorrectly() {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("add");
    imc.setObjectRef(7);
    Class clazz = new Class();
    clazz.setName("com.example.Calculator");
    imc.setClazz(clazz);
    Parameter p1 = new Parameter();
    Obj obj1 = new Obj();
    Class intClass1 = new Class();
    intClass1.setName("int");
    obj1.setClazz(intClass1);
    p1.setValue(obj1);
    Parameter p2 = new Parameter();
    Obj obj2 = new Obj();
    Class intClass2 = new Class();
    intClass2.setName("int");
    obj2.setClazz(intClass2);
    p2.setValue(obj2);
    imc.setParameters(new Parameter[] {p1, p2});
    msg.setInstanceMethodCall(imc);

    WalEntry entry = WalEntry.fromExecMessage(10L, msg);
    OperationSignature sig = OperationSignature.fromWalEntry(entry);

    assertThat(sig.className(), is("com.example.Calculator"));
    assertThat(sig.executableName(), is("add"));
    assertThat(sig.paramTypes(), is(Arrays.asList("int", "int")));
    assertThat(sig.messageType(), is(MessageType.EXEC_INSTANCE_METHOD));
  }
}
