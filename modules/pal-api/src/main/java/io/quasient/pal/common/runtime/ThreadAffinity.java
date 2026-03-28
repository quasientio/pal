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
