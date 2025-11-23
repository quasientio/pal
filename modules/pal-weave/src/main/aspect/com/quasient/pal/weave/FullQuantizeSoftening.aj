/**
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.weave;

import com.quasient.pal.common.runtime.DispatchForwarder;

public aspect FullQuantizeSoftening {
  declare soft: Throwable : call (Object DispatchForwarder.constructor(..));
  declare soft: Throwable : call (void DispatchForwarder.voidInstanceMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidInstanceMethod(..));
  declare soft: Throwable : call (void DispatchForwarder.voidClassMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidClassMethod(..));
}