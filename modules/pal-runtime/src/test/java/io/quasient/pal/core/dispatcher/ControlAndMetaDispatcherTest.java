/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

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
    req.setFromPeer(UUID.randomUUID().toString());
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
