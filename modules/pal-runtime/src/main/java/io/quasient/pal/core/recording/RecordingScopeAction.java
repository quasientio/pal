/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
