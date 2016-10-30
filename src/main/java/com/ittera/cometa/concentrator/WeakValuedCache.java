package com.ittera.cometa.concentrator;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Code stolen/inspired by ceving's answer in
 * http://stackoverflow.com/questions/1802809/javas-weakhashmap-and-caching-why-is-it-referencing-the-keys-not-the-values
 *
 * @param <K>
 * @param <V>
 */
public class WeakValuedCache<K, V> {
  protected static final Logger logger = LogManager.getLogger(WeakValuedCache.class);
  protected final ReferenceQueue<V> queue;
  protected final Map<K, WeakReference<V>> values;
  protected final Map<WeakReference<V>, K> keys;
  protected final Thread cleanup;

  public WeakValuedCache() {
    queue = new ReferenceQueue<V>();
    keys = Collections.synchronizedMap(new HashMap<WeakReference<V>, K>());
    values = Collections.synchronizedMap(new HashMap<K, WeakReference<V>>());
    cleanup = new Thread() {
      public void run() {
        try {
          for (; ; ) {
            WeakReference<V> ref = (WeakReference<V>) queue.remove();
            K key = keys.get(ref);
            keys.remove(ref);
            WeakReference val = values.remove(key);
            logger.info("Key {} and corresponding value cleaned up.", key);
            logger.info("Ref cleaned up for value:" + val.get());
          }
        } catch (InterruptedException e) {
        }
      }
    };

    cleanup.setName("WeakValuedCache cleaner");
    cleanup.setDaemon(true);
    cleanup.start();
  }

  void stopThread() {
    cleanup.interrupt();
  }

  V get(K key) {
    return values.get(key).get();
  }

  void put(K key, V value) {
    WeakReference<V> ref = new WeakReference<V>(value, queue);
    keys.put(ref, key);
    values.put(key, ref);
  }

  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("{");
    boolean first = true;
    for (Map.Entry<K, WeakReference<V>> entry : values.entrySet()) {
      if (first)
        first = false;
      else
        str.append(", ");
      str.append(entry.getKey());
      str.append(": ");
      str.append(entry.getValue().get());
    }
    str.append("}");
    return str.toString();
  }
}


