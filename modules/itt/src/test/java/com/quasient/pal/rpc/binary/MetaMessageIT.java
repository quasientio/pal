/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.util.GzipBase64Utils;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.types.MetaServiceType;
import com.quasient.pal.messages.types.MetaStatusType;
import java.util.HashMap;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
public class MetaMessageIT extends AbstractBinaryRpcMessageIT {

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
  @Ignore
  public void sendMetaMessage_fetchClassMetadata_metadataReturned() throws InterruptedException {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    Map<String, Object> fetchClassMetadataParams = new HashMap<>();
    fetchClassMetadataParams.put("compress_encode", true);
    fetchClassMetadataParams.put("merge_ancestry", false);
    MetaMessage metaMessageRequest =
        messageBuilder.buildMetaMessageRequest(
            clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO, fetchClassMetadataParams);

    MetaMessage metaMessageResponse = sendAndReceive(metaMessageRequest);
    assertNotNull(metaMessageResponse);
    assertEquals(MetaStatusType.OK.getId(), metaMessageResponse.getStatus());
    String body = metaMessageResponse.getBody();
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    int minExpectedClassCount = 3000;
    int classesInResult = findOccurrences("className", plainBody);
    logger.debug("fetch_class_info returned {} classes", classesInResult);
    assertTrue(classesInResult > minExpectedClassCount);

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
  }

  @Test
  @Ignore
  public void sendMetaMessage_fetchClassMetadataWithExcludes_metadataReturned()
      throws InterruptedException {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    Map<String, Object> fetchClassMetadataParams = new HashMap<>();
    fetchClassMetadataParams.put("exclude_prefixes", new String[] {"java.util", "java.lang"});
    fetchClassMetadataParams.put("compress_encode", true);
    fetchClassMetadataParams.put("merge_ancestry", false);

    MetaMessage metaMessageRequest =
        messageBuilder.buildMetaMessageRequest(
            clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO, fetchClassMetadataParams);

    MetaMessage metaMessageResponse = sendAndReceive(metaMessageRequest);
    assertNotNull(metaMessageResponse);
    assertEquals(MetaStatusType.OK.getId(), metaMessageResponse.getStatus());
    String body = metaMessageResponse.getBody();
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    int minExpectedClassCount = 3000;
    int classesInResult = findOccurrences("className", plainBody);
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
  @Ignore
  public void sendMetaMessage_fetchClassMetadataWithIncludeClasses_metadataReturned()
      throws InterruptedException {

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
    Thread.sleep(1000);

    Map<String, Object> fetchClassMetadataParams = new HashMap<>();
    fetchClassMetadataParams.put(
        "include_classes", new String[] {"java.lang.System", "java.lang.Math"});
    fetchClassMetadataParams.put("compress_encode", true);
    fetchClassMetadataParams.put("merge_ancestry", false);

    MetaMessage metaMessageRequest =
        messageBuilder.buildMetaMessageRequest(
            clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO, fetchClassMetadataParams);

    MetaMessage metaMessageResponse = sendAndReceive(metaMessageRequest);
    assertNotNull(metaMessageResponse);
    assertEquals(MetaStatusType.OK.getId(), metaMessageResponse.getStatus());
    String body = metaMessageResponse.getBody();
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    assertEquals(2, findOccurrences("className", plainBody));

    // metadata serialization uses a lot of heap, better request a GC
    sendGcCommand();
  }
}
