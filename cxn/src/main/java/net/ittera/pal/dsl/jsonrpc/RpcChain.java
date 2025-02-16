package net.ittera.pal.dsl.jsonrpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.util.Base62UuidGenerator;
import net.ittera.pal.common.util.IdGenerator;
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

/**
 * RpcChain manages a sequence of JSON-RPC operations to be executed in order. It allows the
 * creation of instances, static method calls, and static field manipulations within a chainable
 * interface. The chain can be executed to send all queued requests to a peer and handle responses
 * accordingly.
 */
public class RpcChain {
  /** Logger for RpcChain operations and debugging. */
  private static final Logger logger = LoggerFactory.getLogger(RpcChain.class);

  /** The ThinPeer instance responsible for sending JSON-RPC requests. */
  protected final ThinPeer thinPeer;

  /** List of JSON-RPC requests queued for execution. */
  protected final List<JsonRpcRequest> requests = new ArrayList<>();

  /** Mapping from request ID to the associated value returned by the response. */
  protected final Map<String, Object> requestIdToValueMap = new HashMap<>(); // requestId -> value

  /** Mapping from request ID to the ObjectRef returned by the response. */
  protected final Map<String, ObjectRef> requestIdToRefMap =
      new HashMap<>(); // requestId -> ObjectRef

  /** The result of the RPC chain after execution. */
  protected RpcChainResult chainResult;

  /** List of deferred operations to be executed as part of the chain. */
  protected final List<DeferredOperation> operations = new ArrayList<>();

  // Maps and counters

  /** Mapping from variable name to the associated ObjectRef instance. */
  protected final Map<String, ObjectRef> varNameToInstanceRefMap =
      new HashMap<>(); // varName -> instanceRef

  /** Mapping from request ID to the deferred variable name associated with the result. */
  protected final Map<String, String> requestIdToDeferredVarNameMap =
      new HashMap<>(); // requestId -> varName

  /** Mapping from variable name to its corresponding type. */
  protected final Map<String, String> varNameToTypeMap = new HashMap<>(); // varName -> type

  /** Mapping from request ID to any error returned in the response. */
  protected final Map<String, JsonRpcError> requestIdToError =
      new HashMap<>(); // requestId -> error

  /** Generator for unique IDs used in JSON-RPC requests. */
  protected final IdGenerator idGenerator;

  /**
   * Constructs an RpcChain with the specified ThinPeer and a default ID generator.
   *
   * @param thinPeer the ThinPeer instance used to send JSON-RPC requests
   */
  public RpcChain(ThinPeer thinPeer) {
    this.thinPeer = thinPeer;
    this.idGenerator = new Base62UuidGenerator();
  }

  /**
   * Constructs an RpcChain with the specified ThinPeer and ID generator.
   *
   * @param thinPeer the ThinPeer instance used to send JSON-RPC requests
   * @param idGenerator the IdGenerator used to generate unique request IDs
   */
  public RpcChain(ThinPeer thinPeer, IdGenerator idGenerator) {
    this.thinPeer = thinPeer;
    this.idGenerator = idGenerator;
  }

  /**
   * Creates a new instance of the specified class and optionally assigns a name to it. Returns an
   * RpcChainInstance bound to the newly created object.
   *
   * @param className the fully qualified name of the class to instantiate
   * @param varName the name to assign to the created instance; if null or empty, a unique name is
   *     generated
   * @param args the arguments to pass to the class constructor; can be null if no arguments are
   *     needed
   * @return an RpcChainInstance representing the newly created object
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
   * Creates a new instance of the specified class with no arguments and assigns a name to it.
   *
   * @param className the fully qualified name of the class to instantiate
   * @param varName the name to assign to the created instance
   * @return an RpcChainInstance representing the newly created object
   */
  public RpcChainInstance create(String className, String varName) {
    return create(className, varName, null);
  }

  /**
   * Creates a new instance of the specified class without assigning a name.
   *
   * @param className the fully qualified name of the class to instantiate
   * @param args the arguments to pass to the class constructor
   * @return an RpcChainInstance representing the newly created object
   */
  public RpcChainInstance create(String className, Object[] args) {
    return create(className, null, args);
  }

  /**
   * Creates a new instance of the specified class without arguments or an assigned name.
   *
   * @param className the fully qualified name of the class to instantiate
   * @return an RpcChainInstance representing the newly created object
   */
  public RpcChainInstance create(String className) {
    return create(className, null, null);
  }

  /**
   * Switches the context to an existing named instance identified by its variable name.
   *
   * @param varName the name of the existing instance to switch to
   * @return an RpcChainInstance bound to the specified named object
   * @throws IllegalArgumentException if no class name is found for the provided variable name
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
   * Calls a static method on the specified class with no arguments. This is a convenience method
   * for invoking static methods without parameters.
   *
   * @param className the fully qualified name of the class containing the static method
   * @param methodName the name of the static method to invoke
   * @return this RpcChain instance for method chaining
   */
  public RpcChain callStatic(String className, String methodName) {
    return callStatic(className, methodName, null, null);
  }

  /**
   * Calls a static method on the specified class with the provided arguments. Arguments can be
   * named instances or regular values.
   *
   * @param className the fully qualified name of the class containing the static method
   * @param methodName the name of the static method to invoke
   * @param args the arguments to pass to the static method; can be null if no arguments are needed
   * @return this RpcChain instance for method chaining
   */
  public RpcChain callStatic(String className, String methodName, Object[] args) {
    return callStatic(className, methodName, null, args);
  }

