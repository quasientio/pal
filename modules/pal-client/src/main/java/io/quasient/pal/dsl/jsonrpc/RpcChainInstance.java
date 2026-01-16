/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.jsonrpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.objects.ObjectRef;
import javax.annotation.Nullable;

/**
 * Represents an RPC chain with an object instance, enabling instance method invocations and field
 * access operations to be queued and executed within the RPC framework.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "DSL builder - RpcChain reference is intentionally shared for chain operations")
public class RpcChainInstance {

  /**
   * The root RPC chain to which this instance chain belongs, managing the sequence of RPC
   * operations.
   */
  private final RpcChain rootChain;

  /** Reference to the remote object instance that this chain's instance refers to. */
  protected final ObjectRef instanceRef;

  /** The identifier name assigned to the remote object instance within the RPC chain. */
  protected final String instanceName;

  /** The fully qualified class name of the remote object instance. */
  protected final String className;

  /**
   * Constructs a new RpcChainInstance with the specified parameters.
   *
   * @param rootChain The root RPC chain associated to this instance chain.
   * @param instanceRef Reference to the remote object instance.
   * @param instanceName The identifier name assigned to the remote object instance within the RPC
   *     chain.
   * @param className The fully qualified class name of the remote object instance.
   */
  public RpcChainInstance(
      RpcChain rootChain, ObjectRef instanceRef, String instanceName, String className) {
    this.rootChain = rootChain;
    this.instanceRef = instanceRef;
    this.instanceName = instanceName;
    this.className = className;
  }

  /**
   * Calls an instance method without arguments on the remote object.
   *
   * @param methodName The name of the method to invoke.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance call(String methodName) {
    return call(methodName, null, null);
  }

  /**
   * Calls an instance method on the remote object with the specified arguments.
   *
   * @param methodName The name of the method to invoke.
   * @param args The arguments to pass to the method.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance call(String methodName, Object[] args) {
    return call(methodName, null, args);
  }

  /**
   * Calls an instance method without arguments on the remote object and assigns the result to a
   * variable.
   *
   * @param methodName The name of the method to invoke.
   * @param resultVarName The name to assign to the result of the method call.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance call(String methodName, String resultVarName) {
    return call(methodName, resultVarName, null);
  }

  /**
   * Calls an instance method on the remote object with the specified arguments and assigns the
   * result to a variable.
   *
   * @param methodName The name of the method to invoke.
   * @param resultVarName The name to assign to the result of the method call.
   * @param args The arguments to pass to the method.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance call(String methodName, @Nullable String resultVarName, Object[] args) {
    rootChain.addOperation(
        DeferredOperation.instanceMethod(
            className, methodName, resultVarName, instanceName, instanceRef, args));
    return this;
  }

  /**
   * Retrieves the value of the specified field from the remote object.
   *
   * @param fieldName The name of the field to retrieve.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance get(String fieldName) {
    return get(fieldName, null);
  }

  /**
   * Retrieves the value of the specified field from the remote object and assigns it to a variable.
   *
   * @param fieldName The name of the field to retrieve.
   * @param resultVarName The name to assign to the retrieved field's value.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance get(String fieldName, @Nullable String resultVarName) {
    rootChain.addOperation(
        DeferredOperation.instanceFieldGet(
            className, fieldName, resultVarName, instanceName, instanceRef));
    return this;
  }

  /**
   * Sets the value of the specified field on the remote object.
   *
   * @param fieldName The name of the field to set.
   * @param value The value to assign to the field.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance put(String fieldName, Object value) {
    rootChain.addOperation(
        DeferredOperation.instanceFieldPut(className, fieldName, value, instanceName, instanceRef));
    return this;
  }

  /**
   * Calls a static method on the specified class with the given arguments.
   *
   * @param className The fully qualified class name containing the static method.
   * @param methodName The name of the static method to invoke.
   * @param args The arguments to pass to the static method.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance callStatic(String className, String methodName, Object[] args) {
    rootChain.callStatic(className, methodName, args);
    return this;
  }

  /**
   * Calls a static method on the specified class with the given arguments and assigns the result to
   * a variable.
   *
   * @param className The fully qualified class name containing the static method.
   * @param methodName The name of the static method to invoke.
   * @param resultVarName The name to assign to the result of the method call.
   * @param args The arguments to pass to the static method.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance callStatic(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    rootChain.callStatic(className, methodName, resultVarName, args);
    return this;
  }

  /**
   * Retrieves the value of the specified static field from the given class.
   *
   * @param className The fully qualified class name containing the static field.
   * @param fieldName The name of the static field to retrieve.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance getStatic(String className, String fieldName) {
    rootChain.getStatic(className, fieldName);
    return this;
  }

  /**
   * Retrieves the value of the specified static field from the given class and assigns it to a
   * variable.
   *
   * @param className The fully qualified class name containing the static field.
   * @param fieldName The name of the static field to retrieve.
   * @param resultVarName The name to assign to the retrieved field's value.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance getStatic(
      String className, String fieldName, @Nullable String resultVarName) {
    rootChain.getStatic(className, fieldName, resultVarName);
    return this;
  }

  /**
   * Creates a new instance of the specified class with the given variable name and constructor
   * arguments.
   *
   * @param className The fully qualified class name to instantiate.
   * @param varName The variable name to assign to the new instance.
   * @param args The constructor arguments for instantiation.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance create(
      String className, @Nullable String varName, @Nullable Object[] args) {
    return rootChain.create(className, varName, args);
  }

  /**
   * Creates a new instance of the specified class with the given variable name.
   *
   * @param className The fully qualified class name to instantiate.
   * @param varName The variable name to assign to the new instance.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance create(String className, String varName) {
    return rootChain.create(className, varName);
  }

  /**
   * Creates a new instance of the specified class with the given constructor arguments.
   *
   * @param className The fully qualified class name to instantiate.
   * @param args The constructor arguments for instantiation.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance create(String className, Object[] args) {
    return rootChain.create(className, args);
  }

  /**
   * Creates a new instance of the specified class without constructor arguments.
   *
   * @param className The fully qualified class name to instantiate.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance create(String className) {
    return rootChain.create(className);
  }

  /**
   * Associates a variable name with this RpcChainInstance within the RPC chain.
   *
   * @param varName The variable name to associate with this chain's instance.
   * @return This RpcChainInstance for method chaining.
   */
  public RpcChainInstance with(String varName) {
    return rootChain.with(varName);
  }

  /**
   * Sends the accumulated RPC operations for execution.
   *
   * @return The result of executing the RPC operations.
   * @throws Exception If an error occurs during the execution of the RPC operations.
   */
  public RpcChainResult send() throws Exception {
    return rootChain.send();
  }
}
