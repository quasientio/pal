package net.ittera.pal.serdes.jsonrpc;

import java.util.List;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.util.Base62UuidGenerator;
import net.ittera.pal.common.util.IdGenerator;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.Params;

public class JsonRpcMessageFactory {

  private static final IdGenerator idGenerator = new Base62UuidGenerator();

  private JsonRpcMessageFactory() {}

  public static JsonRpcRequest buildConstructorCall(
      @Nullable String id, String type, List<Argument> arguments) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("new")
        .withParams(Params.builder().withType(type).withArgs(arguments).build())
        .build();
  }

  public static JsonRpcRequest buildConstructorCall(String type, List<Argument> arguments) {
    return buildConstructorCall(null, type, arguments);
  }

  public static JsonRpcRequest buildClassMethodCall(
      @Nullable String id, String type, String method, List<Argument> arguments) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("call")
        .withParams(Params.builder().withType(type).withMethod(method).withArgs(arguments).build())
        .build();
  }

  public static JsonRpcRequest buildClassMethodCall(
      String type, String method, List<Argument> arguments) {
    return buildClassMethodCall(null, type, method, arguments);
  }

  public static JsonRpcRequest buildInstanceMethodCall(
      @Nullable String id,
      String type,
      String method,
      ObjectRef instanceRef,
      List<Argument> arguments) {
    return buildInstanceMethodCall(
        id != null ? id : nextId(), type, method, instanceRef.getRef(), arguments);
  }

  public static JsonRpcRequest buildInstanceMethodCall(
      String type, String method, ObjectRef instanceRef, List<Argument> arguments) {
    return buildInstanceMethodCall(null, type, method, instanceRef, arguments);
  }

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

  public static JsonRpcRequest buildInstanceMethodCall(
      String type, String method, Integer instanceId, List<Argument> arguments) {
    return buildInstanceMethodCall(null, type, method, instanceId, arguments);
  }

  public static JsonRpcRequest buildStaticFieldGet(@Nullable String id, String type, String field) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("get")
        .withParams(Params.builder().withType(type).withField(field).build())
        .build();
  }

  public static JsonRpcRequest buildStaticFieldGet(String type, String field) {
    return buildStaticFieldGet(null, type, field);
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      @Nullable String id, String type, ObjectRef instanceRef, String field) {
    return buildInstanceFieldGet(id != null ? id : nextId(), type, instanceRef.getRef(), field);
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      String type, ObjectRef instanceRef, String field) {
    return buildInstanceFieldGet(null, type, instanceRef, field);
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      @Nullable String id, String type, Integer instanceId, String field) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("get")
        .withParams(
            Params.builder().withType(type).withInstance(instanceId).withField(field).build())
        .build();
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      String type, Integer instanceId, String field) {
    return buildInstanceFieldGet(null, type, instanceId, field);
  }

  public static JsonRpcRequest buildStaticFieldPut(
      @Nullable String id, String type, String field, Argument value) {
    return JsonRpcRequest.builder()
        .withId(id != null ? id : nextId())
        .withMethod("put")
        .withParams(Params.builder().withType(type).withField(field).withValue(value).build())
        .build();
  }

  public static JsonRpcRequest buildStaticFieldPut(String type, String field, Argument value) {
    return buildStaticFieldPut(null, type, field, value);
  }

  public static JsonRpcRequest buildInstanceFieldPut(
      @Nullable String id, String type, ObjectRef instanceRef, String field, Argument value) {
    return buildInstanceFieldPut(
        id != null ? id : nextId(), type, instanceRef.getRef(), field, value);
  }

  public static JsonRpcRequest buildInstanceFieldPut(
      String type, ObjectRef instanceRef, String field, Argument value) {
    return buildInstanceFieldPut(null, type, instanceRef, field, value);
  }

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

  public static JsonRpcRequest buildInstanceFieldPut(
      String type, Integer instanceId, String field, Argument value) {
    return buildInstanceFieldPut(null, type, instanceId, field, value);
  }

  private static String nextId() {
    return idGenerator.nextId();
  }
}
