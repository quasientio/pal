package net.ittera.pal.rpc.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.util.GzipBase64Utils;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.Params;
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
    JsonRpcRequest rpcRequest =
        JsonRpcRequest.builder()
            .withId(generateId())
            .withMethod("meta")
            .withParams(Params.builder().withMethod("fetch_classes_info").build())
            .build();

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
    // expect > 10000 classes
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, plainBody) > minExpectedClassCount);
  }

  @Test
  public void sendMetaMessage_fetchClassMetadataWithExcludes_metadataReturned() throws Exception {
    JsonRpcRequest rpcRequest =
        JsonRpcRequest.builder()
            .withId(generateId())
            .withMethod("meta")
            .withParams(
                Params.builder()
                    .withMethod("fetch_classes_info")
                    .addArg(
                        Argument.builder()
                            .withName("exclude_prefixes")
                            .withValue(new String[] {"java.util", "java.lang"})
                            .build())
                    .build())
            .build();

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
    // expect > 10000 classes
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, plainBody) > minExpectedClassCount);

    // expect no java.util classes
    String javaUtilClassNameEntry = "\"className\" : \"java.util.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));

    // expect no java.lang classes
    javaUtilClassNameEntry = "\"className\" : \"java.lang.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));
  }
}
