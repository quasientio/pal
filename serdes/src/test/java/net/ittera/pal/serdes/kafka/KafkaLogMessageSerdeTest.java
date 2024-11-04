package net.ittera.pal.serdes.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.JsonRpcType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import net.ittera.pal.serdes.kafka.typed.KafkaLogMessageSerializer;
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

    assertThat(consumedLogMessage.topic(), is(logMessage.topic()));
    assertThat(consumedLogMessage.content().getClass(), is(logMessage.content().getClass()));

    // Cast the content to Message to compare fields
    Message producedContent = logMessage.content();
    Message consumedContent = (Message) consumedLogMessage.content();

    assertThat(consumedContent.getExecMessage(), is(producedContent.getExecMessage()));
  }

  @Test
  public void testJsonRpcMessageSerialization() {
    // Create sample JSON-RPC content
    JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
    jsonRpcRequest.setJsonrpc("2.0");
    jsonRpcRequest.setId("1");
    jsonRpcRequest.setMethod("sampleMethod");
    jsonRpcRequest.setParams(convertList(List.of("Hello", ", ", "world!")));

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

    assertThat(consumedLogMessage.topic(), is(logMessage.topic()));
    assertThat(consumedLogMessage.content().getClass(), is(logMessage.content().getClass()));

    // Cast the content to JsonRpcRequest to compare fields
    JsonRpcRequest producedContent = logMessage.content();
    JsonRpcRequest consumedContent = (JsonRpcRequest) consumedLogMessage.content();

    assertThat(consumedContent.getId(), is(producedContent.getId()));
    assertThat(consumedContent.getMethod(), is(producedContent.getMethod()));
    assertThat(consumedContent.getParams(), is(producedContent.getParams()));
  }

  public static List<JsonRpcParameter> convertList(List<String> stringList) {
    return stringList.stream()
        .map(
            str -> {
              JsonRpcParameter param = new JsonRpcParameter();
              param.setValue(str);
              return param;
            })
        .collect(Collectors.toList());
  }
}