  /**
   * Calls a static method on the specified class and assigns the result to a variable. This is a
   * convenience method for invoking static methods without parameters and storing the result.
   *
   * @param className the fully qualified name of the class containing the static method
   * @param methodName the name of the static method to invoke
   * @param resultVarName the name of the variable to assign the result to
   * @return this RpcChain instance for method chaining
   */
  public RpcChain callStatic(String className, String methodName, String resultVarName) {
    addOperation(DeferredOperation.staticMethod(className, methodName, resultVarName, null));
    return this;
  }

  /**
   * Calls a static method on the specified class with the provided arguments and assigns the result
   * to a variable. Arguments can be named instances or regular values.
   *
   * @param className the fully qualified name of the class containing the static method
   * @param methodName the name of the static method to invoke
   * @param resultVarName the name of the variable to assign the result to; can be null if the
   *     result is not to be stored
   * @param args the arguments to pass to the static method
   * @return this RpcChain instance for method chaining
   */
  public RpcChain callStatic(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    addOperation(DeferredOperation.staticMethod(className, methodName, resultVarName, args));
    return this;
  }

  /**
   * Retrieves the value of a static field from the specified class.
   *
   * @param className the fully qualified name of the class containing the static field
   * @param fieldName the name of the static field to retrieve
   * @return this RpcChain instance for method chaining
   */
  public RpcChain getStatic(String className, String fieldName) {
    return getStatic(className, fieldName, null);
  }

  /**
   * Retrieves the value of a static field from the specified class and assigns it to a variable.
   *
   * @param className the fully qualified name of the class containing the static field
   * @param fieldName the name of the static field to retrieve
   * @param resultVarName the name of the variable to assign the field's value to; can be null if
   *     the value is not to be stored
   * @return this RpcChain instance for method chaining
   */
  public RpcChain getStatic(String className, String fieldName, @Nullable String resultVarName) {
    addOperation(DeferredOperation.staticFieldGet(className, fieldName, resultVarName));
    return this;
  }

  /**
   * Sets the value of a static field in the specified class.
   *
   * @param className the fully qualified name of the class containing the static field
   * @param fieldName the name of the static field to set
   * @param value the value to assign to the static field
   * @return this RpcChain instance for method chaining
   */
  public RpcChain putStatic(String className, String fieldName, Object value) {
    addOperation(DeferredOperation.staticFieldPut(className, fieldName, value));
    return this;
  }

  /**
   * Adds a deferred operation to the execution queue.
   *
   * @param op the DeferredOperation to add to the queue
   */
  protected void addOperation(DeferredOperation op) {
    operations.add(op);
  }

  /**
   * Sends all queued JSON-RPC requests to the peer. Executes the operations sequentially, resolves
   * references, and handles deferred variable names.
   *
   * @return an RpcChainResult containing the results of the executed operations
   * @throws Exception if an error occurs while sending requests or processing responses
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

  /**
   * Builds a JsonRpcRequest based on the provided DeferredOperation.
   *
   * @param id the unique identifier for the JSON-RPC request
   * @param op the DeferredOperation defining the operation to perform
   * @return a JsonRpcRequest representing the operation
   * @throws IllegalArgumentException if the operation type is unsupported
   */
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

  /**
   * Retrieves the values from all responses and organizes them into a list of maps.
   *
   * @return a list of maps containing response details for each request
   */
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

  /**
   * Processes a JSON-RPC response, updating internal mappings based on the response content.
   *
   * @param request the original JsonRpcRequest that was sent
   * @param response the JsonRpcResponse received in reply
   * @throws RuntimeException if the response contains an error
   * @throws IllegalStateException if the response lacks both a result and an error
   */
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

  /**
   * Resolves the ObjectRef for an instance based on the variable name or a direct reference.
   *
   * @param varName the name of the variable referencing the instance; can be null
   * @param directRef a direct ObjectRef to use; takes precedence over varName
   * @return the resolved ObjectRef, or null if neither varName nor directRef is provided
   */
  private ObjectRef resolveInstanceRef(String varName, ObjectRef directRef) {
    if (directRef != null) {
      return directRef;
    }
    if (varName != null) {
      return varNameToInstanceRefMap.get(varName);
    }
    return null;
  }

  /**
   * Resolves an argument to be included in a JSON-RPC request. If the argument is an
   * RpcChainInstance, it resolves to its ObjectRef. If the argument is a string referencing a known
   * instance, it resolves to its ObjectRef. Otherwise, it wraps the argument as a direct value.
   *
   * @param arg the original argument
   * @return an Argument object representing the resolved argument
   * @throws IllegalStateException if the referenced instance has not been created
   */
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

  /**
   * Stores the class name associated with a variable name.
   *
   * @param varName the name of the variable
   * @param className the fully qualified class name to associate with the variable
   */
  protected void storeClassForVarName(String varName, String className) {
    varNameToTypeMap.put(varName, className);
  }

  /**
   * Retrieves the class name associated with a given variable name.
   *
   * @param varName the name of the variable
   * @return the fully qualified class name associated with the variable, or null if not found
   */
  protected String getClassForVarName(String varName) {
    return varNameToTypeMap.get(varName);
  }

  /**
   * Retrieves the result of the RPC chain after execution.
   *
   * @return the RpcChainResult containing the outcomes of the executed operations
   */
  public RpcChainResult getChainResult() {
    return chainResult;
  }

  /**
   * Creates an array of arguments for use in RPC operations.
   *
   * @param args the arguments to include in the array
   * @return an array of objects representing the arguments
   */
  public static Object[] args(Object... args) {
    return args;
  }
}
