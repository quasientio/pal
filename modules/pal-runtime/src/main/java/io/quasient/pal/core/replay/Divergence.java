/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

/**
 * An individual divergence detected between a WAL-recorded value and the actual live execution
 * value during deterministic replay.
 *
 * @param type the category of divergence (e.g., value mismatch, operation mismatch)
 * @param walOffset the WAL offset of the entry where the divergence was detected
 * @param description a human-readable description of the divergence
 * @param expected the expected value from the WAL recording (may be {@code null})
 * @param actual the actual value from live execution (may be {@code null})
 */
public record Divergence(
    DivergenceDetector.DivergenceType type,
    long walOffset,
    String description,
    Object expected,
    Object actual) {}
