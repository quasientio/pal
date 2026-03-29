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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.common.replay.Span;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link SpanFieldMutationReplayer} — replays PUT_FIELD and PUT_STATIC operations
 * within a stubbed span via reflection, enabling STUB_WITH_SIDE_EFFECTS replay semantics.
 */
public class SpanFieldMutationReplayerTest {

  /** The replayer under test. */
  private SpanFieldMutationReplayer replayer;

  /** The object store used for resolving WAL refs. */
  private ReplayObjectStore objectStore;

  /** Sets up the replayer and object store before each test. */
  @Before
  public void setUp() {
    replayer = new SpanFieldMutationReplayer();
    objectStore = new ReplayObjectStore();
  }

  /** Resets static state that may have been modified by tests. */
  @After
  public void tearDown() {
    StaticTarget.counter = 0;
  }

  /**
   * Verifies that an instance field PUT recorded in the WAL is applied to the corresponding live
   * object via reflection.
   */
  @Test
  public void replaysInstanceFieldPutOnRegisteredObject() {
    // Given: WalIndex with span containing PUT_FIELD (ref=1, field="name", value="Alice");
    //        objectStore has ref 1 mapped to a live object with String field `name`
    InstanceTarget target = new InstanceTarget();
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main")); // span operation
    entries.add(
        makeInstanceFieldPutEntry(15, "main", InstanceTarget.class.getName(), "name", "Alice", 1));
    entries.add(makeInstanceFieldPutDoneEntry(16, "main"));
    entries.add(makeCompletionEntry(20, "main")); // span completion
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: replayMutations(index, span, objectStore) called
    replayer.replayMutations(index, span, objectStore);

    // Then: Live object's `name` field is set to "Alice"
    assertThat(target.name, is("Alice"));
  }

  /** Verifies that a static field PUT recorded in the WAL is applied to the target class. */
  @Test
  public void replaysStaticFieldPut() {
    // Given: Span containing PUT_STATIC (class=StaticTarget, field="counter", value=42)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main"));
    entries.add(makeStaticFieldPutEntry(15, "main", StaticTarget.class.getName(), "counter", 42));
    entries.add(makeStaticFieldPutDoneEntry(16, "main"));
    entries.add(makeCompletionEntry(20, "main"));
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: Replayed
    replayer.replayMutations(index, span, objectStore);

    // Then: StaticTarget.counter is set to 42
    assertThat(StaticTarget.counter, is(42));
  }

  /**
   * Verifies that mutations targeting a phantom (unresolvable) object are silently skipped rather
   * than throwing.
   */
  @Test
  public void skipsPhantomTarget() {
    // Given: Span containing PUT_FIELD on ref 99; ref 99 is a phantom (not in objectStore)
    objectStore.registerPhantom(99);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main"));
    entries.add(
        makeInstanceFieldPutEntry(15, "main", InstanceTarget.class.getName(), "name", "Bob", 99));
    entries.add(makeInstanceFieldPutDoneEntry(16, "main"));
    entries.add(makeCompletionEntry(20, "main"));
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: Replayed
    // Then: No exception thrown; mutation skipped (logged at DEBUG)
    replayer.replayMutations(index, span, objectStore);
  }

  /**
   * Verifies that a PUT_FIELD whose value is a reference-only object (no serialized data) skips the
   * mutation (preserving the original field value) and registers the value ref as phantom.
   */
  @Test
  public void skipsUnreconstructableValueAndRegistersPhantom() {
    // Given: Span containing PUT_FIELD with reference-only value (no serialized data)
    InstanceTarget target = new InstanceTarget();
    target.name = "original";
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main"));
    entries.add(
        makeInstanceFieldPutEntryWithRefOnlyValue(
            15, "main", InstanceTarget.class.getName(), "name", 1, 77));
    entries.add(makeInstanceFieldPutDoneEntry(16, "main"));
    entries.add(makeCompletionEntry(20, "main"));
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: Replayed
    replayer.replayMutations(index, span, objectStore);

    // Then: Mutation skipped; field retains original value; value ref registered as phantom
    assertThat(target.name, is("original"));
    assertThat(objectStore.isPhantom(77), is(true));
  }

  /**
   * Verifies that multiple PUT_FIELD entries within a span are applied in WAL order, so the last
   * write wins.
   */
  @Test
  public void replaysMultiplePutsInOrder() {
    // Given: Span with two PUT_FIELD entries: (ref=1, field="value", value=1) then
    //        (ref=1, field="value", value=2)
    InstanceTarget target = new InstanceTarget();
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main"));
    entries.add(
        makeInstanceFieldPutEntry(15, "main", InstanceTarget.class.getName(), "value", 1, 1));
    entries.add(makeInstanceFieldPutDoneEntry(16, "main"));
    entries.add(
        makeInstanceFieldPutEntry(17, "main", InstanceTarget.class.getName(), "value", 2, 1));
    entries.add(makeInstanceFieldPutDoneEntry(18, "main"));
    entries.add(makeCompletionEntry(20, "main"));
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: Replayed
    replayer.replayMutations(index, span, objectStore);

    // Then: Field value is set to 2 (last write wins)
    assertThat(target.value, is(2));
  }

  /**
   * Verifies that an inaccessible field (e.g., due to JPMS restrictions) is handled gracefully with
   * a warning log rather than propagating the exception, and other mutations in the span are still
   * applied.
   */
  @Test
  public void handlesInaccessibleFieldGracefully() {
    // Given: Span containing PUT_FIELD targeting a non-existent field, followed by a valid PUT
    InstanceTarget target = new InstanceTarget();
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationEntry(10, "main"));
    // First PUT targets a field that doesn't exist — triggers ReflectiveOperationException
    entries.add(
        makeInstanceFieldPutEntry(
            15, "main", InstanceTarget.class.getName(), "nonExistentField", "value", 1));
    entries.add(makeInstanceFieldPutDoneEntry(16, "main"));
    // Second PUT targets a valid field
    entries.add(
        makeInstanceFieldPutEntry(17, "main", InstanceTarget.class.getName(), "name", "Carol", 1));
    entries.add(makeInstanceFieldPutDoneEntry(18, "main"));
    entries.add(makeCompletionEntry(20, "main"));
    WalIndex index = WalIndex.build(entries);
    Span span = index.getSpans().get(10L);

