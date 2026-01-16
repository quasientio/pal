/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Parameter;
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
    params.put("k1", 1);
    params.put("k2", "v2");

    MetaMessage m =
        b.buildMetaMessageRequest(from, reqId, MetaServiceType.FETCH_CLASSES_INFO, params);

    assertEquals(from.toString(), m.getFromPeer());
    assertEquals(reqId, m.getMessageId());
    assertEquals(MetaServiceType.FETCH_CLASSES_INFO.getId(), m.getService());
    assertNotNull(m.getParams());
    assertEquals(2, m.getParams().length);
    // ensure parameter names and values are present
    Parameter p0 = m.getParams()[0];
    Parameter p1 = m.getParams()[1];
    assertNotNull(p0.name);
    assertNotNull(p1.name);
    Object v0 = Unwrapper.unwrapObject(p0.getValue());
    Object v1 = Unwrapper.unwrapObject(p1.getValue());
    assertTrue(v0 instanceof Number || v1 instanceof Number);
    assertTrue(v0 instanceof String || v1 instanceof String);
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
