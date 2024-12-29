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

package net.ittera.pal.rpc.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import net.ittera.pal.common.util.GzipBase64Utils;
import net.ittera.pal.messages.colfer.MetaMessage;
import net.ittera.pal.messages.types.MetaServiceType;
import net.ittera.pal.messages.types.MetaStatusType;
import org.junit.Test;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
public class MetaMessageIT extends AbstractBinaryRPCMessageIT {

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

    MetaMessage metaMessageRequest =
        messageBuilder.buildMetaMessageRequest(
            clientId, generateId(), MetaServiceType.FETCH_CLASSES_INFO);

    MetaMessage metaMessageResponse = sendAndReceive(metaMessageRequest);
    assertNotNull(metaMessageResponse);
    assertEquals(MetaStatusType.OK.getId(), metaMessageResponse.getStatus());
    String body = metaMessageResponse.getBody();
    assertFalse(body.isEmpty());

    // decompress && decode body
    String plainBody = GzipBase64Utils.decode(body);

    // expect > 10000 classes
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences("className", plainBody) > minExpectedClassCount);
  }

  @Test
  public void sendMetaMessage_fetchClassMetadataWithExcludes_metadataReturned() throws Exception {

    Map<String, Object> fetchClassMetadataParams =
        Map.of("exclude_prefixes", new String[] {"java.util", "java.lang"});

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

    // expect > 10000 classes
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences("className", plainBody) > minExpectedClassCount);

    // expect no java.util classes
    String javaUtilClassNameEntry = "\"className\" : \"java.util.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));

    // expect no java.lang classes
    javaUtilClassNameEntry = "\"className\" : \"java.lang.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, plainBody));
  }
}
