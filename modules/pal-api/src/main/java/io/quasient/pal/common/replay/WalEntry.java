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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import java.util.Collections;
import java.util.List;

/**
 * Immutable model wrapping a deserialized {@link ExecMessage} with indexed metadata for WAL replay.
 *
 * <p>Each entry captures the WAL offset, message type, thread context, builder sequence, class and
 * executable names, parameter types, object reference, the raw message, and the derived {@link
 * WalEntryKind}.
 *
 * <p>Instances are created via the {@link #fromExecMessage(long, ExecMessage)} factory method,
 * which extracts fields using {@link ExecMessageUtils}.
 */
public final class WalEntry {

  /** The WAL offset (Chronicle index or Kafka offset) of this entry. */
  private final long offset;

  /** The {@link MessageType} determined from the populated oneof field of the raw message. */
  private final MessageType messageType;

  /** The name of the thread that produced this entry. */
  private final String threadName;

  /** The builder sequence number from the raw message. */
  private final int builderSeq;

  /** The fully qualified class name extracted from the raw message. */
  private final String className;

  /**
   * The executable name (method, constructor, or field) extracted from the raw message, or {@code
   * null} for completion types where the source executable is unavailable.
   */
  private final String executableName;

  /**
   * The parameter types for constructor and method calls, or {@code null} for non-callable message
   * types.
   */
  private final List<String> paramTypes;

  /** The target object reference for instance operations, or {@code 0} for static operations. */
  private final int objectRef;

  /** The full deserialized {@link ExecMessage} for return value extraction and other uses. */
  private final ExecMessage rawMessage;

  /** Whether this entry is an {@link WalEntryKind#OPERATION} or {@link WalEntryKind#COMPLETION}. */
  private final WalEntryKind kind;

  /**
   * Whether this entry represents an entry-point operation (e.g., an incoming RPC call that
   * initiates a new causal chain on a non-self-caller thread).
   */
  private final boolean entryPoint;

  /**
   * Constructs a new {@code WalEntry} with all fields.
   *
   * @param offset the WAL offset
   * @param messageType the message type
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param executableName the executable name (may be null)
   * @param paramTypes the parameter types (may be null)
   * @param objectRef the object reference
   * @param rawMessage the raw ExecMessage
   * @param kind the entry kind (OPERATION or COMPLETION)
   * @param entryPoint whether this is an entry-point operation
   */
  private WalEntry(
      long offset,
      MessageType messageType,
      String threadName,
      int builderSeq,
      String className,
      String executableName,
      List<String> paramTypes,
      int objectRef,
      ExecMessage rawMessage,
      WalEntryKind kind,
      boolean entryPoint) {
    this.offset = offset;
    this.messageType = messageType;
    this.threadName = threadName;
    this.builderSeq = builderSeq;
    this.className = className;
    this.executableName = executableName;
    this.paramTypes = paramTypes != null ? Collections.unmodifiableList(paramTypes) : null;
    this.objectRef = objectRef;
    this.rawMessage = rawMessage;
    this.kind = kind;
    this.entryPoint = entryPoint;
  }

