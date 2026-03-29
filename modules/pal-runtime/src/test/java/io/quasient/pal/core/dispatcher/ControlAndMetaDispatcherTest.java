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
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.execution.java.reflect.ClassMetadataSerializer;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class ControlAndMetaDispatcherTest {

  private static void set(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Test
  public void controlDispatcher_ping_ok() throws Exception {
    ControlMessageDispatcher d = new ControlMessageDispatcher();
    UUID me = UUID.randomUUID();
    set(d, "peerUuid", me);
    set(d, "messageBuilder", new MessageBuilder());
    set(d, "outboundMessageGateway", mock(OutboundMessageGateway.class));
    set(d, "objectLookupStore", mock(ObjectLookupStore.class));

    ControlMessage req = new ControlMessage();
    req.setFromPeer(UuidUtils.toBytes(UUID.randomUUID()));
    req.setMessageId("x1");
    req.setCommand(ControlCommandType.PING.getId());
    ControlMessage resp = d.incomingControlMessage(req);
    assertThat(resp.getStatus(), is(ControlStatusType.OK.toId()));
    assertThat(resp.getResponseToId(), is("x1"));
  }

  @Test
  public void metaDispatcher_fetchClassesInfo_ok() throws Exception {
    UUID me = UUID.randomUUID();
    MessageBuilder mb = new MessageBuilder();
    ClassMetadataSerializer cms = mock(ClassMetadataSerializer.class);
    Path tmp = Files.createTempFile("classes", ".json");
    Mockito.when(
            cms.scannedClasspathToJson(
                ArgumentMatchers.anyBoolean(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean()))
        .thenReturn(tmp);
    MetaMessageDispatcher d = new MetaMessageDispatcher(me, cms, mb);

    MetaMessage req =
        mb.buildMetaMessageRequest(
            me,
            "m1",
            MetaServiceType.FETCH_CLASSES_INFO,
            Map.of(
                "compress_encode", false,
                "include_classes", new String[] {"java.util.ArrayList"},
                "merge_ancestry", false));

    MetaMessage resp = d.incomingMetaMessage(req);
    assertThat(resp.getStatus(), is(MetaStatusType.OK.getId()));
    assertThat(resp.getResponseToId(), is("m1"));
  }

  @Test
  public void metaDispatcher_fetchClassesInfo_error() throws Exception {
    UUID me = UUID.randomUUID();
    MessageBuilder mb = new MessageBuilder();
    ClassMetadataSerializer cms = mock(ClassMetadataSerializer.class);
    Mockito.when(
            cms.scannedClasspathToJson(
                ArgumentMatchers.anyBoolean(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean()))
        .thenThrow(new RuntimeException("boom"));
    MetaMessageDispatcher d = new MetaMessageDispatcher(me, cms, mb);
    MetaMessage req =
        mb.buildMetaMessageRequest(
            me,
            "m2",
            MetaServiceType.FETCH_CLASSES_INFO,
            Map.of("compress_encode", false, "include_classes", new String[] {"java.util.List"}));
    MetaMessage resp = d.incomingMetaMessage(req);
    assertThat(resp.getStatus(), is(MetaStatusType.ERROR.getId()));
    assertThat(resp.getResponseToId(), is("m2"));
  }
}
