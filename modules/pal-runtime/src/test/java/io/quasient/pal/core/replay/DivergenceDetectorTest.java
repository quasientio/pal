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
import io.quasient.pal.core.replay.DivergenceDetector.DivergencePolicy;
import io.quasient.pal.core.replay.DivergenceDetector.DivergenceType;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code DivergenceDetector} — the verification engine that compares actual return
 * values against WAL-recorded values and accumulates a divergence report.
 *
 * <p>Tests cover return-value comparison (equal, null, mismatch), operation mismatch reporting
 * (extra, missing, wrong signature), report aggregation, and divergence policy behavior (HALT vs
 * WARN).
 */
public class DivergenceDetectorTest {

  /** Verifies no divergence is reported when WAL and actual return values are equal. */
  @Test
  public void noDivergenceForEqualValues() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 42, "self-caller");

    assertThat(detector.hasDivergences(), is(false));
  }

  /** Verifies VALUE_MISMATCH divergence when WAL and actual return values differ. */
  @Test
  public void valueMismatchForDifferentValues() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 99, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(), is(DivergenceType.VALUE_MISMATCH));
  }

  /** Verifies no divergence when both WAL and actual return values are null. */
  @Test
  public void noDivergenceForBothNull() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createNullReturnValueEntry();

    detector.compareReturnValue(entry, null, "self-caller");

    assertThat(detector.hasDivergences(), is(false));
  }

  /** Verifies VALUE_MISMATCH when WAL has null but actual is non-null. */
  @Test
  public void valueMismatchNullVsNonNull() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createNullReturnValueEntry();

    detector.compareReturnValue(entry, "hello", "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(), is(DivergenceType.VALUE_MISMATCH));
  }

  /** Verifies OPERATION_MISMATCH is reported when WAL and live signatures differ. */
  @Test
  public void operationMismatchReported() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry expected = createInstanceMethodEntry("com.example.Calculator", "add");
    OperationSignature actual =
        new OperationSignature(
            "com.example.Calculator", "subtract", List.of(), MessageType.EXEC_INSTANCE_METHOD);

    detector.reportOperationMismatch(expected, actual, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(), is(DivergenceType.OPERATION_MISMATCH));
  }

  /** Verifies EXTRA_OPERATION is reported when live execution has an operation not in the WAL. */
  @Test
  public void extraOperationReported() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    OperationSignature actual =
        new OperationSignature(
            "com.example.Calculator", "multiply", List.of(), MessageType.EXEC_INSTANCE_METHOD);

    detector.reportExtraOperation(actual, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(), is(DivergenceType.EXTRA_OPERATION));
  }

  /**
   * Verifies MISSING_OPERATION is reported when the WAL expects an operation not in live execution.
   */
  @Test
  public void missingOperationReported() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry expected = createInstanceMethodEntry("com.example.Calculator", "add");

    detector.reportMissingOperation(expected, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(), is(DivergenceType.MISSING_OPERATION));
  }

  /** Verifies that multiple divergences are aggregated in the report. */
  @Test
  public void reportAggregatesDivergences() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 99, "self-caller");
    detector.reportExtraOperation(
        new OperationSignature(
            "com.example.Foo", "bar", List.of(), MessageType.EXEC_INSTANCE_METHOD),
        "self-caller");
    detector.reportMissingOperation(
        createInstanceMethodEntry("com.example.Baz", "qux"), "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    DivergenceReport report = detector.getReport();
    assertThat(report.size(), is(3));
  }

  /** Verifies that an empty report is returned when no divergences have been recorded. */
  @Test
  public void emptyReportWhenNoDivergences() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);

    assertThat(detector.hasDivergences(), is(false));
    assertThat(detector.getReport().isEmpty(), is(true));
    assertThat(detector.getReport().getDivergences().size(), is(0));
  }

  /** Verifies that HALT policy throws an exception immediately on divergence. */
  @Test(expected = RuntimeException.class)
  public void haltPolicyThrowsOnDivergence() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.HALT);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 99, "self-caller");
  }

  /** Verifies that WARN policy records divergence but does not throw. */
  @Test
  public void warnPolicyLogsButContinues() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 99, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    assertThat(detector.getReport().size(), is(1));
  }

  /**
   * Verifies that the thread name is recorded in the divergence when a return value mismatch is
   * detected on a named thread.
   */
  @Test
  public void compareReturnValue_recordsThreadName() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry entry = createReturnValueEntry(42);

    detector.compareReturnValue(entry, 99, "rpc-worker-1");

    assertThat(detector.hasDivergences(), is(true));
    Divergence divergence = detector.getReport().getDivergences().get(0);
    assertThat(divergence.type(), is(DivergenceType.VALUE_MISMATCH));
    assertThat(divergence.threadName(), is("rpc-worker-1"));
  }

  /**
   * Verifies that the thread name is recorded in the divergence when an operation mismatch is
   * reported from a named thread.
   */
  @Test
  public void reportOperationMismatch_recordsThreadName() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    WalEntry expected = createInstanceMethodEntry("com.example.Calculator", "add");
    OperationSignature actual =
        new OperationSignature(
            "com.example.Calculator", "subtract", List.of(), MessageType.EXEC_INSTANCE_METHOD);

    detector.reportOperationMismatch(expected, actual, "self-caller");

    assertThat(detector.hasDivergences(), is(true));
    Divergence divergence = detector.getReport().getDivergences().get(0);
    assertThat(divergence.type(), is(DivergenceType.OPERATION_MISMATCH));
    assertThat(divergence.threadName(), is("self-caller"));
  }

  /**
   * Verifies that the thread name is recorded in the divergence when an extra operation is reported
   * from a named thread.
   */
  @Test
  public void reportExtraOperation_recordsThreadName() {
    DivergenceDetector detector = new DivergenceDetector(DivergencePolicy.WARN);
    OperationSignature actual =
        new OperationSignature(
            "com.example.Calculator", "multiply", List.of(), MessageType.EXEC_INSTANCE_METHOD);

    detector.reportExtraOperation(actual, "rpc-worker-2");

    assertThat(detector.hasDivergences(), is(true));
    Divergence divergence = detector.getReport().getDivergences().get(0);
    assertThat(divergence.type(), is(DivergenceType.EXTRA_OPERATION));
    assertThat(divergence.threadName(), is("rpc-worker-2"));
  }

  /**
   * Creates a WalEntry for an EXEC_RETURN_VALUE with a non-null integer return value.
   *
   * @param value the integer return value to embed in the WAL entry
   * @return a WalEntry wrapping a ReturnValue with the given integer
   */
  private static WalEntry createReturnValueEntry(int value) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setValue(String.valueOf(value));
    Class clazz = new Class();
    clazz.setName("java.lang.Integer");
    obj.setClazz(clazz);
    rv.setObject(obj);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(0L, msg);
  }

  /**
   * Creates a WalEntry for an EXEC_RETURN_VALUE with a null return value.
   *
   * @return a WalEntry wrapping a ReturnValue with isNull=true
   */
  private static WalEntry createNullReturnValueEntry() {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setIsNull(true);
    Class objClazz = new Class();
    objClazz.setName("java.lang.Object");
    obj.setClazz(objClazz);
    rv.setObject(obj);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(0L, msg);
  }

  /**
   * Creates a WalEntry for an EXEC_INSTANCE_METHOD operation.
   *
   * @param className the class name
   * @param methodName the method name
   * @return a WalEntry wrapping an instance method call
   */
  private static WalEntry createInstanceMethodEntry(String className, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName("self-caller");
    msg.setBuilderSeq(1);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName(methodName);
    imc.setObjectRef(1);
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(0L, msg);
  }
}
