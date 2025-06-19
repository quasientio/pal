package com.quasient.pal.serdes.jsonrpc;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.util.Base62UuidGenerator;
import com.quasient.pal.common.util.IdGenerator;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.messages.types.ControlCommandType;
import com.quasient.pal.messages.types.MessageFamily;
import com.quasient.pal.messages.types.MetaServiceType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Factory class for creating {@link JsonRpcRequest} messages for various operations such as
 * constructor invocations, method calls, field accesses, and control commands.
 */
public class JsonRpcMessageFactory {

  /** Generator for unique identifiers used in {@link JsonRpcRequest} messages. */
  private static final IdGenerator idGenerator = new Base62UuidGenerator();

  /** Private constructor to prevent instantiation of this utility class. */
  private JsonRpcMessageFactory() {}

  // <editor-fold desc="Exec messages">

  /**
   * Constructs a JSON-RPC request to invoke a constructor of the specified type with given
   * arguments.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class to instantiate
   * @param arguments the list of arguments to pass to the constructor
   * @return a {@link JsonRpcRequest} representing the constructor call
   */
  public static JsonRpcRequest buildConstructorCall(
      @Nullable String id, String type, List<Argument> arguments) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("new")
        .withParams(Params.builder().withType(type).withArgs(arguments).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to invoke a constructor of the specified type with given
   * arguments. A unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class to instantiate
   * @param arguments the list of arguments to pass to the constructor
   * @return a {@link JsonRpcRequest} representing the constructor call
   */
  public static JsonRpcRequest buildConstructorCall(String type, List<Argument> arguments) {
    return buildConstructorCall(null, type, arguments);
  }

  /**
   * Constructs a JSON-RPC request to invoke a class method with specified arguments.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the class method call
   */
  public static JsonRpcRequest buildClassMethodCall(
      @Nullable String id, String type, String method, List<Argument> arguments) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("call")
        .withParams(Params.builder().withType(type).withMethod(method).withArgs(arguments).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to invoke a class method with specified arguments. A unique
   * identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the class method call
   */
  public static JsonRpcRequest buildClassMethodCall(
      String type, String method, List<Argument> arguments) {
    return buildClassMethodCall(null, type, method, arguments);
  }

  /**
   * Constructs a JSON-RPC request to invoke an instance method on a specific object instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param instanceRef a reference to the target object instance
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the instance method call
   */
  public static JsonRpcRequest buildInstanceMethodCall(
      @Nullable String id,
      String type,
      String method,
      ObjectRef instanceRef,
      List<Argument> arguments) {
    return buildInstanceMethodCall(
        id != null ? id : nextId(), type, method, instanceRef.getRef(), arguments);
  }

  /**
   * Constructs a JSON-RPC request to invoke an instance method on a specific object instance. A
   * unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param instanceRef a reference to the target object instance
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the instance method call
   */
  public static JsonRpcRequest buildInstanceMethodCall(
      String type, String method, ObjectRef instanceRef, List<Argument> arguments) {
    return buildInstanceMethodCall(null, type, method, instanceRef, arguments);
  }

  /**
   * Constructs a JSON-RPC request to invoke an instance method on a specific object instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param instanceId the identifier of the target object instance
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the instance method call
   */
  public static JsonRpcRequest buildInstanceMethodCall(
      @Nullable String id,
      String type,
      String method,
      Integer instanceId,
      List<Argument> arguments) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("call")
        .withParams(
            Params.builder()
                .withType(type)
                .withInstance(instanceId)
                .withMethod(method)
                .withArgs(arguments)
                .build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to invoke an instance method on a specific object instance. A
   * unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the method
   * @param method the name of the method to invoke
   * @param instanceId the identifier of the target object instance
   * @param arguments the list of arguments to pass to the method
   * @return a {@link JsonRpcRequest} representing the instance method call
   */
  public static JsonRpcRequest buildInstanceMethodCall(
      String type, String method, Integer instanceId, List<Argument> arguments) {
    return buildInstanceMethodCall(null, type, method, instanceId, arguments);
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of a static field.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param field the name of the static field to retrieve
   * @return a {@link JsonRpcRequest} representing the static field retrieval
   */
  public static JsonRpcRequest buildStaticFieldGet(@Nullable String id, String type, String field) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("get")
        .withParams(Params.builder().withType(type).withField(field).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of a static field. A unique identifier is
   * automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param field the name of the static field to retrieve
   * @return a {@link JsonRpcRequest} representing the static field retrieval
   */
  public static JsonRpcRequest buildStaticFieldGet(String type, String field) {
    return buildStaticFieldGet(null, type, field);
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of an instance field from a specific object
   * instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param instanceRef a reference to the target object instance
   * @param field the name of the instance field to retrieve
   * @return a {@link JsonRpcRequest} representing the instance field retrieval
   */
  public static JsonRpcRequest buildInstanceFieldGet(
      @Nullable String id, String type, ObjectRef instanceRef, String field) {
    return buildInstanceFieldGet(id != null ? id : nextId(), type, instanceRef.getRef(), field);
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of an instance field from a specific object
   * instance. A unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param instanceRef a reference to the target object instance
   * @param field the name of the instance field to retrieve
   * @return a {@link JsonRpcRequest} representing the instance field retrieval
   */
  public static JsonRpcRequest buildInstanceFieldGet(
      String type, ObjectRef instanceRef, String field) {
    return buildInstanceFieldGet(null, type, instanceRef, field);
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of an instance field from a specific object
   * instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param instanceId the identifier of the target object instance
   * @param field the name of the instance field to retrieve
   * @return a {@link JsonRpcRequest} representing the instance field retrieval
   */
  public static JsonRpcRequest buildInstanceFieldGet(
      @Nullable String id, String type, Integer instanceId, String field) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("get")
        .withParams(
            Params.builder().withType(type).withInstance(instanceId).withField(field).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to retrieve the value of an instance field from a specific object
   * instance. A unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param instanceId the identifier of the target object instance
   * @param field the name of the instance field to retrieve
   * @return a {@link JsonRpcRequest} representing the instance field retrieval
   */
  public static JsonRpcRequest buildInstanceFieldGet(
      String type, Integer instanceId, String field) {
    return buildInstanceFieldGet(null, type, instanceId, field);
  }

  /**
   * Constructs a JSON-RPC request to set the value of a static field.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param field the name of the static field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the static field assignment
   */
  public static JsonRpcRequest buildStaticFieldPut(
      @Nullable String id, String type, String field, Argument value) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("put")
        .withParams(Params.builder().withType(type).withField(field).withValue(value).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to set the value of a static field. A unique identifier is
   * automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param field the name of the static field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the static field assignment
   */
  public static JsonRpcRequest buildStaticFieldPut(String type, String field, Argument value) {
    return buildStaticFieldPut(null, type, field, value);
  }

  /**
   * Constructs a JSON-RPC request to set the value of an instance field on a specific object
   * instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param instanceRef a reference to the target object instance
   * @param field the name of the instance field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the instance field assignment
   */
  public static JsonRpcRequest buildInstanceFieldPut(
      @Nullable String id, String type, ObjectRef instanceRef, String field, Argument value) {
    return buildInstanceFieldPut(
        id != null ? id : nextId(), type, instanceRef.getRef(), field, value);
  }

  /**
   * Constructs a JSON-RPC request to set the value of an instance field on a specific object
   * instance. A unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param instanceRef a reference to the target object instance
   * @param field the name of the instance field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the instance field assignment
   */
  public static JsonRpcRequest buildInstanceFieldPut(
      String type, ObjectRef instanceRef, String field, Argument value) {
    return buildInstanceFieldPut(null, type, instanceRef, field, value);
  }

  /**
   * Constructs a JSON-RPC request to set the value of an instance field on a specific object
   * instance.
   *
   * @param id the unique identifier for the request; if {@code null}, a new ID is generated
   * @param type the fully qualified name of the class containing the field
   * @param instanceId the identifier of the target object instance
   * @param field the name of the instance field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the instance field assignment
   */
  public static JsonRpcRequest buildInstanceFieldPut(
      @Nullable String id, String type, Integer instanceId, String field, Argument value) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("put")
        .withParams(
            Params.builder()
                .withType(type)
                .withInstance(instanceId)
                .withField(field)
                .withValue(value)
                .build())
        .build();
  }

  /**
   * Constructs a JSON-RPC request to set the value of an instance field on a specific object
   * instance. A unique identifier is automatically generated for the request.
   *
   * @param type the fully qualified name of the class containing the field
   * @param instanceId the identifier of the target object instance
   * @param field the name of the instance field to set
   * @param value the new value to assign to the field
   * @return a {@link JsonRpcRequest} representing the instance field assignment
   */
  public static JsonRpcRequest buildInstanceFieldPut(
      String type, Integer instanceId, String field, Argument value) {
    return buildInstanceFieldPut(null, type, instanceId, field, value);
  }

  // </editor-fold>

  // <editor-fold desc="Meta messages">

  /**
   * Constructs a JSON-RPC meta message to fetch information about classes, optionally excluding
   * classes with specified prefixes, or only those explicitly required to be included.
   *
   * @param includeClasses an array of class names to include from the fetched information; if
   *     given, all others will be excluded; may be {@code null} or empty
   * @param excludePrefixes an array of class name prefixes to exclude from the fetched information;
   *     may be {@code null} or empty to include all classes
   * @param compressAndEncode indicates whether the returned metadata should be Gzipped and
   *     Base64-encoded; if {@code false}, class metadata will be returned as plain JSON
   * @param mergeAncestry indicates whether to include methods and fields inherited from interfaces
   *     and superclasses
   * @return a {@link JsonRpcRequest} representing the fetch classes information meta message
   */
  public static JsonRpcRequest buildFetchClassesInfoMetaMessage(
      @Nullable String[] includeClasses,
      @Nullable String[] excludePrefixes,
      boolean compressAndEncode,
      boolean mergeAncestry) {

    /* Create args list */
    List<Argument> args = new ArrayList<>();
    // compress_encode
    args.add(Argument.builder().withName("compress_encode").withValue(compressAndEncode).build());
    // merge_ancestry
    args.add(Argument.builder().withName("merge_ancestry").withValue(mergeAncestry).build());

    // exclude_prefixes
    if (excludePrefixes != null && excludePrefixes.length > 0) {
      args.add(Argument.builder().withName("exclude_prefixes").withValue(excludePrefixes).build());
    }
    // include_classes
    if (includeClasses != null && includeClasses.length > 0) {
      args.add(Argument.builder().withName("include_classes").withValue(includeClasses).build());
    }

    return JsonRpcRequest.builder()
        .withId(nextId())
        .withMethod(MessageFamily.META.getJsonName())
        .withParams(
            Params.builder()
                .withMethod(MetaServiceType.FETCH_CLASSES_INFO.getJsonName())
                .withArgs(args)
                .build())
        .build();
  }

  // </editor-fold>

  // <editor-fold desc="Control messages">

  /**
   * Constructs a JSON-RPC control message to remove an object reference, given the specified
   * identifier, from the caller's session.
   *
   * @param objId the identifier of the object reference to delete. That is, the objectRef given as
   *     integer.
   * @return a {@link JsonRpcRequest} representing the delete object command
   */
  public static JsonRpcRequest buildDeleteObjectCommandMessage(Integer objId) {
    return JsonRpcRequest.builder()
        .withId(nextId())
        .withMethod(MessageFamily.CONTROL.getJsonName())
        .withParams(
            Params.builder()
                .withMethod(ControlCommandType.DELETE_OBJECT.getJsonName())
                .addArg(Argument.builder().withRef(objId).build())
                .build())
        .build();
  }

  /**
   * Constructs a JSON-RPC control message to remove an object reference, given its {@link
   * ObjectRef}, from the caller's session.
   *
   * @param objRef a reference to the object to delete
   * @return a {@link JsonRpcRequest} representing the delete object command
   */
  public static JsonRpcRequest buildDeleteObjectCommandMessage(ObjectRef objRef) {
    return buildDeleteObjectCommandMessage(objRef.getRef());
  }

  /**
   * Constructs a JSON-RPC control message to delete the current caller's session.
   *
   * @return a {@link JsonRpcRequest} representing the delete session command
   */
  public static JsonRpcRequest buildDeleteSessionCommandMessage() {
    return JsonRpcRequest.builder()
        .withId(nextId())
        .withMethod(MessageFamily.CONTROL.getJsonName())
        .withParams(
            Params.builder().withMethod(ControlCommandType.DELETE_SESSION.getJsonName()).build())
        .build();
  }

  /**
   * Constructs a JSON-RPC control message to trigger garbage collection.
   *
   * @return a {@link JsonRpcRequest} representing the garbage collection command
   */
  public static JsonRpcRequest buildGcCommandMessage() {
    return JsonRpcRequest.builder()
        .withId(nextId())
        .withMethod(MessageFamily.CONTROL.getJsonName())
        .withParams(Params.builder().withMethod(ControlCommandType.GC.getJsonName()).build())
        .build();
  }

  // </editor-fold>

  /**
   * Generates the next unique identifier for a JSON-RPC request.
   *
   * @return a new unique identifier as a {@code String}
   */
  private static String nextId() {
    return idGenerator.nextId();
  }
}
