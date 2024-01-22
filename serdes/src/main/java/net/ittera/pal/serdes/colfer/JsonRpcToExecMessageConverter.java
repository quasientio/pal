package net.ittera.pal.serdes.colfer;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.messages.colfer.ConstructorCall;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;

public class JsonRpcToExecMessageConverter {
  private JsonRpcRequest parseJsonRpcMessage(String jsonRpcMessage) {
    // Parse the JSON-RPC message and return a JsonRpcRequest object
    // This requires JSON parsing logic, possibly using a library like Jackson or Gson
    Gson gson = new Gson();
    return gson.fromJson(jsonRpcMessage, JsonRpcRequest.class);
  }

  public ExecMessage convertJsonRpcToExecMessage(String jsonRpcMessage, UUID fromPeerUuid) {
    // 1. Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = parseJsonRpcMessage(jsonRpcMessage);

    // 2. Create an instance of ExecMessage and initialize required fields
    ExecMessage execMessage = new ExecMessage();
    execMessage.setPeerUuid(fromPeerUuid.toString());
    execMessage.setMessageUuid(UUID.randomUUID().toString());
    // execMessage.setExecMessageType((byte) 0); TODO
    // currentTime TODO

    // threadName ?
    // dispatchSeq ?
    // builderSeq ?
    // followingUuid ?

    // 3. Create a ConstructorCall instance and fill it based on the JSON-RPC request
    ConstructorCall constructorCall = new ConstructorCall();
    //        constructorCall.setClazz(new Class().withName(jsonRpcRequest.getClassName()));
    constructorCall.setParameters(convertJsonParamsToColferParams(jsonRpcRequest.getParams()));
    // TODO modifiers
    // TODO context

    // 4. Set the constructorCall in execMessage
    execMessage.setConstructorCall(constructorCall);

    return execMessage;
  }

  private Parameter[] convertJsonParamsToColferParams(List<JsonRpcParameter> jsonParams) {
    // Convert JSON-RPC parameters to Colfer Parameter objects
    // Loop through jsonParams and create Parameter objects
    return null;
  }
}
