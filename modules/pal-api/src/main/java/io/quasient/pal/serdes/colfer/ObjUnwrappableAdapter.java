/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.Unwrappable;
import io.quasient.pal.serdes.Unwrapper;

/**
 * Adapts an {@link Obj} instance to the {@link Unwrappable} interface.
 *
 * <p>This adapter allows an {@code Obj} to be used by the {@link Unwrapper} class wherever an
 * {@code Unwrappable} is required, providing access to the underlying object's properties through
 * the {@code Unwrappable} interface methods.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Adapter pattern - wraps the Obj instance without copying")
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

  /** {@inheritDoc} */
  @Override
  public Integer getRef() {
    if (obj.getRef() == 0) {
      return null;
    }
    return obj.getRef();
  }
}
