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
import org.junit.Ignore;
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

  // ===== Additional Test Specifications (Issue #468) =====

  /**
   * Tests that an unsupported service type returns UNSUPPORTED status.
   *
   * <p>Given: MetaMessage with unknown service type (service ID not mapped to any MetaServiceType)
   * When: incomingMetaMessage called Then: Response has UNSUPPORTED status and error message
   * indicates the unsupported service
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_unsupportedService_returnsUnsupportedResponse() {
    // Given: MetaMessage with unknown service type
    // The service ID 127 is not mapped to any MetaServiceType (only FETCH_CLASSES_INFO=1 exists)

    // When: incomingMetaMessage called

    // Then: Response has UNSUPPORTED status

    // TODO(#469): Implement test logic
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that an unknown parameter name is logged as warning but processing continues.
   *
   * <p>Given: MetaMessage with unrecognized parameter name When: incomingMetaMessage called Then:
   * Warning logged; parameter ignored; processing continues successfully
   *
   * <p>Note: This test verifies the behavior documented at MetaMessageDispatcher.java:136 where
   * unknown parameters are logged with logger.warn() and skipped.
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_unknownParameter_logsWarning() {
    // Given: MetaMessage with unrecognized parameter name

    // When: incomingMetaMessage called

    // Then: Warning logged; parameter ignored; processing continues

    // TODO(#469): Implement test logic
    // Consider using a log appender to verify the warning was logged
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that a malformed parameter value throws RuntimeException during unwrapping.
   *
   * <p>Given: MetaMessage with malformed parameter value (e.g., compress_encode set to a value that
   * cannot be cast to Boolean) When: incomingMetaMessage called Then: RuntimeException thrown with
   * message indicating the parameter name
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_unwrapError_throwsRuntimeException() {
    // Given: MetaMessage with malformed parameter value
    // Create an Obj with incorrect type that will fail to unwrap as Boolean

    // When: incomingMetaMessage called

    // Then: RuntimeException thrown

    // TODO(#469): Implement test logic
    // Create a parameter where getValue() returns an Obj that cannot be unwrapped as Boolean
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that exclude_prefixes parameter excludes matching classes from result.
   *
   * <p>Given: MetaMessage with exclude_prefixes parameter containing class prefixes When:
   * FETCH_CLASSES_INFO processed Then: Classes matching prefixes excluded from result; serializer
   * called with correct excludePrefixes set
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_excludePrefixes_excludesMatchingClasses() throws Exception {
    // Given: MetaMessage with exclude_prefixes parameter
    // Create a parameter with name "exclude_prefixes" and value as String[]

    // When: FETCH_CLASSES_INFO processed

    // Then: Classes matching prefixes excluded from result
    // Verify classMetadataSerializer.scannedClasspathToJson was called with correct excludePrefixes

    // TODO(#469): Implement test logic
    // Use Wrapper to create properly wrapped String[] for exclude_prefixes
    // Verify using Mockito ArgumentCaptor or eq() matchers
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that include_classes parameter only includes specified classes in result.
   *
   * <p>Given: MetaMessage with include_classes parameter containing specific class names When:
   * FETCH_CLASSES_INFO processed Then: Only specified classes in result; serializer called with
   * correct includeClasses set
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_includeClasses_onlyIncludesSpecified() throws Exception {
    // Given: MetaMessage with include_classes parameter
    // Create a parameter with name "include_classes" and value as String[]

    // When: FETCH_CLASSES_INFO processed

    // Then: Only specified classes in result
    // Verify classMetadataSerializer.scannedClasspathToJson was called with correct includeClasses

    // TODO(#469): Implement test logic
    // Use Wrapper to create properly wrapped String[] for include_classes
    // Use ArgumentCaptor to capture the Set<String> argument
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that merge_ancestry=true parameter includes parent members in result.
   *
   * <p>Given: MetaMessage with merge_ancestry=true When: FETCH_CLASSES_INFO processed Then: Result
   * includes inherited members; serializer called with mergeAncestry=true
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_mergeAncestry_includesParentMembers() throws Exception {
    // Given: MetaMessage with merge_ancestry=true

    // When: FETCH_CLASSES_INFO processed

    // Then: Result includes inherited members
    // Verify classMetadataSerializer.scannedClasspathToJson was called with mergeAncestry=true

    // TODO(#469): Implement test logic
    // Use Wrapper to create properly wrapped Boolean for merge_ancestry
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that compress_encode=false returns uncompressed JSON result.
   *
   * <p>Given: MetaMessage with compress_encode=false When: FETCH_CLASSES_INFO processed Then:
   * Result is plain JSON (not compressed); serializer called with compressAndEncode=false
   */
  @Test
  @Ignore("Awaiting implementation in #469")
  public void incomingMetaMessage_compressEncodeFalse_returnsUncompressed() throws Exception {
    // Given: MetaMessage with compress_encode=false

    // When: FETCH_CLASSES_INFO processed

    // Then: Result is plain JSON (not compressed)
    // Verify classMetadataSerializer.scannedClasspathToJson was called with compressAndEncode=false

    // TODO(#469): Implement test logic
    // Use Wrapper to create properly wrapped Boolean for compress_encode
    throw new AssertionError("Not yet implemented");
  }
}
