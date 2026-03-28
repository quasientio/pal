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
package io.quasient.pal.core.replay;

/**
 * An individual divergence detected between a WAL-recorded value and the actual live execution
 * value during deterministic replay.
 *
 * @param type the category of divergence (e.g., value mismatch, operation mismatch)
 * @param walOffset the WAL offset of the entry where the divergence was detected
 * @param threadName the name of the thread on which the divergence was detected
 * @param description a human-readable description of the divergence
 * @param expected the expected value from the WAL recording (may be {@code null})
 * @param actual the actual value from live execution (may be {@code null})
 */
public record Divergence(
    DivergenceDetector.DivergenceType type,
    long walOffset,
    String threadName,
    String description,
    Object expected,
    Object actual) {}
