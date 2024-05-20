/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.messages;

import java.util.HashMap;
import java.util.Map;
import net.ittera.pal.messages.colfer.Message;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class ContextFillingTransformSupplier
    implements Transformer<String, Message, KeyValue<String, Map<String, Object>>> {
  private ProcessorContext processorContext;

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
  }

  @Override
  public KeyValue<String, Map<String, Object>> transform(String key, Message message) {
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
