/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.messages.types.JsonRpcType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import com.quasient.pal.serdes.kafka.typed.KafkaLogMessageSerializer;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KafkaLogMessageSerdeTest {

  private final UUID peerUuid = UUID.randomUUID();
  private MessageBuilder messageBuilder;

  @Before
  public void setUp() {
    messageBuilder = new MessageBuilder();
  }

  @After
  public void tearDown() {}

  @Test
  public void testBinaryRpcMessageSerialization() {
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

    // Create the Serializer
    KafkaLogMessageSerializer serializer = new KafkaLogMessageSerializer();

    // Serialize the LogMessage
    byte[] serializedValue = serializer.serialize(topic, headers, logMessage);

    // Now, set up a MockConsumer to consume the message
    MockConsumer<byte[], byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

    // Assign the topic partition and set the starting offset
    TopicPartition partition = new TopicPartition(topic, 0);
    mockConsumer.assign(List.of(partition));
    mockConsumer.updateBeginningOffsets(Map.of(partition, 0L));

    // Prepare the ConsumerRecord with serialized bytes
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

    // Add the record to the mock consumer
    mockConsumer.addRecord(consumerRecord);

    // Poll the consumer to get the records
    ConsumerRecords<byte[], byte[]> consumerRecords = mockConsumer.poll(java.time.Duration.ZERO);

    // Deserialize the records
    List<ConsumerRecord<String, LogMessage<?>>> records = new ArrayList<>();
    for (ConsumerRecord<byte[], byte[]> record : consumerRecords.records(topic)) {
      String deserializedKey = null;
      var deserializer = new KafkaLogMessageDeserializer();
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

    // assertions
    assertThat(records.size(), is(1));
    LogMessage<?> consumedLogMessage = records.get(0).value();

    assertThat(consumedLogMessage.getTopic(), is(logMessage.getTopic()));
    assertThat(consumedLogMessage.getContent().getClass(), is(logMessage.getContent().getClass()));

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

    var serializer = new KafkaLogMessageSerializer();
    byte[] serializedValue = serializer.serialize(topic, headers, logMessage);

    // set up a MockConsumer to consume the message
    MockConsumer<byte[], byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

    // Assign the topic partition and set the starting offset
    TopicPartition partition = new TopicPartition(topic, 0);
    mockConsumer.assign(List.of(partition));
    mockConsumer.updateBeginningOffsets(Map.of(partition, 0L));

    // Prepare the ConsumerRecord with serialized bytes
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

    // Add the record to the mock consumer
    mockConsumer.addRecord(consumerRecord);

    // Poll the consumer to get the records
    ConsumerRecords<byte[], byte[]> consumerRecords = mockConsumer.poll(java.time.Duration.ZERO);

    // Deserialize the records
    var deserializer = new KafkaLogMessageDeserializer();
    List<ConsumerRecord<String, LogMessage<?>>> records = new ArrayList<>();
    for (ConsumerRecord<byte[], byte[]> record : consumerRecords.records(topic)) {
      String deserializedKey = null;
      LogMessage<?> deserializedValue =
          deserializer.deserialize(record.topic(), record.headers(), record.value());

      // Create a new ConsumerRecord with deserialized key and value
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

    // assertions
    assertThat(records.size(), is(1));
    LogMessage<?> consumedLogMessage = records.get(0).value();

    assertThat(consumedLogMessage.getTopic(), is(logMessage.getTopic()));
    assertThat(consumedLogMessage.getContent().getClass(), is(logMessage.getContent().getClass()));

    // Cast the content to JsonRpcRequest to compare fields
    JsonRpcRequest producedContent = logMessage.getContent();
    JsonRpcRequest consumedContent = (JsonRpcRequest) consumedLogMessage.getContent();

    assertThat(consumedContent.getId(), is(producedContent.getId()));
    assertThat(consumedContent.getMethod(), is(producedContent.getMethod()));
    assertThat(consumedContent.getParams(), is(producedContent.getParams()));
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
