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
package io.quasient.pal.messages;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.Objects;
import org.apache.kafka.common.header.Header;

/**
 * Represents a header in log messages, implementing the {@link Header} interface from Apache Kafka.
 *
 * <p>This class encapsulates a key-value pair used to store header information associated with log
 * messages.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Kafka Header interface requires direct array access for performance")
public class LogMessageHeader implements Header {
  /** the key of the header */
  private final String key;

  /** the value of the header as a byte array */
  private final byte[] value;

  /**
   * Simple two-arg constructor
   *
   * @param key the key of the header, must not be {@code null} or empty
   * @param value the value of the header as a byte array, may be {@code null} or empty
   */
  public LogMessageHeader(String key, byte[] value) {
    this.key = key;
    this.value = value;
  }

  /** {@inheritDoc} */
  @Override
  public String key() {
    return key;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LogMessageHeader that)) return false;
    return Objects.equals(key, that.key) && Objects.deepEquals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, Arrays.hashCode(value));
  }

  @Override
  public String toString() {
    return "LogMessageHeader{" + "key='" + key + '\'' + ", value=" + Arrays.toString(value) + '}';
  }
}
