/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench.dummies;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A MockProducer variant that invokes the normal callbacks/futures but immediately discards every
 * {@link ProducerRecord}, so its internal {@code history()} list never grows and no payloads are
 * retained on the heap.
 *
 * <p>Intended for soak / load tests where we care about the end-to-end behaviour of our pipeline,
 * not about inspecting every record afterwards.
 *
 * <p><b>Important:</b> this class is created with {@code autoComplete=true}. Each {@code send}
 * therefore <em>immediately</em> completes the returned future and invokes the callback on the
 * calling thread – exactly what the default MockProducer does when {@code autoComplete} is enabled.
 *
 * @param <K> type of Key
 * @param <V> type of Value
 */
public class DrainingMockProducer<K, V> extends MockProducer<K, V> {

  /** Convenience constructor with custom serializers. */
  public DrainingMockProducer(Serializer<K> keySerializer, Serializer<V> valueSerializer) {

    /* autoComplete = true ⇒ MockProducer will run the callback
    immediately inside {@link #send}. */
    super(/* autoComplete= */ true, keySerializer, valueSerializer);
  }

  /* -----------------------------------------------------------------------
   *  Overridden API
   * -------------------------------------------------------------------- */

  /** Discard the record right after MockProducer has fired the callback. */
  @Override
  public synchronized Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {

    // Let the parent do normal validation + callback invocation
    Future<RecordMetadata> f = super.send(record, callback);

    // Immediately wipe internal state so nothing is retained
    super.clear(); // clears history + pendingCompletion lists
    return f;
  }

  /**
   * @return an immutable empty list – nothing is stored.
   */
  @Override
  public synchronized List<ProducerRecord<K, V>> history() {
    return Collections.emptyList();
  }
}