    // When: Replayed
    // Then: Warning logged; no exception thrown; other mutations still applied
    replayer.replayMutations(index, span, objectStore);
    assertThat(target.name, is("Carol"));
  }

  // ---- Helper methods ----

  /**
   * Creates an EXEC_INSTANCE_METHOD operation entry (used as span start).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @return a WalEntry representing a method call operation
   */
  private static WalEntry makeOperationEntry(long offset, String threadName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("someMethod");
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_RETURN_VALUE completion entry (used as span end).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @return a WalEntry representing a return value completion
   */
  private static WalEntry makeCompletionEntry(long offset, String threadName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_FIELD entry with a string value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param className the declaring class name
   * @param fieldName the field name
   * @param value the string value to set
   * @param objectRef the target object WAL ref
   * @return a WalEntry representing an instance field PUT
   */
  private static WalEntry makeInstanceFieldPutEntry(
      long offset,
      String threadName,
      String className,
      String fieldName,
      String value,
      int objectRef) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPut put = new InstanceFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    put.setClazz(clazz);
    put.setObjectRef(objectRef);

    Field field = new Field();
    field.setName(fieldName);
    put.setField(field);

    Obj valueObj = new Obj();
    Class valueClazz = new Class();
    valueClazz.setName("java.lang.String");
    valueObj.setClazz(valueClazz);
    valueObj.setValue("\"" + value + "\"");
    put.setValueObject(valueObj);

    msg.setInstanceFieldPut(put);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_FIELD entry with an integer value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param className the declaring class name
   * @param fieldName the field name
   * @param value the integer value to set
   * @param objectRef the target object WAL ref
   * @return a WalEntry representing an instance field PUT with an int value
   */
  private static WalEntry makeInstanceFieldPutEntry(
      long offset,
      String threadName,
      String className,
      String fieldName,
      int value,
      int objectRef) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPut put = new InstanceFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    put.setClazz(clazz);
    put.setObjectRef(objectRef);

    Field field = new Field();
    field.setName(fieldName);
    put.setField(field);

    Obj valueObj = new Obj();
    Class valueClazz = new Class();
    valueClazz.setName("java.lang.Integer");
    valueObj.setClazz(valueClazz);
    valueObj.setValue(String.valueOf(value));
    put.setValueObject(valueObj);

    msg.setInstanceFieldPut(put);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_FIELD entry with a reference-only value (no serialized data).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param className the declaring class name
   * @param fieldName the field name
   * @param objectRef the target object WAL ref
   * @param valueRef the WAL ref for the value (reference-only)
   * @return a WalEntry representing an instance field PUT with a reference-only value
   */
  private static WalEntry makeInstanceFieldPutEntryWithRefOnlyValue(
      long offset,
      String threadName,
      String className,
      String fieldName,
      int objectRef,
      int valueRef) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPut put = new InstanceFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    put.setClazz(clazz);
    put.setObjectRef(objectRef);

    Field field = new Field();
    field.setName(fieldName);
    put.setField(field);

    Obj valueObj = new Obj();
    valueObj.setRef(valueRef);
    // No value or type set — reference-only
    put.setValueObject(valueObj);
    put.setValueObjectRef(valueRef);

    msg.setInstanceFieldPut(put);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_FIELD_DONE completion entry.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @return a WalEntry representing a PUT_FIELD_DONE completion
   */
  private static WalEntry makeInstanceFieldPutDoneEntry(long offset, String threadName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPutDone done = new InstanceFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    Field doneField = new Field();
    doneField.setName("done");
    doneField.setClazz(clazz);
    done.setField(doneField);
    msg.setInstanceFieldPutDone(done);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_STATIC entry with an integer value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param className the declaring class name
   * @param fieldName the field name
   * @param value the integer value to set
   * @return a WalEntry representing a static field PUT
   */
  private static WalEntry makeStaticFieldPutEntry(
      long offset, String threadName, String className, String fieldName, int value) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    StaticFieldPut put = new StaticFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    put.setClazz(clazz);

    Field field = new Field();
    field.setName(fieldName);
    put.setField(field);

    Obj valueObj = new Obj();
    Class valueClazz = new Class();
    valueClazz.setName("java.lang.Integer");
    valueObj.setClazz(valueClazz);
    valueObj.setValue(String.valueOf(value));
    put.setValueObject(valueObj);

    msg.setStaticFieldPut(put);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an EXEC_PUT_STATIC_DONE completion entry.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @return a WalEntry representing a PUT_STATIC_DONE completion
   */
  private static WalEntry makeStaticFieldPutDoneEntry(long offset, String threadName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    StaticFieldPutDone done = new StaticFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    Field doneField = new Field();
    doneField.setName("done");
    doneField.setClazz(clazz);
    done.setField(doneField);
    msg.setStaticFieldPutDone(done);

    return WalEntry.fromExecMessage(offset, msg);
  }

  // ---- Test target classes ----

  /** Test target for instance field mutation tests. */
  static class InstanceTarget {

    /** A string field for testing. */
    String name;

    /** An int field for testing multiple writes. */
    int value;
  }

  /** Test target for static field mutation tests. */
  static class StaticTarget {

    /** A static int field for testing. */
    static int counter;
  }
}