  /**
   * Creates a {@code WalEntry} from a deserialized {@link ExecMessage} and its WAL offset.
   *
   * <p>For {@link WalEntryKind#OPERATION} types, the executable name is extracted via {@link
   * ExecMessageUtils#getExecutableName(ExecMessage)}. For {@link WalEntryKind#COMPLETION} types
   * (PUT_STATIC_DONE, PUT_FIELD_DONE, THROWABLE, RETURN_VALUE), the executable name is extracted
   * via {@link ExecMessageUtils#getFromExecutableName(ExecMessage)}.
   *
   * @param offset the WAL offset (Chronicle index or Kafka offset)
   * @param msg the deserialized ExecMessage
   * @return a new {@code WalEntry} with all fields extracted from the message
   */
  public static WalEntry fromExecMessage(long offset, ExecMessage msg) {
    MessageType msgType = ExecMessageUtils.getMessageTypeOf(msg);
    WalEntryKind kind = WalEntryKind.fromMessageType(msgType);
    String className = ExecMessageUtils.getClassname(msg);
    String executableName;
    List<String> paramTypes;
    int objectRef;

    if (kind == WalEntryKind.OPERATION) {
      executableName = ExecMessageUtils.getExecutableName(msg);
      paramTypes = ExecMessageUtils.getParameterTypes(msg);
      objectRef = extractObjectRef(msg, msgType);
    } else {
      executableName = ExecMessageUtils.getFromExecutableName(msg);
      paramTypes = null;
      objectRef = 0;
    }

    boolean entryPoint = msg.getEntryPoint();

    return new WalEntry(
        offset,
        msgType,
        msg.getThreadName(),
        msg.getBuilderSeq(),
        className,
        executableName,
        paramTypes,
        objectRef,
        msg,
        kind,
        entryPoint);
  }

  /**
   * Extracts the object reference from the message for instance operations.
   *
   * @param msg the ExecMessage
   * @param msgType the determined message type
   * @return the object reference, or {@code 0} for non-instance operations
   */
  private static int extractObjectRef(ExecMessage msg, MessageType msgType) {
    return switch (msgType) {
      case EXEC_INSTANCE_METHOD -> msg.getInstanceMethodCall().getObjectRef();
      case EXEC_GET_FIELD -> msg.getInstanceFieldGet().getObjectRef();
      case EXEC_PUT_FIELD -> msg.getInstanceFieldPut().getObjectRef();
      default -> 0;
    };
  }

  /**
   * Returns the WAL offset of this entry.
   *
   * @return the offset (Chronicle index or Kafka offset)
   */
  public long getOffset() {
    return offset;
  }

  /**
   * Returns the {@link MessageType} of this entry.
   *
   * @return the message type
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Returns the name of the thread that produced this entry.
   *
   * @return the thread name
   */
  public String getThreadName() {
    return threadName;
  }

  /**
   * Returns the builder sequence number.
   *
   * @return the builder sequence
   */
  public int getBuilderSeq() {
    return builderSeq;
  }

  /**
   * Returns the fully qualified class name.
   *
   * @return the class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Returns the executable name (method, constructor, or field name).
   *
   * @return the executable name, or {@code null} for completion types where the source executable
   *     is unavailable
   */
  public String getExecutableName() {
    return executableName;
  }

  /**
   * Returns the parameter types for constructor and method calls.
   *
   * @return an unmodifiable list of parameter type names, or {@code null} for non-callable message
   *     types
   */
  public List<String> getParamTypes() {
    return paramTypes;
  }

  /**
   * Returns the target object reference for instance operations.
   *
   * @return the object reference, or {@code 0} for static or non-instance operations
   */
  public int getObjectRef() {
    return objectRef;
  }

  /**
   * Returns the full deserialized {@link ExecMessage}.
   *
   * <p>The returned reference is the same object passed to the factory method. Callers should not
   * mutate it.
   *
   * @return the raw message
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Intentional: WalEntry wraps the ExecMessage for downstream access")
  public ExecMessage getRawMessage() {
    return rawMessage;
  }

  /**
   * Returns whether this entry is an {@link WalEntryKind#OPERATION} or {@link
   * WalEntryKind#COMPLETION}.
   *
   * @return the entry kind
   */
  public WalEntryKind getKind() {
    return kind;
  }

  /**
   * Returns whether this entry represents an entry-point operation.
   *
   * <p>Entry-point operations are incoming RPC calls or other external inputs that initiate a new
   * causal chain on a non-self-caller thread. During deterministic WAL replay, entry-point
   * operations are injected from the WAL rather than matched against the cursor.
   *
   * @return {@code true} if this is an entry-point operation, {@code false} otherwise
   */
  public boolean isEntryPoint() {
    return entryPoint;
  }
}
