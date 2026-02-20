/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.runtime;

import io.quasient.pal.messages.colfer.ExecMessage;

/**
 * Well-known thread affinity identifiers for RPC invocation routing.
 *
 * <p>Callers set these on {@link ExecMessage#threadAffinity} to request execution on a specific
 * thread at the receiving peer. The receiving peer must have a matching executor registered (e.g.,
 * via {@code --fx-thread}).
 */
public final class ThreadAffinity {

  /** Execute on the JavaFX Application Thread via {@code Platform.runLater()}. */
  public static final String FX_THREAD = "fx-thread";

  /** Prevents instantiation. */
  private ThreadAffinity() {}
}
