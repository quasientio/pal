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
package io.quasient.pal.common.replay;

/**
 * Represents a paired operation/completion span in the WAL, identified by the offsets of the
 * initiating operation entry and its corresponding completion entry.
 *
 * @param operationOffset the WAL offset of the operation (request) entry
 * @param completionOffset the WAL offset of the completion (response/done) entry
 */
public record Span(long operationOffset, long completionOffset) {}
