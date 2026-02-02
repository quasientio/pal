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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.quasient.pal.core.execution.java.reflect.ClassMetadataSerializer;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

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
   * indicates the unknown service ID
   */
  @Test
  public void incomingMetaMessage_unsupportedService_returnsUnsupportedResponse() {
    // Given: MetaMessage with unknown service type
    // The service ID 127 is not mapped to any MetaServiceType (only FETCH_CLASSES_INFO=1 exists)
    MetaMessage request = new MetaMessage();
    request.setService((byte) 127);
    request.setMessageId("msg-unsupported");
    request.setFromPeer("peer-unsupported");

    // When: incomingMetaMessage called
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Response has UNSUPPORTED status and error message indicates the unknown service
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.UNSUPPORTED.getId()));
    assertThat(response.getBody(), containsString("unknown service ID"));
    assertThat(response.getResponseToId(), is("msg-unsupported"));
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
  public void incomingMetaMessage_unknownParameter_logsWarning() throws Exception {
    // Given: MetaMessage with unrecognized parameter name
    Logger logger = (Logger) LoggerFactory.getLogger(MetaMessageDispatcher.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      MetaMessage request = new MetaMessage();
      request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
      request.setMessageId("msg-unknown-param-log");

      Obj valueObj = Wrapper.wrapForceByValue("some-value");
      Parameter param = new Parameter();
      param.setName("totally_unknown_param");
      param.setValue(valueObj);
      request.setParams(new Parameter[] {param});

      Path resultPath = Path.of("/tmp/result.json");
      when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
          .thenReturn(resultPath);

      // When: incomingMetaMessage called
      MetaMessage response = dispatcher.incomingMetaMessage(request);

      // Then: Warning logged; parameter ignored; processing continues
      assertThat(response, notNullValue());
      assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));

      // Verify warning was logged
      boolean warningLogged =
          listAppender.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("totally_unknown_param"));
      assertThat("Warning should be logged for unknown parameter", warningLogged, is(true));
    } finally {
      logger.detachAppender(listAppender);
    }
  }

  /**
   * Tests that a malformed parameter value throws RuntimeException during unwrapping.
   *
   * <p>Given: MetaMessage with malformed parameter value (e.g., compress_encode set to a value that
   * cannot be cast to Boolean) When: incomingMetaMessage called Then: RuntimeException thrown with
   * message indicating the parameter name
   */
  @Test(expected = RuntimeException.class)
  public void incomingMetaMessage_unwrapError_throwsRuntimeException() {
    // Given: MetaMessage with malformed parameter value
    // Create an Obj with incorrect type that will fail to unwrap as Boolean
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-unwrap-error");

    // Create a parameter with a String value for compress_encode which expects Boolean
    Obj wrongTypeValue = Wrapper.wrapForceByValue("not-a-boolean");
    Parameter param = new Parameter();
    param.setName("compress_encode");
    param.setValue(wrongTypeValue);
    request.setParams(new Parameter[] {param});

    // When: incomingMetaMessage called
    // Then: RuntimeException thrown with message indicating the parameter name
    dispatcher.incomingMetaMessage(request);
  }

  /**
   * Tests that exclude_prefixes parameter excludes matching classes from result.
   *
   * <p>Given: MetaMessage with exclude_prefixes parameter containing class prefixes When:
   * FETCH_CLASSES_INFO processed Then: Classes matching prefixes excluded from result; serializer
   * called with correct excludePrefixes set
   */
  @Test
  @SuppressWarnings("unchecked")
  public void incomingMetaMessage_excludePrefixes_excludesMatchingClasses() throws Exception {
    // Given: MetaMessage with exclude_prefixes parameter
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-exclude-prefixes");

    // Create a parameter with name "exclude_prefixes" and value as String[]
    String[] prefixesToExclude = new String[] {"java.lang.", "sun.misc."};
    Obj valueObj = Wrapper.wrapForceByValue(prefixesToExclude);
    Parameter param = new Parameter();
    param.setName("exclude_prefixes");
    param.setValue(valueObj);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(
            anyBoolean(), any(), any(Set.class), anyBoolean()))
        .thenReturn(resultPath);

    // When: FETCH_CLASSES_INFO processed
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Classes matching prefixes excluded from result
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));

    // Verify classMetadataSerializer.scannedClasspathToJson was called with correct excludePrefixes
    ArgumentCaptor<Set<String>> excludePrefixesCaptor = ArgumentCaptor.forClass(Set.class);
    verify(classMetadataSerializer)
        .scannedClasspathToJson(eq(true), isNull(), excludePrefixesCaptor.capture(), eq(false));

    Set<String> capturedPrefixes = excludePrefixesCaptor.getValue();
    assertThat(capturedPrefixes, notNullValue());
    assertThat(capturedPrefixes.contains("java.lang."), is(true));
    assertThat(capturedPrefixes.contains("sun.misc."), is(true));
  }

  /**
   * Tests that include_classes parameter only includes specified classes in result.
   *
   * <p>Given: MetaMessage with include_classes parameter containing specific class names When:
   * FETCH_CLASSES_INFO processed Then: Only specified classes in result; serializer called with
   * correct includeClasses set
   */
  @Test
  @SuppressWarnings("unchecked")
  public void incomingMetaMessage_includeClasses_onlyIncludesSpecified() throws Exception {
    // Given: MetaMessage with include_classes parameter
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-include-classes");

    // Create a parameter with name "include_classes" and value as String[]
    String[] classesToInclude = new String[] {"com.example.MyClass", "com.example.AnotherClass"};
    Obj valueObj = Wrapper.wrapForceByValue(classesToInclude);
    Parameter param = new Parameter();
    param.setName("include_classes");
    param.setValue(valueObj);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(
            anyBoolean(), any(Set.class), any(), anyBoolean()))
        .thenReturn(resultPath);

    // When: FETCH_CLASSES_INFO processed
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Only specified classes in result
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));

    // Verify classMetadataSerializer.scannedClasspathToJson was called with correct includeClasses
    ArgumentCaptor<Set<String>> includeClassesCaptor = ArgumentCaptor.forClass(Set.class);
    verify(classMetadataSerializer)
        .scannedClasspathToJson(eq(true), includeClassesCaptor.capture(), isNull(), eq(false));

    Set<String> capturedClasses = includeClassesCaptor.getValue();
    assertThat(capturedClasses, notNullValue());
    assertThat(capturedClasses.contains("com.example.MyClass"), is(true));
    assertThat(capturedClasses.contains("com.example.AnotherClass"), is(true));
  }

  /**
   * Tests that merge_ancestry=true parameter includes parent members in result.
   *
   * <p>Given: MetaMessage with merge_ancestry=true When: FETCH_CLASSES_INFO processed Then: Result
   * includes inherited members; serializer called with mergeAncestry=true
   */
  @Test
  public void incomingMetaMessage_mergeAncestry_includesParentMembers() throws Exception {
    // Given: MetaMessage with merge_ancestry=true
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-merge-ancestry");

    // Create a parameter with name "merge_ancestry" and value as Boolean true
    Obj valueObj = Wrapper.wrapForceByValue(Boolean.TRUE);
    Parameter param = new Parameter();
    param.setName("merge_ancestry");
    param.setValue(valueObj);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(true)))
        .thenReturn(resultPath);

    // When: FETCH_CLASSES_INFO processed
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Result includes inherited members
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));

    // Verify classMetadataSerializer.scannedClasspathToJson was called with mergeAncestry=true
    verify(classMetadataSerializer).scannedClasspathToJson(eq(true), isNull(), isNull(), eq(true));
  }

  /**
   * Tests that compress_encode=false returns uncompressed JSON result.
   *
   * <p>Given: MetaMessage with compress_encode=false When: FETCH_CLASSES_INFO processed Then:
   * Result is plain JSON (not compressed); serializer called with compressAndEncode=false
   */
  @Test
  public void incomingMetaMessage_compressEncodeFalse_returnsUncompressed() throws Exception {
    // Given: MetaMessage with compress_encode=false
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-compress-false");

    // Create a parameter with name "compress_encode" and value as Boolean false
    Obj valueObj = Wrapper.wrapForceByValue(Boolean.FALSE);
    Parameter param = new Parameter();
    param.setName("compress_encode");
    param.setValue(valueObj);
    request.setParams(new Parameter[] {param});

    Path resultPath = Path.of("/tmp/uncompressed-result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(false), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    // When: FETCH_CLASSES_INFO processed
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Result is plain JSON (not compressed)
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
    assertThat(response.getBody(), containsString("uncompressed-result.json"));

    // Verify classMetadataSerializer.scannedClasspathToJson was called with compressAndEncode=false
    verify(classMetadataSerializer)
        .scannedClasspathToJson(eq(false), isNull(), isNull(), eq(false));
  }

  // ===== Test Specifications (Issue #537) =====
  // These test methods document the acceptance criteria for #537.
  // They are implemented above with different names following existing patterns.

  /**
   * [TEST:MetaMessageDispatcherTest.testIncomingMetaMessage_dispatchesCorrectly]
   *
   * <p>Tests that incomingMetaMessage() correctly dispatches valid meta messages.
   *
   * <p>Given: Valid meta message with known service type (FETCH_CLASSES_INFO)
   *
   * <p>When: incomingMetaMessage called
   *
   * <p>Then: Message dispatched to correct handler; response generated with OK status
   *
   * <p>IMPLEMENTATION NOTE: This acceptance criterion is satisfied by existing tests {@link
   * #incomingMetaMessage_fetchClassesInfo_success()}, {@link
   * #incomingMetaMessage_responseContainsServiceType()}, and {@link
   * #incomingMetaMessage_responseContainsPeerUuid()}.
   *
   * @see #incomingMetaMessage_fetchClassesInfo_success()
   * @see #incomingMetaMessage_responseContainsServiceType()
   * @see #incomingMetaMessage_responseContainsPeerUuid()
   */
  @Test
  @Ignore("Criterion satisfied by existing tests: incomingMetaMessage_fetchClassesInfo_success()")
  public void testIncomingMetaMessage_dispatchesCorrectly() {
    // Given: Valid meta message
    // When: incomingMetaMessage called
    // Then: Message dispatched to correct handler
    //
    // See incomingMetaMessage_fetchClassesInfo_success() for implementation.
    fail("This test documents the acceptance criterion - see referenced tests for implementation");
  }

  /**
   * [TEST:MetaMessageDispatcherTest.testIncomingMetaMessage_unknownMessageType_handledGracefully]
   *
   * <p>Tests that incomingMetaMessage() handles unknown message types gracefully.
   *
   * <p>Given: Meta message with unknown service type (service ID not mapped to any MetaServiceType)
   *
   * <p>When: incomingMetaMessage called
   *
   * <p>Then: Handled gracefully; no exception thrown; response has UNSUPPORTED status
   *
   * <p>IMPLEMENTATION NOTE: This acceptance criterion is satisfied by existing test {@link
   * #incomingMetaMessage_unsupportedService_returnsUnsupportedResponse()}.
   *
   * @see #incomingMetaMessage_unsupportedService_returnsUnsupportedResponse()
   */
  @Test
  @Ignore(
      "Criterion satisfied by existing test:"
          + " incomingMetaMessage_unsupportedService_returnsUnsupportedResponse()")
  public void testIncomingMetaMessage_unknownMessageType_handledGracefully() {
    // Given: Unknown message type
    // When: incomingMetaMessage called
    // Then: Handled gracefully; no exception
    //
    // See incomingMetaMessage_unsupportedService_returnsUnsupportedResponse() for implementation.
    fail("This test documents the acceptance criterion - see referenced test for implementation");
  }
}
