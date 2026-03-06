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

import io.quasient.pal.messages.types.MessageType;

/**
 * Strategy object that determines what replay action to take for each operation during
 * deterministic WAL replay.
 *
 * <p>In Phase 1, the policy is hardcoded to always return {@link ReplayAction#RE_EXECUTE}. Phase 2
 * will extend this with configurable rules that allow returning WAL-recorded values without
 * executing (e.g., for I/O-dependent operations).
 */
public class ReplayPolicy {

  /**
   * The action the replay system should take for a given operation.
   *
   * <p>All four values are defined now for forward compatibility, but Phase 1 only implements
   * {@link #RE_EXECUTE}.
   */
  public enum ReplayAction {
    /** Execute the operation live and verify the return value against the WAL. */
    RE_EXECUTE,

    /** Execute the operation live without verifying against the WAL. */
    RE_EXECUTE_UNCHECKED,

    /** Return the WAL-recorded value without executing the operation. */
    STUB_FROM_WAL,

    /** Return the WAL-recorded value and verify that the arguments match the WAL. */
    STUB_FROM_WAL_VERIFIED,

    /** Return the WAL-recorded value and replay field mutations from within the span. */
    STUB_WITH_SIDE_EFFECTS
  }

  /**
   * Determines the replay action for the given operation.
   *
   * <p>Phase 1 always returns {@link ReplayAction#RE_EXECUTE} regardless of the class name, method
   * name, or message type.
   *
   * @param className the fully qualified class name of the operation target
   * @param methodName the method, constructor, or field name
   * @param messageType the EXEC message type classifying the operation
   * @return {@link ReplayAction#RE_EXECUTE} (always, in Phase 1)
   */
  public ReplayAction getAction(String className, String methodName, MessageType messageType) {
    return ReplayAction.RE_EXECUTE;
  }
}
