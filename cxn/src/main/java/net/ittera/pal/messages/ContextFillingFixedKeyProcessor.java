package net.ittera.pal.messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.processor.api.RecordMetadata;

public class ContextFillingFixedKeyProcessor
    implements FixedKeyProcessor<String, LogMessage<?>, Map<String, Object>> {

  private final String logId;

  private FixedKeyProcessorContext<String, Map<String, Object>> context;

  public ContextFillingFixedKeyProcessor(String logId) {
    this.logId = logId;
  }

  @Override
  public void init(FixedKeyProcessorContext<String, Map<String, Object>> context) {
    this.context = context;
  }

  @Override
  public void process(FixedKeyRecord<String, LogMessage<?>> record) {
    Map<String, Object> map = new HashMap<>();

    // Retrieve metadata from the context
    Optional<RecordMetadata> metadata = context.recordMetadata();

    // Access timestamp from FixedKeyRecord directly
    long timestamp = record.timestamp();

    if (metadata.isPresent()) {
      RecordMetadata meta = metadata.get();

      // Create MessageContext with available metadata
      MessageContext messageContext =
          new MessageContext(meta.offset(), meta.partition(), timestamp, meta.topic(), logId);

      map.put("message", record.value());
      map.put("context", messageContext);

      // Forward enriched record downstream
      context.forward(record.withValue(map));
    } else {
      // Handle cases without metadata
      context.forward(record.withValue(null)); // Or apply alternative handling
    }
  }

  @Override
  public void close() {
    // No resources to close
  }
}
