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
package io.quasient.pal.core.execution.java;

@SuppressWarnings("unused")
public abstract class AbstractMethodDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_noArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_noArgs_ok() throws Exception;

  public abstract void dispatch_withArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withArgs_ok() throws Exception;

  public abstract void dispatch_withPrimitiveArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withPrimitiveArgs_ok() throws Exception;

  public abstract void dispatchIncoming_withObjectRefArgs_ok() throws Exception;

  public abstract void dispatchIncoming_withNullArgs_ok() throws Exception;

  public abstract void dispatch_varargs_ok() throws Throwable;

  public abstract void dispatchIncoming_varargs_ok() throws Exception;

  public abstract void dispatch_throwsException_exceptionThrown() throws Throwable;

  public abstract void dispatchIncoming_throwsException_exceptionThrown() throws Exception;

  public abstract void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown()
      throws Exception;

  // WAL incoming RPC tests
  public abstract void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception;

  public abstract void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception;

  public abstract void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception;

  public abstract void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception;
}
