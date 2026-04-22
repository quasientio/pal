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
package io.quasient.pal.core.execution;

import java.util.concurrent.Callable;

/**
 * Strategy for executing method invocations with thread-affinity control.
 *
 * <p>Implementations marshal execution onto specific threads (e.g., JavaFX Application Thread)
 * while blocking the calling thread for the result.
 */
@FunctionalInterface
public interface InvocationExecutor {

  /**
   * Executes the given invocation, potentially on a different thread.
   *
   * @param invocation the callable representing the method invocation to execute
   * @return the result of the invocation
   * @throws Exception if the invocation throws an exception
   */
  Object execute(Callable<Object> invocation) throws Exception;

  /**
   * Resolves a live target instance of the given declaring type, typically by consulting a
   * framework-managed container (CDI, Spring, Guice, etc.).
   *
   * <p>Used by the replay dispatcher as a fallback when {@link
   * io.quasient.pal.core.replay.ReplayObjectStore} cannot locate the target by its WAL-recorded ref
   * — common for frameworks that instantiate beans lazily, where no woven constructor ran during
   * setup to populate the store.
   *
   * <p>Default returns {@code null}, meaning this executor does not know how to resolve targets and
   * the dispatcher should fall through to its existing phantom-skip path.
   *
   * @param declaringType the declaring class of the entry-point method
   * @return a live instance of {@code declaringType}, or {@code null} if this executor does not
   *     manage instances of that type
   */
  default Object resolveTarget(Class<?> declaringType) {
    return null;
  }
}
