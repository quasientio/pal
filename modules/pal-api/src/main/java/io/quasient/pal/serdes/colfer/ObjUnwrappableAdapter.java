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
