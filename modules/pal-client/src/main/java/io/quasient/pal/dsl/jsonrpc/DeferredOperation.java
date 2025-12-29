/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.jsonrpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.objects.ObjectRef;
import javax.annotation.Nullable;

/**
 * Represents a deferred operation in the JSON-RPC DSL, encapsulating the details required to
 * execute operations such as creating instances, invoking constructors/methods, and accessing
 * fields at a later stage.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "DSL builder - args array is intentionally shared for chain operations")
public class DeferredOperation {
  /** Enumerates the types of supported deferred operations. */
  public enum OpType {
    /** Represents a deferred operation to create a new instance of a class. */
    NEW_INSTANCE,
    /** Represents a deferred operation to invoke a static method. */
    STATIC_METHOD,
    /** Represents a deferred operation to invoke an instance method. */
    INSTANCE_METHOD,
    /** Represents a deferred operation to retrieve the value of a static field. */
    STATIC_FIELD_GET,
    /** Represents a deferred operation to set the value of a static field. */
    STATIC_FIELD_PUT,
    /** Represents a deferred operation to retrieve the value of an instance field. */
    INSTANCE_FIELD_GET,
    /** Represents a deferred operation to set the value of an instance field. */
    INSTANCE_FIELD_PUT
  }

  /** The type of this deferred operation. */
  private OpType opType;

  /** The fully qualified name of the target class associated with this operation. */
  private String className;

  /** The name of the method involved in this operation, if applicable. */
  private String methodName;

  /** The name of the field involved in this operation, if applicable. */
  private String fieldName;

  /**
   * The arguments for the constructor/method invocation or the value to set in field operations.
   */
  private Object[] args;

  /** The variable name used to reference the instance in instance-based operations. */
  private String instanceVarName; // Used if we defer instance references by varName

  /** A direct reference to the instance, used when available. */
  private ObjectRef directInstanceRef; // If we have a known ref

  /** The variable name where the result of this operation will be stored. */
  private String resultVarName;

  /**
   * Creates a deferred operation to instantiate a new object of the specified class.
   *
   * @param className the fully qualified name of the class to instantiate
   * @param varName the variable name to assign the newly created instance, may be {@code null}
   * @param args the constructor arguments for creating the new instance, may be {@code null}
   * @return a {@code DeferredOperation} representing the new instance creation
   */
  public static DeferredOperation newInstance(
      String className, @Nullable String varName, @Nullable Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.NEW_INSTANCE;
    op.className = className;
    op.args = args;
    op.resultVarName = varName;
    return op;
  }

  /**
   * Creates a deferred operation to invoke a static method of the specified class.
   *
   * @param className the fully qualified name of the class containing the static method
   * @param methodName the name of the static method to invoke
   * @param resultVarName the variable name to assign the result of the method invocation, may be
   *     {@code null}
   * @param args the arguments to pass to the static method, must not be {@code null}
   * @return a {@code DeferredOperation} representing the static method invocation
   */
  public static DeferredOperation staticMethod(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_METHOD;
    op.className = className;
    op.methodName = methodName;
    op.args = args;
    op.resultVarName = resultVarName;
    return op;
  }

  /**
   * Creates a deferred operation to invoke an instance method on a specified instance.
   *
   * @param className the fully qualified name of the class containing the instance method
   * @param methodName the name of the instance method to invoke
   * @param resultVarName the variable name to assign the result of the method invocation, may be
   *     {@code null}
   * @param instanceVarName the variable name referencing the instance on which to invoke the method
   * @param directRef a direct reference to the instance, may be {@code null}
   * @param args the arguments to pass to the instance method, must not be {@code null}
   * @return a {@code DeferredOperation} representing the instance method invocation
   */
  public static DeferredOperation instanceMethod(
      String className,
      String methodName,
      @Nullable String resultVarName,
      String instanceVarName,
      ObjectRef directRef,
      Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_METHOD;
    op.className = className;
    op.methodName = methodName;
    op.resultVarName = resultVarName;
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    op.args = args;
    return op;
  }

  /**
   * Creates a deferred operation to retrieve the value of a static field.
   *
   * @param className the fully qualified name of the class containing the static field
   * @param fieldName the name of the static field to retrieve
   * @param resultVarName the variable name to assign the retrieved value, may be {@code null}
   * @return a {@code DeferredOperation} representing the static field retrieval
   */
  public static DeferredOperation staticFieldGet(
      String className, String fieldName, @Nullable String resultVarName) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_FIELD_GET;
    op.className = className;
    op.fieldName = fieldName;
    op.resultVarName = resultVarName;
    return op;
  }

  /**
   * Creates a deferred operation to set the value of a static field.
   *
   * @param className the fully qualified name of the class containing the static field
   * @param fieldName the name of the static field to set
   * @param value the value to assign to the static field
   * @return a {@code DeferredOperation} representing the static field assignment
   */
  public static DeferredOperation staticFieldPut(String className, String fieldName, Object value) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_FIELD_PUT;
    op.className = className;
    op.fieldName = fieldName;
    op.args = new Object[] {value};
    return op;
  }

  /**
   * Creates a deferred operation to retrieve the value of an instance field.
   *
   * @param className the fully qualified name of the class containing the instance field
   * @param fieldName the name of the instance field to retrieve
   * @param resultVarName the variable name to assign the retrieved value, may be {@code null}
   * @param instanceVarName the variable name referencing the instance whose field is to be
   *     retrieved
   * @param directRef a direct reference to the instance, may be {@code null}
   * @return a {@code DeferredOperation} representing the instance field retrieval
   */
  public static DeferredOperation instanceFieldGet(
      String className,
      String fieldName,
      @Nullable String resultVarName,
      String instanceVarName,
      ObjectRef directRef) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_FIELD_GET;
    op.className = className;
    op.fieldName = fieldName;
    op.resultVarName = resultVarName;
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    return op;
  }

  /**
   * Creates a deferred operation to set the value of an instance field.
   *
   * @param className the fully qualified name of the class containing the instance field
   * @param fieldName the name of the instance field to set
   * @param value the value to assign to the instance field
   * @param instanceVarName the variable name referencing the instance whose field is to be set
   * @param directRef a direct reference to the instance, may be {@code null}
   * @return a {@code DeferredOperation} representing the instance field assignment
   */
  public static DeferredOperation instanceFieldPut(
      String className,
      String fieldName,
      Object value,
      String instanceVarName,
      ObjectRef directRef) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_FIELD_PUT;
    op.className = className;
    op.fieldName = fieldName;
    op.args = new Object[] {value};
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    return op;
  }

  /**
   * Retrieves the type of this deferred operation.
   *
   * @return the operation type
   */
  public OpType getOpType() {
    return opType;
  }

  /**
   * Retrieves the fully qualified name of the class associated with this operation.
   *
   * @return the class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Retrieves the name of the method involved in this operation, if applicable.
   *
   * @return the method name, or {@code null} if not applicable
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * Retrieves the name of the field involved in this operation, if applicable.
   *
   * @return the field name, or {@code null} if not applicable
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Retrieves the arguments for this operation.
   *
   * @return the arguments as an array of {@code Object}, may be {@code null}
   */
  public Object[] getArgs() {
    return args;
  }

  /**
   * Retrieves the variable name referencing the instance, used in instance-based operations.
   *
   * @return the instance variable name, or {@code null} if not applicable
   */
  public String getInstanceVarName() {
    return instanceVarName;
  }

  /**
   * Retrieves the direct reference to the instance, used when available in instance-based
   * operations.
   *
   * @return the direct instance reference, or {@code null} if not applicable
   */
  public ObjectRef getDirectInstanceRef() {
    return directInstanceRef;
  }

  /**
   * Retrieves the variable name where the result of this operation will be stored.
   *
   * @return the result variable name, or {@code null} if no result is expected
   */
  public String getResultVarName() {
    return resultVarName;
  }
}
