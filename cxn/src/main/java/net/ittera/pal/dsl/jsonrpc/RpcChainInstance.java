package net.ittera.pal.dsl.jsonrpc;

import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;

public class RpcChainInstance {
  private final RpcChain rootChain;
  protected final ObjectRef instanceRef;
  protected final String instanceName;
  protected final String className;

  public RpcChainInstance(
      RpcChain rootChain, ObjectRef instanceRef, String instanceName, String className) {
    this.rootChain = rootChain;
    this.instanceRef = instanceRef;
    this.instanceName = instanceName;
    this.className = className;
  }

  /**
   * Call an instance method on this instance. This is a convenience method for calling instance
   * methods without arguments.
   *
   * @param methodName The method name to call.
   * @return this RpcChainInstance for chaining.
   */
  public RpcChainInstance call(String methodName) {
    return call(methodName, null, null);
  }

  /**
   * Call an instance method on this instance.
   *
   * @param methodName The method name to call.
   * @param args The arguments to pass to the method.
   * @return this RpcChainInstance for chaining.
   */
  public RpcChainInstance call(String methodName, Object[] args) {
    return call(methodName, null, args);
  }

  /**
   * Call an instance method on this instance. This is a convenience method for calling instance
   * methods without arguments.
   *
   * @param methodName The method name to call.
   * @param resultVarName The name to assign to the result of the method call.
   * @return this RpcChainInstance for chaining.
   */
  public RpcChainInstance call(String methodName, String resultVarName) {
    return call(methodName, resultVarName, null);
  }

  /**
   * Call an instance method on this instance.
   *
   * @param methodName The method name to call.
   * @param resultVarName The name to assign to the result of the method call.
   * @param args The arguments to pass to the method.
   * @return this RpcChainInstance for chaining.
   */
  public RpcChainInstance call(String methodName, @Nullable String resultVarName, Object[] args) {
    rootChain.addOperation(
        DeferredOperation.instanceMethod(
            className, methodName, resultVarName, instanceName, instanceRef, args));
    return this;
  }

  public RpcChainInstance get(String fieldName) {
    return get(fieldName, null);
  }

  public RpcChainInstance get(String fieldName, @Nullable String resultVarName) {
    rootChain.addOperation(
        DeferredOperation.instanceFieldGet(
            className, fieldName, resultVarName, instanceName, instanceRef));
    return this;
  }

  public RpcChainInstance put(String fieldName, Object value) {
    rootChain.addOperation(
        DeferredOperation.instanceFieldPut(className, fieldName, value, instanceName, instanceRef));
    return this;
  }

  /* Delegate methods */
  public RpcChainInstance callStatic(String className, String methodName, Object[] args) {
    rootChain.callStatic(className, methodName, args);
    return this;
  }

  public RpcChainInstance callStatic(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    rootChain.callStatic(className, methodName, resultVarName, args);
    return this;
  }

  public RpcChainInstance getStatic(String className, String fieldName) {
    rootChain.getStatic(className, fieldName);
    return this;
  }

  public RpcChainInstance getStatic(
      String className, String fieldName, @Nullable String resultVarName) {
    rootChain.getStatic(className, fieldName, resultVarName);
    return this;
  }

  public RpcChainInstance create(
      String className, @Nullable String varName, @Nullable Object[] args) {
    return rootChain.create(className, varName, args);
  }

  public RpcChainInstance create(String className, String varName) {
    return rootChain.create(className, varName);
  }

  public RpcChainInstance create(String className, Object[] args) {
    return rootChain.create(className, args);
  }

  public RpcChainInstance create(String className) {
    return rootChain.create(className);
  }

  public RpcChainInstance with(String varName) {
    return rootChain.with(varName);
  }

  public RpcChainResult send() throws Exception {
    return rootChain.send();
  }
}
