package net.ittera.pal.serdes.colfer;

import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.Unwrappable;

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
