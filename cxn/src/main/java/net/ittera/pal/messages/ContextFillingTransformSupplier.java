package net.ittera.pal.messages;

import java.util.HashMap;
import java.util.Map;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class ContextFillingTransformSupplier
    implements Transformer<String, Message, KeyValue<String, Map>> {
  private ProcessorContext processorContext;

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
  }

  @Override
  public KeyValue<String, Map> transform(String key, Message message) {
    Map<String, Object> map = new HashMap<>();
    MessageContext messageContext =
        new MessageContext(
            processorContext.offset(),
            processorContext.partition(),
            processorContext.timestamp(),
            processorContext.topic(),
            processorContext.headers());
    map.put("message", message);
    map.put("context", messageContext);
    return new KeyValue<>(key, map);
  }

  @Override
  public void close() {
    // no resources to close
  }
}
