package net.ittera.pal.dsl.jsonrpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcError;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.ResponseObject;
import net.ittera.pal.serdes.Unwrapper;
import net.ittera.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcChain {
  private static final Logger logger = LoggerFactory.getLogger(RpcChain.class);

  protected final ThinPeer thinPeer;
  protected final List<JsonRpcRequest> requests = new ArrayList<>();
  protected final Map<String, Object> requestIdToValueMap = new HashMap<>(); // requestId -> value
  protected final Map<String, ObjectRef> requestIdToRefMap =
      new HashMap<>(); // requestId -> ObjectRef
  protected RpcChainResult chainResult;
  protected final List<DeferredOperation> operations = new ArrayList<>();

  // Maps and counters
  protected final Map<String, ObjectRef> varNameToInstanceRefMap =
      new HashMap<>(); // varName -> instanceRef
  protected final Map<String, String> requestIdToDeferredVarNameMap =
      new HashMap<>(); // requestId -> varName
  protected final Map<String, String> varNameToTypeMap = new HashMap<>(); // varName -> type
  protected final Map<String, JsonRpcError> requestIdToError =
      new HashMap<>(); // requestId -> error
  protected final IdGenerator idGenerator;

  public RpcChain(ThinPeer thinPeer) {
    this.thinPeer = thinPeer;
    this.idGenerator = new Base62UuidGenerator();
  }

  public RpcChain(ThinPeer thinPeer, IdGenerator idGenerator) {
    this.thinPeer = thinPeer;
    this.idGenerator = idGenerator;
  }

  /**
   * Create a new instance and optionally name it. Returns an RpcChainInstance bound to that new
   * object.
   *
   * @param className The class name to instantiate.
   * @param varName The name to assign to the instance.
   * @param args The arguments to pass to the constructor.
   * @return An RpcChainInstance bound to the new object.
   */
  public RpcChainInstance create(
      String className, @Nullable String varName, @Nullable Object[] args) {
    // if varName is provided, use it, otherwise generate a unique name
    String variableName;
    if (varName != null && !varName.isEmpty()) {
      variableName = varName;
    } else {
      variableName = "_tmpVar" + System.nanoTime();
    }

    addOperation(DeferredOperation.newInstance(className, variableName, args));
    // Store class name immediately since it's known
    storeClassForVarName(variableName, className);
    return new RpcChainInstance(this, null, variableName, className);
  }

  /**
   * Create a new instance without args and name it.
   *
   * @param className The class name to instantiate.
   * @param varName The name to assign to the instance.
   * @return An RpcChainInstance bound to the new object.
   */
  public RpcChainInstance create(String className, String varName) {
    return create(className, varName, null);
  }

  /**
   * Create a new instance, without naming it.
   *
   * @param className The class name to instantiate.
   * @param args The arguments to pass to the constructor.
   * @return An RpcChainInstance bound to the new object.
   */
  public RpcChainInstance create(String className, Object[] args) {
    return create(className, null, args);
  }

  /**
   * Create a new named instance without arguments. This is a convenience method for creating a new
   * object calling its constructor with no arguments.
   *
   * @param className The class name to instantiate.
   * @return An RpcChainInstance bound to the new object.
   */
  public RpcChainInstance create(String className) {
    return create(className, null, null);
  }

  /**
   * Switch to a named instance by its varName.
   *
   * @param varName The name of the instance to switch to.
   * @return An RpcChainInstance bound to the named object.
   */
  public RpcChainInstance with(String varName) {
    ObjectRef ref = varNameToInstanceRefMap.get(varName);
    // even if ref == null, we accept a deferred reference:
    String className = getClassForVarName(varName);
    if (className == null) {
      throw new IllegalArgumentException("No class name found for varName: " + varName);
    }
    return new RpcChainInstance(this, ref, varName, className);
  }

  /**
   * Call a static method on a class. This is a convenience method for calling static methods with
   * no arguments.
   *
   * @param className The class name to call the method on.
   * @param methodName The method name to call.
   * @return this RpcChain for chaining.
   */
  public RpcChain callStatic(String className, String methodName) {
    return callStatic(className, methodName, null, null);
  }

  /**
   * Call a static method on a class. We allow arguments that can be named instances or regular
   * values.
   *
   * @param className The class name to call the method on.
   * @param methodName The method name to call.
   * @param args The arguments to pass to the method.
   * @return this RpcChain for chaining.
   */
  public RpcChain callStatic(String className, String methodName, Object[] args) {
    return callStatic(className, methodName, null, args);
  }

  /**
   * Call a static method on a class. This is a convenience method for calling static methods with
   * no arguments.
   *
   * @param className The class name to call the method on.
   * @param methodName The method name to call.
   * @param resultVarName The name to assign to the result of the method call.
   * @return this RpcChain for chaining.
   */
  public RpcChain callStatic(String className, String methodName, String resultVarName) {
    addOperation(DeferredOperation.staticMethod(className, methodName, resultVarName, null));
    return this;
  }

  /**
   * Call a static method on a class. We allow arguments that can be named instances or regular
   * values.
   *
   * @param className The class name to call the method on.
   * @param methodName The method name to call.
   * @param resultVarName The name to assign to the result of the method call.
   * @param args The arguments to pass to the method.
   * @return this RpcChain for chaining.
   */
  public RpcChain callStatic(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    addOperation(DeferredOperation.staticMethod(className, methodName, resultVarName, args));
    return this;
  }

  public RpcChain getStatic(String className, String fieldName) {
    return getStatic(className, fieldName, null);
  }

  public RpcChain getStatic(String className, String fieldName, @Nullable String resultVarName) {
    addOperation(DeferredOperation.staticFieldGet(className, fieldName, resultVarName));
    return this;
  }

  public RpcChain putStatic(String className, String fieldName, Object value) {
    addOperation(DeferredOperation.staticFieldPut(className, fieldName, value));
    return this;
  }

  protected void addOperation(DeferredOperation op) {
    operations.add(op);
  }

  /**
   * Send all queued requests. Executes them sequentially, resolving references and handling
   * deferred names.
   *
   * @return this RpcChain for chaining.
   */
  public RpcChainResult send() throws Exception {
    for (DeferredOperation op : operations) {
      String requestId = idGenerator.nextId();
      JsonRpcRequest req = buildRequestFromOperation(requestId, op);
      requests.add(req);
      if (op.getResultVarName() != null) {
        requestIdToDeferredVarNameMap.put(requestId, op.getResultVarName());
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Sending request: {}", req);
      }
      JsonRpcResponse response = thinPeer.sendJsonRpcRequestToPeer(req, requestId).get();
      processResponse(req, response);
      if (logger.isDebugEnabled()) {
        logger.debug("Received response: {}", response);
      }
    }

    this.chainResult = new RpcChainResult(getResponseValues());
    return chainResult;
  }

  private JsonRpcRequest buildRequestFromOperation(String id, DeferredOperation op) {
    // Resolve references and arguments
    Object[] rawArgs = op.getArgs();
    List<Argument> argumentList = new ArrayList<>();
    if (rawArgs != null) {
      for (Object arg : rawArgs) {
        argumentList.add(resolveArgument(arg));
      }
    }
    ObjectRef ref;

    switch (op.getOpType()) {
      case NEW_INSTANCE:
        return JsonRpcMessageFactory.buildConstructorCall(id, op.getClassName(), argumentList);
      case STATIC_METHOD:
        return JsonRpcMessageFactory.buildClassMethodCall(
            id, op.getClassName(), op.getMethodName(), argumentList);
      case INSTANCE_METHOD:
        ref = resolveInstanceRef(op.getInstanceVarName(), op.getDirectInstanceRef());
        return JsonRpcMessageFactory.buildInstanceMethodCall(
            id, op.getClassName(), op.getMethodName(), ref.getRef(), argumentList);
      case STATIC_FIELD_GET:
        return JsonRpcMessageFactory.buildStaticFieldGet(id, op.getClassName(), op.getFieldName());
      case STATIC_FIELD_PUT:
        assert rawArgs != null && rawArgs.length == 1;
        Argument valArg = resolveArgument(rawArgs[0]);
        return JsonRpcMessageFactory.buildStaticFieldPut(
            id, op.getClassName(), op.getFieldName(), valArg);
      case INSTANCE_FIELD_GET:
        ref = resolveInstanceRef(op.getInstanceVarName(), op.getDirectInstanceRef());
        return JsonRpcMessageFactory.buildInstanceFieldGet(
            id, op.getClassName(), ref.getRef(), op.getFieldName());
      case INSTANCE_FIELD_PUT:
        ref = resolveInstanceRef(op.getInstanceVarName(), op.getDirectInstanceRef());
        assert rawArgs != null && rawArgs.length == 1;
        Argument valueArg = resolveArgument(rawArgs[0]);
        return JsonRpcMessageFactory.buildInstanceFieldPut(
            id, op.getClassName(), ref.getRef(), op.getFieldName(), valueArg);
      default:
        throw new IllegalArgumentException("Unsupported operation type: " + op.getOpType());
    }
  }

  private List<Map<String, Object>> getResponseValues() {
    List<Map<String, Object>> chainValues = new ArrayList<>();
    for (JsonRpcRequest request : requests) {
      String requestId = request.getId();
      String varName = requestIdToDeferredVarNameMap.get(requestId);
      Object value = requestIdToValueMap.get(requestId);
      ObjectRef objectRef = requestIdToRefMap.get(requestId);
      JsonRpcError error = requestIdToError.get(requestId);
      Map<String, Object> map = new HashMap<>();
      map.put("requestId", requestId);
      map.put("varName", varName);
      map.put("value", value);
      map.put("ref", objectRef);
      map.put("error", error);
      chainValues.add(map);
    }
    return chainValues;
  }

  protected void processResponse(JsonRpcRequest request, JsonRpcResponse response) {
    if (response.getError() != null) {
      requestIdToError.put(request.getId(), response.getError());
      logger.error("Error returned in response: {}", response.getError());
      throw new RuntimeException("Error returned in response: " + response.getError().toString());
    }

    JsonRpcResponseReturnValue responseValue = response.getResult();
    if (responseValue == null) {
      logger.error("Response has no error and no result");
      throw new IllegalStateException("Response has no error and no result");
    }

    if (responseValue.getIsVoid()) {
      // void method, nothing to do
      return;
    }

    // unwrap returned value if any
    ResponseObject responseObject = responseValue.getValue();
    try {
      Object returnedValue = Unwrapper.unwrapObject(responseObject);
      // Store the value in the requestId -> value map
      requestIdToValueMap.put(request.getId(), returnedValue);
    } catch (Exception e) {
      logger.error("Error unwrapping response value", e);
    }

    // get the returned ref if any
    ObjectRef valueRef = null;
    if (responseObject.getRef() != null) {
      valueRef = ObjectRef.from(responseObject.getRef());
      // Store the ref or value in the requestId -> ref map
      requestIdToRefMap.put(request.getId(), valueRef);
    }

    // If varName associated with this requestId, store accordingly
    String varName = requestIdToDeferredVarNameMap.get(request.getId());
    if (varName != null && valueRef != null) {
      // It's a reference, like a 'new' call result
      varNameToInstanceRefMap.put(varName, valueRef);
      // If needed, store class type if available
      if (request.getParams() != null && request.getParams().getType() != null) {
        storeClassForVarName(varName, request.getParams().getType());
      }
    }
  }

  private ObjectRef resolveInstanceRef(String varName, ObjectRef directRef) {
    if (directRef != null) {
      return directRef;
    }
    if (varName != null) {
      return varNameToInstanceRefMap.get(varName);
    }
    return null;
  }

  private Argument resolveArgument(Object arg) {
    if (arg instanceof RpcChainInstance instance) {
      // Resolve instance ref by varName
      String varName = instance.instanceName;
      ObjectRef ref = varNameToInstanceRefMap.get(varName);
      if (ref == null) {
        throw new IllegalStateException("Instance " + varName + " not created before use.");
      }
      return new Argument(ref.getRef());
    } else if (arg instanceof String varName && varNameToInstanceRefMap.containsKey(varName)) {
      // It's a varName referencing a known instanceRef
      ObjectRef instanceRef = varNameToInstanceRefMap.get(varName);
      return new Argument(instanceRef.getRef());
    } else if (arg instanceof Argument existingArg) {
      return existingArg;
    } else {
      // It's a direct value
      return new Argument(arg, arg != null ? arg.getClass().getName() : "java.lang.Object");
    }
  }

  protected void storeClassForVarName(String varName, String className) {
    varNameToTypeMap.put(varName, className);
  }

  protected String getClassForVarName(String varName) {
    return varNameToTypeMap.get(varName);
  }

  public RpcChainResult getChainResult() {
    return chainResult;
  }

  public static Object[] args(Object... args) {
    return args;
  }
}
