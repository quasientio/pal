/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.rpc.AbstractRpcMessageIT;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractJsonRpcMessageIT extends AbstractRpcMessageIT
    implements JsonRpcMessageAssertions {

  protected AbstractJsonRpcMessageIT(TargetType targetType) {
    super(targetType);
  }

  protected AbstractJsonRpcMessageIT() {
    this(TargetType.PEER);
  }

  @BeforeClass
  public static void initialize() throws Exception {
    logger.debug("Initializing before tests...");
    directoryConnectionProvider = new DirectoryConnectionProvider(getPalDirectoryUrl());

    // configure wiring
    AbstractModule module =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Properties appProperties = new Properties();
            appProperties.setProperty("messages.with_src_context", Boolean.toString(false));
            Names.bindProperties(binder(), appProperties);
            bind(MessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    final Properties consumerProperties = getKafkaConsumerProperties();
    final Properties producerProperties = getKafkaProducerProperties();

    // find a peer listening with JSON-RPC enabled
    PeerInfo jsonRpcPeer =
        findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider)
            .orElseThrow(() -> new RuntimeException("No peer found with JSON-RPC enabled"));
    thinPeer =
        new ThinPeer()
            .withUuid(clientId)
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .withInitialPeer(jsonRpcPeer)
            .withOutboundRpcType(RpcType.JSON_RPC)
            .init();
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after json-rpc tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
    if (directoryConnectionProvider != null) {
      directoryConnectionProvider.get().ifPresent(PalDirectory::close);
    }
  }

  protected JsonRpcResponse sendAndReceive(String jsonRpcRequest)
      throws ExecutionException, InterruptedException, JsonSerializationException {
    return sendAndReceive(jsonRpcRequest, null);
  }

  protected JsonRpcResponse sendAndReceive(JsonRpcRequest jsonRpcRequest)
      throws JsonSerializationException, ExecutionException, InterruptedException {
    return sendAndReceive(JsonRpcSerializer.toJson(jsonRpcRequest));
  }

  /* Include the messageId in order to avoid client-side parsing (by ThinPeer) and
   * cause parsing exceptions to happen on the server (i.e. remote peer) */
  protected JsonRpcResponse sendAndReceive(Object jsonRpcRequest, String messageId)
      throws ExecutionException, InterruptedException, JsonSerializationException {
    logger.debug("Sending JSON-RPC request: {}", jsonRpcRequest);
    final JsonRpcResponse response;
    try {
      if (targetType.equals(TargetType.PEER)) {
        logger.debug("Sending message w/id: {} to peer", messageId);
        response = thinPeer.sendJsonRpcRequestToPeer(jsonRpcRequest, messageId).get();
        logger.debug("Received response for message w/id: {}", messageId);
        if (logger.isTraceEnabled()) {
          logger.trace("Received response: {}", response);
        }
      } else {
        logger.debug("Sending message w/id: {} to log", messageId);
        LogMessage<JsonRpcResponse> responseLogMessage =
            thinPeer.sendJsonRpcRequestToLogAndReceive(jsonRpcRequest);
        logger.debug("Received response for message w/id: {}", messageId);
        if (logger.isTraceEnabled()) {
          logger.trace("Received response: {}", responseLogMessage);
        }
        response = responseLogMessage.getContent();
      }
    } catch (Exception e) {
      logger.error("Exception sending/receiving message: {}", jsonRpcRequest, e);
      throw e;
    }
    return response;
  }

  // <editor-fold desc="exec methods">
  protected JsonRpcResponse callGetStaticField(String className, String fieldName)
      throws Exception {
    String request =
            """
            {
              "jsonrpc": "2.0",
              "method": "get",
              "params": {
                "type": "%s",
                "field": "%s"
              },
              "id": %s
            }
            """
            .formatted(className, fieldName, generateId());

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callPutStaticField(
      String className, String fieldName, String fieldValue) throws Exception {
    String request =
            """
             {
               "jsonrpc": "2.0",
               "method": "put",
               "params": {
                 "type": "%s",
                 "field": "%s",
                 "value": %s
               },
               "id": %s
             }
             """
            .formatted(className, fieldName, fieldValue, generateId());

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callGetInstanceField(
      String className, String fieldName, long instanceRef) throws Exception {
    String request =
            """
            {
              "jsonrpc": "2.0",
              "method": "get",
              "params": {
                "type": "%s",
                "field": "%s",
                "instance": %d
              },
              "id": %s
            }
            """
            .formatted(className, fieldName, instanceRef, generateId());

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callPutInstanceField(
      String className, String fieldName, long instanceRef, String fieldValue) throws Exception {
    String request =
            """
            {
              "jsonrpc": "2.0",
              "method": "put",
              "params": {
                "type": "%s",
                "field": "%s",
                "instance": %d,
                "value": %s
              },
              "id": %s
            }
            """
            .formatted(className, fieldName, instanceRef, fieldValue, generateId());

    return sendAndReceive(request);
  }

  /**
   * Helper method to call a class method.
   *
   * @param className the class name
   * @param methodName the method name
   * @param argsJson a JSON array of arguments or null if no args
   * @return JsonRpcResponse
   */
  protected JsonRpcResponse callClassMethod(String className, String methodName, String argsJson)
      throws Exception {

    String request;
    if (argsJson == null) {
      // no arguments
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %s,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s"
                }
              }
              """
              .formatted(generateId(), className, methodName);
    } else {
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %s,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "args": %s
                }
              }
              """
              .formatted(generateId(), className, methodName, argsJson);
    }

    return sendAndReceive(request);
  }

  /**
   * Helper method to call an instance method.
   *
   * @param instanceRef the reference to the instance
   * @param className the class name
   * @param methodName the method name
   * @param argsJson a JSON array of arguments or null if no args
   * @return JsonRpcResponse
   */
  protected JsonRpcResponse callInstanceMethod(
      long instanceRef, String className, String methodName, String argsJson) throws Exception {

    String request;
    if (argsJson == null) {
      // no arguments
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %s,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "instance": %d
                }
              }
              """
              .formatted(generateId(), className, methodName, instanceRef);
    } else {
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %s,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "instance": %d,
                  "args": %s
                }
              }
              """
              .formatted(generateId(), className, methodName, instanceRef, argsJson);
    }

    return sendAndReceive(request);
  }

  protected Integer createNewInstance(String className) throws Exception {
    String request =
            """
            {
              "jsonrpc": "2.0",
              "method": "new",
              "params": {
                "type": "%s"
              },
              "id": %s
            }
            """
            .formatted(className, generateId());

    JsonRpcResponse response = sendAndReceive(request);
    assertNotNull(response.getResult());
    assertNotNull(response.getResult().getValue());
    return response.getResult().getValue().getRef();
  }

  protected static String arrayToJsonString(Object value, boolean addNumericSuffix) {
    if (value == null) {
      logger.debug("arrayToJsonString with null value");
      return "null";
    }

    logger.debug("arrayToJsonString with value: {} of class: {}", value, value.getClass());

    Object[] wrapperArray = unwrapArray(value);
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < wrapperArray.length; i++) {
      Object elem = wrapperArray[i];
      if (elem == null) {
        sb.append("null");
      } else if (elem instanceof String || elem instanceof Character) {
        sb.append("\"").append(elem).append("\"");
      } else if (elem instanceof Double || elem instanceof Float || elem instanceof Long) {
        sb.append(elem);
        // Handle numeric types with suffixes for JSON-RPC compatibility
        if (addNumericSuffix && elem instanceof Double) {
          sb.append("d"); // Add 'd' for double
        } else if (addNumericSuffix && elem instanceof Float) {
          sb.append("f"); // Add 'f' for float
        } else if (addNumericSuffix) { // elem instanceof Long
          sb.append("l"); // Add 'l' for long
        }
      } else {
        // Boolean or Integer (other types don't need special handling)
        sb.append(elem);
      }
      if (i < wrapperArray.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  // Helper method to unwrap arrays (handles primitive arrays)
  protected static Object[] unwrapArray(Object array) {
    if (array == null) {
      return null;
    }
    if (array instanceof Object[]) {
      return (Object[]) array;
    } else if (array instanceof boolean[] primitiveArray) {
      Boolean[] wrapperArray = new Boolean[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof byte[] primitiveArray) {
      Byte[] wrapperArray = new Byte[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof char[] primitiveArray) {
      Character[] wrapperArray = new Character[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof short[] primitiveArray) {
      Short[] wrapperArray = new Short[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof int[] primitiveArray) {
      Integer[] wrapperArray = new Integer[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof long[] primitiveArray) {
      Long[] wrapperArray = new Long[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof float[] primitiveArray) {
      Float[] wrapperArray = new Float[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else if (array instanceof double[] primitiveArray) {
      Double[] wrapperArray = new Double[primitiveArray.length];
      for (int i = 0; i < primitiveArray.length; i++) {
        wrapperArray[i] = primitiveArray[i];
      }
      return wrapperArray;
    } else {
      // Should not reach here
      throw new IllegalArgumentException("Unsupported array type: " + array.getClass());
    }
  }

  // </editor-fold>

  // <editor-fold desc="control methods">
  protected boolean sendDeleteObjectCommand(ObjectRef ref) throws Exception {
    JsonRpcRequest request = JsonRpcMessageFactory.buildDeleteObjectCommandMessage(ref);
    JsonRpcResponse response = thinPeer.sendJsonRpcRequestToPeer(request).get();
    logger.debug("response to delete object command: {}", response);
    return response.getError() == null && response.getResult().getIsVoid();
  }

  protected boolean sendDeleteSessionCommand() throws Exception {
    JsonRpcRequest request = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();
    JsonRpcResponse response = thinPeer.sendJsonRpcRequestToPeer(request).get();
    logger.debug("response to delete session command: {}", response);
    return response.getError() == null && response.getResult().getIsVoid();
  }

  protected boolean sendGcCommand() throws Exception {
    JsonRpcRequest request = JsonRpcMessageFactory.buildGcCommandMessage();
    JsonRpcResponse response = thinPeer.sendJsonRpcRequestToPeer(request).get();
    logger.debug("response to GC command: {}", response);
    return response.getError() == null && response.getResult().getIsVoid();
  }
  // </editor-fold>
}
