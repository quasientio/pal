/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.util.GzipBase64Utils;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior. This test is not
 * parameterized on TargetType, so it only runs against a Peer via direct socket RPC. That is
 * because MetaMessages are currently only sent via directly to a peer via socket RPC.
 */
public class MetaMessageIT extends AbstractJsonRpcMessageIT {

  private static int findOccurrences(String searchString, String content) {
    // Count occurrences of the searchString in content
    int count = 0;
    int index = content.indexOf(searchString);
    while (index != -1) {
      count++;
      index = content.indexOf(searchString, index + searchString.length());
    }
    return count;
  }

  @Test
  public void sendMetaMessage_fetchClassMetadata_metadataReturned() throws Exception {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    JsonRpcRequest rpcRequest =
        JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(null, null, true, false);
    JsonRpcResponse rpcResponse = sendAndReceive(rpcRequest);
    assertNotNull(rpcResponse);
    assertNull(rpcResponse.getError());
    assertNotNull(rpcResponse.getResult());
    JsonRpcResponseReturnValue result = rpcResponse.getResult();
    assertNotNull(result.getValue());
    String body = result.getValue().getValue();
    assertNotNull(body);
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    String searchString = "className";
    int minExpectedClassCount = 3000;
    int classesInResult = findOccurrences(searchString, plainBody);
    logger.debug("fetch_class_info returned {} classes", classesInResult);
    assertTrue(classesInResult > minExpectedClassCount);

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
  }

  @Test
  public void sendMetaMessage_fetchClassMetadataWithExcludes_metadataReturned() throws Exception {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    String[] excludes = new String[] {"java.util", "java.lang"};
    JsonRpcRequest rpcRequest =
        JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(null, excludes, true, false);
    JsonRpcResponse rpcResponse = sendAndReceive(rpcRequest);
    assertNotNull(rpcResponse);
    assertNull(rpcResponse.getError());
    assertNotNull(rpcResponse.getResult());
    JsonRpcResponseReturnValue result = rpcResponse.getResult();
    assertNotNull(result.getValue());
    String body = result.getValue().getValue();
    assertNotNull(body);
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    String searchString = "className";
    int minExpectedClassCount = 3000;
    int classesInResult = findOccurrences(searchString, plainBody);
    logger.debug("fetch_class_info returned {} classes", classesInResult);
    assertTrue(classesInResult > minExpectedClassCount);

    // expect no java.util classes
    String javaUtilClassNameEntry = "\"className\":\"java.util.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));

    // expect no java.lang classes
    javaUtilClassNameEntry = "\"className\":\"java.lang.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
  }

  @Test
  public void sendMetaMessage_fetchClassMetadataWithIncludeClasses_metadataReturned()
      throws Exception {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    String[] includes = new String[] {"java.lang.System", "java.lang.Math"};
    JsonRpcRequest rpcRequest =
        JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(includes, null, true, false);
    JsonRpcResponse rpcResponse = sendAndReceive(rpcRequest);
    assertNotNull(rpcResponse);
    assertNull(rpcResponse.getError());
    assertNotNull(rpcResponse.getResult());
    JsonRpcResponseReturnValue result = rpcResponse.getResult();
    assertNotNull(result.getValue());
    String body = result.getValue().getValue();
    assertNotNull(body);
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    String searchString = "className";
    assertEquals(2, findOccurrences(searchString, plainBody));

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
  }
}
