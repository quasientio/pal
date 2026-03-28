/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.Unwrapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

public class MessageBuilderMetaMessagesTest {

  @Test
  public void meta_request_with_params_map_builds_correctly() throws Exception {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    UUID from = UUID.randomUUID();
    String reqId = UUID.randomUUID().toString();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("compress_encode", true);
    params.put("merge_ancestry", false);

    MetaMessage m =
        b.buildMetaMessageRequest(from, reqId, MetaServiceType.FETCH_CLASSES_INFO, params);

    assertEquals(from.toString(), m.getFromPeer());
    assertEquals(reqId, m.getMessageId());
    assertEquals(MetaServiceType.FETCH_CLASSES_INFO.getId(), m.getService());
    assertNotNull(m.getParams());
    assertEquals(4, m.getParams().length);
    // positional: index 0 = compress_encode, index 3 = merge_ancestry
    Object v0 = Unwrapper.unwrapObject(m.getParams()[0]);
    Object v3 = Unwrapper.unwrapObject(m.getParams()[3]);
    assertEquals(true, v0);
    assertEquals(false, v3);
    // indices 1 and 2 were not set — marked as isNull
    assertNotNull(m.getParams()[1]);
    assertTrue(m.getParams()[1].getIsNull());
    assertNotNull(m.getParams()[2]);
    assertTrue(m.getParams()[2].getIsNull());
  }

  @Test
  public void meta_response_with_body_json_ok() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    UUID from = UUID.randomUUID();
    String responseTo = UUID.randomUUID().toString();
    String body = "ok";

    MetaMessage m =
        b.buildMetaMessageResponse(
            from, MetaServiceType.FETCH_CLASSES_INFO, MetaStatusType.OK, body, responseTo);

    assertEquals(from.toString(), m.getFromPeer());
    assertEquals(responseTo, m.getResponseToId());
    assertEquals(MetaServiceType.FETCH_CLASSES_INFO.getId(), m.getService());
    assertEquals(MetaStatusType.OK.getId(), m.getStatus());

    // body is a JSON string with keys {service, response}
    JsonObject obj = new Gson().fromJson(m.getBody(), JsonObject.class);
    assertThat(
        obj.get("service").getAsString(), is(MetaServiceType.FETCH_CLASSES_INFO.getJsonName()));
    assertThat(obj.get("response").getAsString(), is(body));
  }

  @Test
  public void meta_request_with_null_params_yields_noParamsArray() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    UUID from = UUID.randomUUID();
    String reqId = UUID.randomUUID().toString();

    MetaMessage m =
        b.buildMetaMessageRequest(
            from, reqId, MetaServiceType.FETCH_CLASSES_INFO, (Map<String, Object>) null);

    assertEquals(from.toString(), m.getFromPeer());
    assertEquals(reqId, m.getMessageId());
    assertNotNull(m.getParams());
    assertEquals(0, m.getParams().length);
  }
}
