/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

import org.apache.kafka.common.header.Header;

/**
 * Represents a header in log messages, implementing the {@link Header} interface from Apache Kafka.
 *
 * <p>This record-class encapsulates a key-value pair used to store header information associated
 * with log messages.
 *
 * @param key the key of the header, must not be {@code null} or empty
 * @param value the value of the header as a byte array, may be {@code null} or empty
 */
public record LogMessageHeader(String key, byte[] value) implements Header {}
