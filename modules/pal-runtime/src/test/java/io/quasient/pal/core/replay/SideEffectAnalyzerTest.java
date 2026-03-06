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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.core.replay.SideEffectAnalyzer.UnsafeStubWarning;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link SideEffectAnalyzer} — detects when stubbing a span would silently skip
 * field mutations (PUT_FIELD / PUT_STATIC) that are visible outside the span, and emits warnings.
 */
public class SideEffectAnalyzerTest {

  /** The analyzer under test. */
  private final SideEffectAnalyzer analyzer = new SideEffectAnalyzer();

  /**
   * Verifies that a span containing no PUT_FIELD or PUT_STATIC entries produces no warnings when
   * stubbed.
   */
  @Test
  public void noWarningsForSafeStub() {
    // Given: WAL with span containing no PUT_FIELD/PUT_STATIC entries; policy stubs the span
    // Span: operation at 10, nested method call at 20, return at 30, completion at 40
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Service", "process", 0, 1),
            makeMethodOperation(20, "com.example.Helper", "compute", 0, 2),
            makeCompletion(30, 3),
            makeCompletion(40, 4));

    WalIndex index = WalIndex.build(entries);
    ReplayPolicy policy = stubAllPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then
    assertThat(warnings.size(), is(0));
  }

  /**
   * Verifies that a PUT_FIELD on an object referenced outside the stubbed span produces a warning.
   */
  @Test
  public void warningForPutFieldOnExternallyReferencedObject() {
    // Given: WAL with span (10, 40) containing PUT_FIELD at offset 25 on ref 99;
    //        ref 99 appears in an operation at offset 55 (outside span); policy stubs span
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Enricher", "enrich", 0, 1),
            makePutFieldOperation(25, "com.example.Order", "enriched", 99, 2),
            makePutFieldCompletion(30, 3),
            makeCompletion(40, 4),
            makeMethodOperation(55, "com.example.Processor", "submit", 99, 5),
            makeCompletion(60, 6));

    WalIndex index = WalIndex.build(entries);
    ReplayPolicy policy = stubAllPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then: Returns one UnsafeStubWarning identifying the PUT at offset 25
    //       and the external reference at offset 55
    assertThat(warnings.size(), is(1));
    UnsafeStubWarning warning = warnings.get(0);
    assertThat(warning.getPutEntry().getOffset(), is(25L));
    assertThat(warning.getExternalReferenceOffset(), is(55L));
    assertThat(warning.getOperationEntry().getOffset(), is(10L));
    assertThat(warning.toString(), containsString("PUT_FIELD"));
    assertThat(warning.toString(), containsString("ref 99"));
    assertThat(warning.toString(), containsString("offset 55"));
  }

  /**
   * Verifies that a PUT_FIELD on an object only referenced within the stubbed span produces no
   * warning, since the mutation is not visible outside.
   */
  @Test
  public void noWarningForPutFieldOnInternalObject() {
    // Given: WAL with span (10, 40) containing PUT_FIELD at offset 25 on ref 99;
    //        ref 99 only appears within the span (offsets 20-35)
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Builder", "build", 0, 1),
            makeMethodOperation(20, "com.example.Internal", "setup", 99, 2),
            makePutFieldOperation(25, "com.example.Internal", "value", 99, 3),
            makePutFieldCompletion(28, 4),
            makeCompletion(30, 5),
            makeCompletion(40, 6));

    WalIndex index = WalIndex.build(entries);
    ReplayPolicy policy = stubAllPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then: Returns empty list (internal mutation, not visible outside)
    assertThat(warnings.size(), is(0));
  }

  /**
   * Verifies that a PUT_STATIC within a stubbed span always produces a warning, since static fields
   * are globally visible.
   */
  @Test
  public void warningForStaticFieldPut() {
    // Given: WAL with span containing PUT_STATIC; policy stubs the span
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Initializer", "init", 0, 1),
            makePutStaticOperation(25, "com.example.Config", "initialized", 2),
            makePutStaticCompletion(30, 3),
            makeCompletion(40, 4));

    WalIndex index = WalIndex.build(entries);
    ReplayPolicy policy = stubAllPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then: Returns warning (static fields are always externally visible)
    assertThat(warnings.size(), is(1));
    UnsafeStubWarning warning = warnings.get(0);
    assertThat(warning.getPutEntry().getOffset(), is(25L));
    assertThat(warning.getExternalReferenceOffset(), is(-1L));
    assertThat(warning.toString(), containsString("PUT_STATIC"));
    assertThat(warning.toString(), containsString("always externally visible"));
  }

  /**
   * Verifies that spans whose policy action is RE_EXECUTE are not analyzed for side effects, since
   * they will be fully re-executed.
   */
  @Test
  public void noAnalysisForReExecuteSpans() {
    // Given: WAL with span containing PUT_FIELD; policy returns RE_EXECUTE for this span
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Service", "process", 0, 1),
            makePutFieldOperation(25, "com.example.Order", "status", 99, 2),
            makePutFieldCompletion(30, 3),
            makeCompletion(40, 4),
            makeMethodOperation(55, "com.example.Checker", "check", 99, 5),
            makeCompletion(60, 6));

    WalIndex index = WalIndex.build(entries);
    // Default policy RE_EXECUTEs everything
    ReplayPolicy policy = new ReplayPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then: Returns empty list (RE_EXECUTE spans don't need analysis)
    assertThat(warnings.size(), is(0));
  }

  /**
   * Verifies that multiple PUT operations on different externally-referenced objects within a
   * single stubbed span produce multiple warnings.
   */
  @Test
  public void multipleWarningsForMultiplePuts() {
    // Given: WAL with span containing two PUTs on different external refs
    List<WalEntry> entries =
        Arrays.asList(
            makeMethodOperation(10, "com.example.Enricher", "enrichAll", 0, 1),
            makePutFieldOperation(20, "com.example.Order", "field1", 88, 2),
            makePutFieldCompletion(22, 3),
            makePutFieldOperation(25, "com.example.Order", "field2", 77, 4),
            makePutFieldCompletion(28, 5),
            makeCompletion(40, 6),
            makeMethodOperation(50, "com.example.Sender", "send", 88, 7),
            makeCompletion(55, 8),
            makeMethodOperation(60, "com.example.Logger", "log", 77, 9),
            makeCompletion(65, 10));

    WalIndex index = WalIndex.build(entries);
    ReplayPolicy policy = stubAllPolicy();

    // When
    List<UnsafeStubWarning> warnings = analyzer.analyze(index, policy);

    // Then: Returns two warnings
    assertThat(warnings.size(), is(2));
  }

  /**
   * Creates a policy that stubs all operations with {@link ReplayAction#STUB_FROM_WAL}.
   *
   * @return a stub-all replay policy
   */
  private static ReplayPolicy stubAllPolicy() {
    List<ReplayPolicyRule> rules =
        List.of(new ReplayPolicyRule("**", "**", ReplayAction.STUB_FROM_WAL));
    return new ReplayPolicy(rules, ReplayAction.STUB_FROM_WAL);
  }

  /**
   * Creates a synthetic instance method OPERATION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param className the class name
   * @param methodName the method name
   * @param objectRef the target object reference (0 for non-instance)
   * @param builderSeq the builder sequence number
   * @return a new operation entry
   */
  private static WalEntry makeMethodOperation(
      long offset, String className, String methodName, int objectRef, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName(methodName);
    imc.setObjectRef(objectRef);
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic COMPLETION {@link WalEntry} using a void {@link ReturnValue}.
   *
   * @param offset the WAL offset
   * @param builderSeq the builder sequence number
   * @return a new completion entry
   */
  private static WalEntry makeCompletion(long offset, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic PUT_FIELD OPERATION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param className the class owning the field
   * @param fieldName the field name
   * @param objectRef the target object reference
   * @param builderSeq the builder sequence number
   * @return a new PUT_FIELD operation entry
   */
  private static WalEntry makePutFieldOperation(
      long offset, String className, String fieldName, int objectRef, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    InstanceFieldPut ifp = new InstanceFieldPut();
    ifp.setObjectRef(objectRef);
    Class clazz = new Class();
    clazz.setName(className);
    ifp.setClazz(clazz);
    Field field = new Field();
    field.setName(fieldName);
    ifp.setField(field);
    msg.setInstanceFieldPut(ifp);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic PUT_FIELD_DONE COMPLETION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param builderSeq the builder sequence number
   * @return a new PUT_FIELD_DONE completion entry
   */
  private static WalEntry makePutFieldCompletion(long offset, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    io.quasient.pal.messages.colfer.InstanceFieldPutDone done =
        new io.quasient.pal.messages.colfer.InstanceFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Done");
    done.setClazz(clazz);
    Field field = new Field();
    field.setName("done");
    done.setField(field);
    msg.setInstanceFieldPutDone(done);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic PUT_STATIC OPERATION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param className the class owning the static field
   * @param fieldName the static field name
   * @param builderSeq the builder sequence number
   * @return a new PUT_STATIC operation entry
   */
  private static WalEntry makePutStaticOperation(
      long offset, String className, String fieldName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    StaticFieldPut sfp = new StaticFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    sfp.setClazz(clazz);
    Field field = new Field();
    field.setName(fieldName);
    sfp.setField(field);
    msg.setStaticFieldPut(sfp);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic PUT_STATIC_DONE COMPLETION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param builderSeq the builder sequence number
   * @return a new PUT_STATIC_DONE completion entry
   */
  private static WalEntry makePutStaticCompletion(long offset, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(builderSeq);
    io.quasient.pal.messages.colfer.StaticFieldPutDone done =
        new io.quasient.pal.messages.colfer.StaticFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Done");
    done.setClazz(clazz);
    Field field = new Field();
    field.setName("done");
    done.setField(field);
    msg.setStaticFieldPutDone(done);
    return WalEntry.fromExecMessage(offset, msg);
  }
}
