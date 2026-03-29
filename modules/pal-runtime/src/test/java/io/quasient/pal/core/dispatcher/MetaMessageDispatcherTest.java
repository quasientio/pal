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

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.execution.java.reflect.ClassMetadataSerializer;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
    request.setFromPeer(
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-1".getBytes(StandardCharsets.UTF_8))));

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
    request.setParams(new Obj[0]);

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
    request.setFromPeer(
        UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-err".getBytes(StandardCharsets.UTF_8))));

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

  /** Tests that a null Obj at a param position is skipped. */
  @Test
  public void incomingMetaMessage_parameterWithNullObj_skipped() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-name");

    request.setParams(new Obj[] {null});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  /** Tests that an isNull Obj at a param position is skipped. */
  @Test
  public void incomingMetaMessage_parameterWithIsNullObj_skipped() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-null-value");

    Obj nullObj = new Obj();
    nullObj.setIsNull(true);
    request.setParams(new Obj[] {nullObj});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
  }

  /** Tests that extra positional params beyond the known ones are ignored. */
  @Test
  public void incomingMetaMessage_extraPositionalParam_ignored() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-extra-param");

    Obj extraObj = Wrapper.wrapForceByValue("extra-value");
    request.setParams(new Obj[] {null, null, null, null, extraObj});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Should complete successfully despite extra positional param
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

    assertThat(UuidUtils.toString(response.getFromPeer()), is(peerUuid.toString()));
  }

  // ===== Additional Test Specifications =====

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
    request.setFromPeer(
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("peer-unsupported".getBytes(StandardCharsets.UTF_8))));

    // When: incomingMetaMessage called
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Response has UNSUPPORTED status and error message indicates the unknown service
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.UNSUPPORTED.getId()));
    assertThat(response.getBody(), containsString("unknown service ID"));
    assertThat(response.getResponseToId(), is("msg-unsupported"));
  }

  /**
   * Tests that extra positional params beyond known indices do not cause errors.
   *
   * <p>Given: MetaMessage with more Obj entries than recognized positional params When:
   * incomingMetaMessage called Then: Processing continues successfully; extra entries ignored
   */
  @Test
  public void incomingMetaMessage_extraPositionalParams_processedSuccessfully() throws Exception {
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-extra-params-log");

    Obj extraObj = Wrapper.wrapForceByValue("some-value");
    request.setParams(new Obj[] {null, null, null, null, extraObj});

    Path resultPath = Path.of("/tmp/result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    // When: incomingMetaMessage called
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Processing continues successfully; extra entries ignored
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
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

    // Create an Obj with String value at index 0 (compress_encode expects Boolean)
    Obj wrongTypeValue = Wrapper.wrapForceByValue("not-a-boolean");
    request.setParams(new Obj[] {wrongTypeValue});

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

    // Positional: index 1 = exclude_prefixes (index 0 = compress_encode, left null)
    String[] prefixesToExclude = new String[] {"java.lang.", "sun.misc."};
    Obj valueObj = Wrapper.wrapForceByValue(prefixesToExclude);
    request.setParams(new Obj[] {null, valueObj});

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

    // Positional: index 2 = include_classes (indices 0-1 left null)
    String[] classesToInclude = new String[] {"com.example.MyClass", "com.example.AnotherClass"};
    Obj valueObj = Wrapper.wrapForceByValue(classesToInclude);
    request.setParams(new Obj[] {null, null, valueObj});

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

    // Positional: index 3 = merge_ancestry (indices 0-2 left null)
    Obj valueObj = Wrapper.wrapForceByValue(Boolean.TRUE);
    request.setParams(new Obj[] {null, null, null, valueObj});

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

    // Positional: index 0 = compress_encode
    Obj valueObj = Wrapper.wrapForceByValue(Boolean.FALSE);
    request.setParams(new Obj[] {valueObj});

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

  // ===== Test Specifications =====
  // These test methods document the acceptance criteria for MetaMessageDispatcher.
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
  public void testIncomingMetaMessage_dispatchesCorrectly() throws Exception {
    // Given: Valid meta message with known service type
    MetaMessage request = new MetaMessage();
    request.setService(MetaServiceType.FETCH_CLASSES_INFO.getId());
    request.setMessageId("msg-dispatch-test");
    request.setFromPeer(
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("peer-dispatch-test".getBytes(StandardCharsets.UTF_8))));

    Path resultPath = Path.of("/tmp/dispatch-result.json");
    when(classMetadataSerializer.scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false)))
        .thenReturn(resultPath);

    // When: incomingMetaMessage called
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Message dispatched to correct handler; response generated with OK status
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.OK.getId()));
    assertThat(response.getResponseToId(), is("msg-dispatch-test"));
    assertThat(response.getService(), is(MetaServiceType.FETCH_CLASSES_INFO.getId()));
    assertThat(UuidUtils.toString(response.getFromPeer()), is(peerUuid.toString()));
    verify(classMetadataSerializer).scannedClasspathToJson(eq(true), isNull(), isNull(), eq(false));
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
  public void testIncomingMetaMessage_unknownMessageType_handledGracefully() {
    // Given: Meta message with unknown service type
    MetaMessage request = new MetaMessage();
    request.setService((byte) 127); // Not mapped to any MetaServiceType
    request.setMessageId("msg-unknown-test");
    request.setFromPeer(
        UuidUtils.toBytes(
            UUID.nameUUIDFromBytes("peer-unknown-test".getBytes(StandardCharsets.UTF_8))));

    // When: incomingMetaMessage called
    MetaMessage response = dispatcher.incomingMetaMessage(request);

    // Then: Handled gracefully; no exception thrown; response has UNSUPPORTED status
    assertThat(response, notNullValue());
    assertThat(response.getStatus(), is(MetaStatusType.UNSUPPORTED.getId()));
    assertThat(response.getBody(), containsString("unknown service ID"));
    assertThat(response.getResponseToId(), is("msg-unknown-test"));
  }
}
