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
package io.quasient.pal.serdes.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.types.JsonRpcType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import io.quasient.pal.serdes.kafka.typed.KafkaLogMessageSerializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Before;
import org.junit.Test;

public class KafkaLogMessageSerdeTest {

  private final UUID peerUuid = UUID.randomUUID();
  private MessageBuilder messageBuilder;

  @Before
  public void setUp() {
    messageBuilder = new MessageBuilder();
  }

  @Test
  public void testColferMessageSerialization() {
    // Topic to use for testing
    String topic = "test-topic";

    // Create sample content
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message content = messageBuilder.wrap(execMessage);

    Map<String, String> messageHeaders = new HashMap<>();

    // Create the LogMessage
    LogMessage<Message> logMessage = new LogMessage<>(topic, null, messageHeaders, content);

    // Create headers for the ProducerRecord
    Headers headers = new RecordHeaders();

    // Serialize, deserialize and verify basic expectations
    LogMessage<?> consumedLogMessage = serializeAndDeserialize(topic, headers, logMessage);

    // Cast the content to Message to compare fields
    Message producedContent = logMessage.getContent();
    Message consumedContent = (Message) consumedLogMessage.getContent();

    assertThat(consumedContent.getExecMessage(), is(producedContent.getExecMessage()));
  }

  @Test
  public void testJsonRpcMessageSerialization() {
    // Create sample JSON-RPC content
    JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
    jsonRpcRequest.setId("1");
    jsonRpcRequest.setMethod("call");
    jsonRpcRequest.setParams(createCallParams("testMethod", List.of("Hello", ", ", "world!")));

    // Prepare message headers
    Map<String, String> messageHeaders = new HashMap<>();
    messageHeaders.put("message-type", JsonRpcType.REQUEST.name());

    String topic = "test-topic";

    // Create the LogMessage
    LogMessage<JsonRpcRequest> logMessage =
        new LogMessage<>(topic, null, messageHeaders, jsonRpcRequest);

    // Create headers for the ProducerRecord
    Headers headers = new RecordHeaders();

    // Serialize, deserialize and verify basic expectations
    LogMessage<?> consumedLogMessage = serializeAndDeserialize(topic, headers, logMessage);

    // Cast the content to JsonRpcRequest to compare fields
    JsonRpcRequest producedContent = logMessage.getContent();
    JsonRpcRequest consumedContent = (JsonRpcRequest) consumedLogMessage.getContent();

    assertThat(consumedContent.getId(), is(producedContent.getId()));
    assertThat(consumedContent.getMethod(), is(producedContent.getMethod()));
    assertThat(consumedContent.getParams(), is(producedContent.getParams()));
  }

  /**
   * Helper method to serialize a LogMessage, deserialize it, and verify basic expectations.
   *
   * @param topic the topic name
   * @param headers the Kafka headers
   * @param logMessage the log message to serialize and deserialize
   * @return the consumed/deserialized LogMessage
   */
  private LogMessage<?> serializeAndDeserialize(
      String topic, Headers headers, LogMessage<?> logMessage) {
    // Create the Serializer
    KafkaLogMessageSerializer serializer = new KafkaLogMessageSerializer();

    // Serialize the LogMessage
    byte[] serializedValue = serializer.serialize(topic, headers, logMessage);

    // Set up mock consumer and poll for records
    ConsumerRecords<byte[], byte[]> consumerRecords =
        pollMockConsumer(topic, serializedValue, headers);

    // Deserialize the records
    List<ConsumerRecord<String, LogMessage<?>>> records =
        deserializeRecords(consumerRecords, topic);

    // Verify basic expectations
    assertThat(records.size(), is(1));
    LogMessage<?> consumedLogMessage = records.get(0).value();

    assertThat(consumedLogMessage.getTopic(), is(logMessage.getTopic()));
    assertThat(consumedLogMessage.getContent().getClass(), is(logMessage.getContent().getClass()));

    return consumedLogMessage;
  }

  /**
   * Helper method to set up a MockConsumer, add a record with serialized data, and poll.
   *
   * @param topic the topic name
   * @param serializedValue the serialized message bytes
   * @param headers the Kafka headers
   * @return the polled consumer records
   */
  private ConsumerRecords<byte[], byte[]> pollMockConsumer(
      String topic, byte[] serializedValue, Headers headers) {
    MockConsumer<byte[], byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

    TopicPartition partition = new TopicPartition(topic, 0);
    mockConsumer.assign(List.of(partition));
    mockConsumer.updateBeginningOffsets(Map.of(partition, 0L));

    ConsumerRecord<byte[], byte[]> consumerRecord =
        new ConsumerRecord<>(
            topic,
            partition.partition(),
            0L, // offset
            System.currentTimeMillis(), // timestamp
            TimestampType.CREATE_TIME, // timestamp type
            -1, // serialized key size (key is null)
            serializedValue.length, // serialized value size
            null, // key (null in this test)
            serializedValue, // serialized value
            headers, // headers
            Optional.empty());

    mockConsumer.addRecord(consumerRecord);
    return mockConsumer.poll(Duration.ZERO);
  }

  /**
   * Helper method to deserialize Kafka consumer records into LogMessage records.
   *
   * @param consumerRecords the raw consumer records to deserialize
   * @param topic the topic name
   * @return list of deserialized consumer records
   */
  private List<ConsumerRecord<String, LogMessage<?>>> deserializeRecords(
      ConsumerRecords<byte[], byte[]> consumerRecords, String topic) {
    List<ConsumerRecord<String, LogMessage<?>>> records = new ArrayList<>();
    var deserializer = new KafkaLogMessageDeserializer();

    for (ConsumerRecord<byte[], byte[]> record : consumerRecords.records(topic)) {
      String deserializedKey = null;
      LogMessage<?> deserializedValue =
          deserializer.deserialize(record.topic(), record.headers(), record.value());

      ConsumerRecord<String, LogMessage<?>> deserializedRecord =
          new ConsumerRecord<>(
              record.topic(),
              record.partition(),
              record.offset(),
              record.timestamp(),
              record.timestampType(),
              -1, // serialized key size
              -1, // serialized value size
              deserializedKey,
              deserializedValue,
              record.headers(),
              Optional.empty());

      records.add(deserializedRecord);
    }

    return records;
  }

  public static Params createCallParams(String methodName, List<String> stringList) {
    Params callParams = new Params();
    callParams.setMethod(methodName);
    callParams.setType("org.tests.DummyClass");
    var args =
        stringList.stream()
            .map(
                str -> {
                  Argument arg = new Argument();
                  arg.setValue(str);
                  return arg;
                })
            .collect(Collectors.toList());
    callParams.setArgs(args);
    return callParams;
  }
}
