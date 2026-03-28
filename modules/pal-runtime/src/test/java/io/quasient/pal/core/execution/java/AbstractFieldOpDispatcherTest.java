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
public abstract class AbstractFieldOpDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_primitive_ok() throws Throwable;

  public abstract void dispatchIncoming_primitive_ok() throws Exception;

  public abstract void dispatch_primitiveArray_ok() throws Throwable;

  public abstract void dispatchIncoming_primitiveArray_ok() throws Exception;

  public abstract void dispatch_wrapper_ok() throws Throwable;

  public abstract void dispatchIncoming_wrapper_ok() throws Exception;

  public abstract void dispatch_string_ok() throws Throwable;

  public abstract void dispatchIncoming_string_ok() throws Exception;

  public abstract void dispatch_object_ok() throws Throwable;

  public abstract void dispatchIncoming_object_ok() throws Exception;

  public abstract void dispatch_nullObject_ok() throws Throwable;

  public abstract void dispatchIncoming_nullObject_ok() throws Exception;

  public abstract void dispatch_objectArray_ok() throws Throwable;

  public abstract void dispatchIncoming_objectArray_ok() throws Exception;

  public abstract void dispatch_throwable_ok() throws Throwable;

  public abstract void dispatchIncoming_throwable_ok() throws Exception;

  // WAL incoming RPC tests
  public abstract void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception;

  public abstract void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception;

  public abstract void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception;

  public abstract void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception;
}
