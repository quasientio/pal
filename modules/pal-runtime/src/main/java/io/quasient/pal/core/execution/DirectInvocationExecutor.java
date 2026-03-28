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
 * Default executor that invokes callables directly on the current thread with zero overhead.
 *
 * <p>This is the fallback executor used when no thread affinity is specified or when the requested
 * affinity has no matching registered executor.
 */
public class DirectInvocationExecutor implements InvocationExecutor {

  /** Creates a new direct invocation executor. */
  public DirectInvocationExecutor() {}

  @Override
  public Object execute(Callable<Object> invocation) throws Exception {
    return invocation.call();
  }
}
