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

package net.ittera.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.RpcType;
import net.ittera.pal.rpc.AbstractRpcMessageIT;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.jsonrpc.JsonRpcSerializer;
import net.ittera.pal.serdes.jsonrpc.JsonSerializationException;
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
            bind(ObjectLookupStore.class)
                .to(ConcurrentHashMapObjectLookupStore.class)
                .asEagerSingleton();
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
    logger.debug("Finalizing after tests...");
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
        logger.debug("Sending message to peer");
        response = thinPeer.sendJsonRpcRequestToPeer(jsonRpcRequest, messageId).get();
      } else {
        logger.debug("Sending message to log");
        response = thinPeer.sendJsonRpcRequestToLogAndReceive(jsonRpcRequest);
      }
    } catch (Exception e) {
      logger.error("Exception sending/receiving message: {}", jsonRpcRequest, e);
      throw e;
    }
    return response;
  }

  // </editor-fold desc="helper methods">
  protected JsonRpcResponse callGetStaticField(int messageId, String className, String fieldName)
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
              "id": %d
            }
            """
            .formatted(className, fieldName, messageId);

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callPutStaticField(
      int messageId, String className, String fieldName, String fieldValue) throws Exception {
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
               "id": %d
             }
             """
            .formatted(className, fieldName, fieldValue, messageId);

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callGetInstanceField(
      int messageId, String className, String fieldName, long instanceRef) throws Exception {
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
              "id": %d
            }
            """
            .formatted(className, fieldName, instanceRef, messageId);

    return sendAndReceive(request);
  }

  protected JsonRpcResponse callPutInstanceField(
      int messageId, String className, String fieldName, long instanceRef, String fieldValue)
      throws Exception {
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
              "id": %d
            }
            """
            .formatted(className, fieldName, instanceRef, fieldValue, messageId);

    return sendAndReceive(request);
  }

  /**
   * Helper method to call a class method.
   *
   * @param messageId the message ID
   * @param className the class name
   * @param methodName the method name
   * @param argsJson a JSON array of arguments or null if no args
   * @return JsonRpcResponse
   */
  protected JsonRpcResponse callClassMethod(
      int messageId, String className, String methodName, String argsJson) throws Exception {

    String request;
    if (argsJson == null) {
      // no arguments
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s"
                }
              }
              """
              .formatted(messageId, className, methodName);
    } else {
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "args": %s
                }
              }
              """
              .formatted(messageId, className, methodName, argsJson);
    }

    return sendAndReceive(request);
  }

  /**
   * Helper method to call an instance method.
   *
   * @param messageId the message ID
   * @param instanceRef the reference to the instance
   * @param className the class name
   * @param methodName the method name
   * @param argsJson a JSON array of arguments or null if no args
   * @return JsonRpcResponse
   */
  protected JsonRpcResponse callInstanceMethod(
      int messageId, long instanceRef, String className, String methodName, String argsJson)
      throws Exception {

    String request;
    if (argsJson == null) {
      // no arguments
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "instance": %d
                }
              }
              """
              .formatted(messageId, className, methodName, instanceRef);
    } else {
      request =
              """
              {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "call",
                "params": {
                  "type": "%s",
                  "method": "%s",
                  "instance": %d,
                  "args": %s
                }
              }
              """
              .formatted(messageId, className, methodName, instanceRef, argsJson);
    }

    return sendAndReceive(request);
  }

  protected Integer createNewInstance(int messageId, String className) throws Exception {
    String request =
            """
            {
              "jsonrpc": "2.0",
              "method": "new",
              "params": {
                "type": "%s"
              },
              "id": %d
            }
            """
            .formatted(className, messageId);

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
  // </editor-fold>
}
