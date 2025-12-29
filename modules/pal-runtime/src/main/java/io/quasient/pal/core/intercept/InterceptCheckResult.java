/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.Collections;
import java.util.List;

/**
 * Result of intercept checking operation.
 *
 * <p>This class encapsulates the results of checking for matching intercepts, separating remote
 * intercepts (which require callbacks to other peers) from local intercepts (which can be handled
 * in the same JVM without message passing).
 *
 * <p>The distinction between remote and local intercepts allows optimization: remote intercepts
 * require creating and sending ExecMessage objects, while local intercepts can potentially be
 * handled without the serialization overhead.
 */
public class InterceptCheckResult {

  /** List of intercept messages for remote peers that matched the execution context. */
  private final List<InterceptMessage> remoteIntercepts;

  /**
   * List of intercept messages for local handlers that matched the execution context. Currently
   * unused but reserved for future local intercept functionality.
   */
  private final List<InterceptMessage> localIntercepts;

  /**
   * Constructs a new InterceptCheckResult with the specified remote and local intercept matches.
   *
   * @param remoteIntercepts list of intercepts requiring remote peer callbacks; must not be null
   * @param localIntercepts list of intercepts that can be handled locally; must not be null
   */
  public InterceptCheckResult(
      List<InterceptMessage> remoteIntercepts, List<InterceptMessage> localIntercepts) {
    this.remoteIntercepts = remoteIntercepts != null ? remoteIntercepts : Collections.emptyList();
    this.localIntercepts = localIntercepts != null ? localIntercepts : Collections.emptyList();
  }

  /**
   * Checks whether any remote intercepts were matched.
   *
   * @return true if at least one remote intercept matched; false otherwise
   */
  public boolean hasRemoteIntercepts() {
    return !remoteIntercepts.isEmpty();
  }

  /**
   * Checks whether any local intercepts were matched.
   *
   * @return true if at least one local intercept matched; false otherwise
   */
  public boolean hasLocalIntercepts() {
    return !localIntercepts.isEmpty();
  }

  /**
   * Checks whether any intercepts (remote or local) were matched.
   *
   * @return true if at least one intercept matched; false otherwise
   */
  public boolean hasAnyIntercepts() {
    return hasRemoteIntercepts() || hasLocalIntercepts();
  }

  /**
   * Determines whether an ExecMessage needs to be created for this intercept check result.
   *
   * <p>ExecMessage creation is only necessary when remote intercepts are present, as they require
   * serialized messages to be sent to other peers. Local intercepts can be handled without message
   * creation overhead.
   *
   * @return true if ExecMessage creation is required; false otherwise
   */
  public boolean needsExecMessage() {
    return hasRemoteIntercepts();
  }

  /**
   * Retrieves the list of remote intercept messages.
   *
   * @return an unmodifiable view of remote intercepts; never null but may be empty
   */
  public List<InterceptMessage> getRemoteIntercepts() {
    return Collections.unmodifiableList(remoteIntercepts);
  }

  /**
   * Retrieves the list of local intercept messages.
   *
   * @return an unmodifiable view of local intercepts; never null but may be empty
   */
  public List<InterceptMessage> getLocalIntercepts() {
    return Collections.unmodifiableList(localIntercepts);
  }

  /**
   * Checks whether any AROUND intercepts were matched.
   *
   * @return true if at least one AROUND intercept matched; false otherwise
   */
  public boolean hasAroundIntercepts() {
    return remoteIntercepts.stream()
        .anyMatch(i -> InterceptType.fromByte(i.getInterceptType()) == InterceptType.AROUND);
  }

  /**
   * Retrieves the list of AROUND intercept messages from remote intercepts.
   *
   * @return list of AROUND intercepts; never null but may be empty
   */
  public List<InterceptMessage> getAroundIntercepts() {
    return remoteIntercepts.stream()
        .filter(i -> InterceptType.fromByte(i.getInterceptType()) == InterceptType.AROUND)
        .toList();
  }
}
