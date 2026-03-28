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
package io.quasient.pal.core.replay;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
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

  /**
   * Verifies that lambda class names with different numbers and addresses still match.
   *
   * <p>Lambda classes have synthetic names like {@code Foo$$Lambda$653/0x00007fb994397710} where
   * the number (653) and memory address change between JVM runs. Replay must treat these as
   * equivalent.
   */
  @Test
  public void matchesLambdaClassesWithDifferentSuffixes() {
    OperationSignature recorded =
        new OperationSignature(
            "javafx.scene.control.Button",
            "setOnAction",
            Arrays.asList("com.example.Controller$$Lambda$653/0x00007fb994397710"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature replayed =
        new OperationSignature(
            "javafx.scene.control.Button",
            "setOnAction",
            Arrays.asList("com.example.Controller$$Lambda$562/0x00007fbd08449670"),
            MessageType.EXEC_INSTANCE_METHOD);

    assertThat(recorded.matches(replayed), is(true));
  }

  /** Verifies that lambda classes from different enclosing classes do not match. */
  @Test
  public void doesNotMatchLambdasFromDifferentClasses() {
    OperationSignature sig1 =
        new OperationSignature(
            "javafx.scene.control.Button",
            "setOnAction",
            Arrays.asList("com.example.ControllerA$$Lambda$100/0x123"),
            MessageType.EXEC_INSTANCE_METHOD);
    OperationSignature sig2 =
        new OperationSignature(
            "javafx.scene.control.Button",
            "setOnAction",
            Arrays.asList("com.example.ControllerB$$Lambda$200/0x456"),
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
    Obj obj1 = new Obj();
    Class intClass1 = new Class();
    intClass1.setName("int");
    obj1.setClazz(intClass1);
    Obj obj2 = new Obj();
    Class intClass2 = new Class();
    intClass2.setName("int");
    obj2.setClazz(intClass2);
    imc.setArgs(new Obj[] {obj1, obj2});
    msg.setInstanceMethodCall(imc);

    WalEntry entry = WalEntry.fromExecMessage(10L, msg);
    OperationSignature sig = OperationSignature.fromWalEntry(entry);

    assertThat(sig.className(), is("com.example.Calculator"));
    assertThat(sig.executableName(), is("add"));
    assertThat(sig.paramTypes(), is(Arrays.asList("int", "int")));
    assertThat(sig.messageType(), is(MessageType.EXEC_INSTANCE_METHOD));
  }
}
