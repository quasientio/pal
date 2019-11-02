package com.ittera.cometa.messages;

import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class ContextFillingTransformSupplier
    implements Transformer<String, ExecMessage, KeyValue<String, Map>> {
  private ProcessorContext processorContext;

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
  }

  @Override
  public KeyValue<String, Map> transform(String key, ExecMessage execMessage) {
    Map<String, Object> map = new HashMap<>();
    MessageContext messageContext =
        new MessageContext(
            processorContext.offset(),
            processorContext.partition(),
            processorContext.timestamp(),
            processorContext.topic(),
            processorContext.headers());
    map.put("message", execMessage);
    map.put("context", messageContext);
    return new KeyValue<>(key, map);
  }

  @Override
  public void close() {}
}
