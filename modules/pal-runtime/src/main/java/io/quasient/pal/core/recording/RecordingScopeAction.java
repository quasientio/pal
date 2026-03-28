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
package io.quasient.pal.core.recording;

/**
 * Action that a {@link RecordingScopeRule} can prescribe for an operation.
 *
 * <p>Used in the recording scope system to determine whether an operation should be written to the
 * WAL and published via PUB, or silently skipped. Only two actions are needed: recording scope
 * controls persistence and publishing, not security, so there is no need for log-and-allow/deny
 * variants.
 *
 * @see RecordingScopeRule
 * @see RecordingScope
 */
public enum RecordingScopeAction {

  /** The operation should be recorded to WAL and published via PUB. */
  RECORD,

  /** The operation should be skipped (not written to WAL or published). */
  SKIP
}
