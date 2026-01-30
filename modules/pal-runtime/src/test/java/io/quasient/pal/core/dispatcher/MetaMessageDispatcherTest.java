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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.execution.java.reflect.ClassMetadataSerializer;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link MetaMessageDispatcher}.
 *
 * <p>Tests the handling of meta messages including FETCH_CLASSES_INFO service type, error handling,
 * unsupported service types, and null/empty parameter handling.
 */
public class MetaMessageDispatcherTest {

  private UUID peerUuid;
  private ClassMetadataSerializer classMetadataSerializer;
  private MessageBuilder messageBuilder;
  private MetaMessageDispatcher dispatcher;

  /** Sets up the test fixtures. */
  @Before
  public void setUp() {
    peerUuid = UUID.randomUUID();
    classMetadataSerializer = mock(ClassMetadataSerializer.class);
    // Create real MessageBuilder since it's a final class
    messageBuilder = new MessageBuilder(peerUuid);
    dispatcher = new MetaMessageDispatcher(peerUuid, classMetadataSerializer, messageBuilder);
  }

  // ===== FETCH_CLASSES_INFO Success Tests =====

  /** Tests successful FETCH_CLASSES_INFO with no parameters. */
  @Test
  public void incomingMetaMessage_fetchClassesInfo_success() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-1");
    request.setFromPeer("peer-1");

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
    assertThat(response.getBody(), containsString("/tmp/result.json"));
    assertThat(response.getResponseToId(), is("msg-1"));
    verify(classMetadataSerializer).scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false));
  }

  /** Tests successful FETCH_CLASSES_INFO with empty params array. */
  @Test
  public void incomingMetaMessage_fetchClassesInfo_emptyParams() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-empty-params");
    request.setParams(new Parameter[0]);

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  // ===== Error Handling Tests =====

  /** Tests FETCH_CLASSES_INFO when serializer throws exception. */
  @Test
  public void incomingMetaMessage_fetchClassesInfo_serializerThrows_returnsErrorResponse()
      throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-err");
    request.setFromPeer("peer-err");

    when(classMetadataSerializer.scannedClasspathToJson(anyBoolean(), any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Scan failed"));

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.ERROR.getId()));
    assertThat(response.getBody(), containsString("Scan failed"));
    assertThat(response.getResponseToId(), is("msg-err"));
  }

  /** Tests FETCH_CLASSES_INFO when serializer throws exception with null message. */
  @Test
  public void incomingMetaMessage_fetchClassesInfo_serializerThrowsNullMessage() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-ex");

    when(classMetadataSerializer.scannedClasspathToJson(anyBoolean(), any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException());

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.ERROR.getId()));
  }

  // ===== Null/Empty Parameter Handling Tests =====

  /** Tests that null params array is handled gracefully. */
  @Test
  public void incomingMetaMessage_nullParams_handledGracefully() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-params");
    request.setParams(null);

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  /** Tests that parameter with null name is skipped. */
  @Test
  public void incomingMetaMessage_parameterWithNullName_skipped() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-name");

    Parameter param = new Parameter();
    param.setName(null);
    param.setValue(mock(Obj.class));
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  /** Tests that parameter with null value is skipped. */
  @Test
  public void incomingMetaMessage_parameterWithNullValue_skipped() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-value");

    Parameter param = new Parameter();
    param.setName("compress_encode");
    param.setValue(null);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  /** Tests that unknown parameter name is logged as warning but processing continues. */
  @Test
  public void incomingMetaMessage_unknownParameterName_loggedAndContinues() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-unknown-param");

    Obj valueObj = mock(Obj.class);
    Parameter param = new Parameter();
    param.setName("unknown_parameter");
    param.setValue(valueObj);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Should complete successfully despite unknown parameter
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  // ===== Response Field Verification Tests =====

  /** Tests that response contains service type from request. */
  @Test
  public void incomingMetaMessage_responseContainsServiceType() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-service");

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(anyBoolean(), any(), any(), anyBoolean()))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response.getService(), is(MetaServiceType.FETCH_CLASSES_INFO.getId()));
  }

  /** Tests that response contains peer UUID from dispatcher. */
  @Test
  public void incomingMetaMessage_responseContainsPeerUuid() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-peer");

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(anyBoolean(), any(), any(), anyBoolean()))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response.getFromPeer(), is(peerUuid.toString()));
  }
}
