/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.Unwrappable;

/**
 * Adapts an {@link Obj} instance to the {@link Unwrappable} interface.
 *
 * <p>This adapter allows an {@code Obj} to be used by the {@link Unwrapper} class wherever an
 * {@code Unwrappable} is required, providing access to the underlying object's properties through
 * the {@code Unwrappable} interface methods.
 */
public class ObjUnwrappableAdapter implements Unwrappable {

  /** The {@code Obj} instance being adapted. */
  private final Obj obj;

  /**
   * Constructs an {@code ObjUnwrappableAdapter} with the specified {@code Obj}.
   *
   * @param obj the {@code Obj} to adapt; must not be {@code null}
   */
  public ObjUnwrappableAdapter(Obj obj) {
    this.obj = obj;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNull() {
    return obj.getIsNull();
  }

  /** {@inheritDoc} */
  @Override
  public String getValue() {
    return obj.getValue();
  }

  /** {@inheritDoc} */
  @Override
  public String getType() {
    return obj.getClazz() != null ? obj.getClazz().getName() : null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Parses the {@code ref} property of the underlying {@code Obj} as an {@code Integer}. If
   * {@code ref} is {@code null} or empty, {@code null} is returned.
   *
   * @return the integer value of {@code ref}, or {@code null} if {@code ref} is {@code null} or
   *     empty
   */
  @Override
  public Integer getRef() {
    if (obj.getRef() == null || obj.getRef().isEmpty()) {
      return null;
    }
    return Integer.parseInt(obj.getRef());
  }
}
