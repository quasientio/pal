package net.ittera.pal.serdes.jsonrpc;

import java.util.List;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.Params;

public class JsonRpcMessageFactory {

  private JsonRpcMessageFactory() {}

  public static JsonRpcRequest buildConstructorCall(
      String id, String type, List<Argument> arguments) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("new")
        .withParams(new Params.Builder().withType(type).withArgs(arguments).build())
        .build();
  }

  public static JsonRpcRequest buildClassMethodCall(
      String id, String type, String method, List<Argument> arguments) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("call")
        .withParams(
            new Params.Builder().withType(type).withMethod(method).withArgs(arguments).build())
        .build();
  }

  public static JsonRpcRequest buildInstanceMethodCall(
      String id, String type, String method, ObjectRef instanceRef, List<Argument> arguments) {
    return buildInstanceMethodCall(id, type, method, instanceRef.getRef(), arguments);
  }

  public static JsonRpcRequest buildInstanceMethodCall(
      String id, String type, String method, Integer instanceId, List<Argument> arguments) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("call")
        .withParams(
            new Params.Builder()
                .withType(type)
                .withInstance(instanceId)
                .withMethod(method)
                .withArgs(arguments)
                .build())
        .build();
  }

  public static JsonRpcRequest buildStaticFieldGet(String id, String type, String field) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("get")
        .withParams(new Params.Builder().withType(type).withField(field).build())
        .build();
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      String id, String type, ObjectRef instanceRef, String field) {
    return buildInstanceFieldGet(id, type, instanceRef.getRef(), field);
  }

  public static JsonRpcRequest buildInstanceFieldGet(
      String id, String type, Integer instanceId, String field) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("get")
        .withParams(
            new Params.Builder().withType(type).withInstance(instanceId).withField(field).build())
        .build();
  }

  public static JsonRpcRequest buildStaticFieldPut(
      String id, String type, String field, Argument value) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("put")
        .withParams(new Params.Builder().withType(type).withField(field).withValue(value).build())
        .build();
  }

  public static JsonRpcRequest buildInstanceFieldPut(
      String id, String type, ObjectRef instanceRef, String field, Argument value) {
    return buildInstanceFieldPut(id, type, instanceRef.getRef(), field, value);
  }

  public static JsonRpcRequest buildInstanceFieldPut(
      String id, String type, Integer instanceId, String field, Argument value) {
    return new JsonRpcRequest.Builder()
        .withId(id)
        .withMethod("put")
        .withParams(
            new Params.Builder()
                .withType(type)
                .withInstance(instanceId)
                .withField(field)
                .withValue(value)
                .build())
        .build();
  }
}
