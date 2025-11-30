/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes;

/**
 * This exception is thrown when an attempt is made to wrap an object that is not supported by the
 * serialization process.
 */
public class NonWrappableObjectException extends IllegalArgumentException {

  /** The object that could not be wrapped. */
  private final transient Object nonWrappableObject;

  /**
   * Constructs a new NonWrappableObjectException with the specified object.
   *
   * @param nonWrappableObject the object that cannot be wrapped
   */
  public NonWrappableObjectException(Object nonWrappableObject) {
    this.nonWrappableObject = nonWrappableObject;
  }

  /**
   * Constructs a new NonWrappableObjectException with the specified detail message and object.
   *
   * @param message the detail message explaining the reason for the exception
   * @param nonWrappableObject the object that cannot be wrapped
   */
  public NonWrappableObjectException(String message, Object nonWrappableObject) {
    super(message);
    this.nonWrappableObject = nonWrappableObject;
  }

  /**
   * Constructs a new NonWrappableObjectException with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the exception
   * @param cause the underlying cause of the serialization failure
   */
  public NonWrappableObjectException(String message, Throwable cause) {
    super(message, cause);
    this.nonWrappableObject = null;
  }

  /**
   * Returns the object that could not be wrapped.
   *
   * @return the non-wrappable object
   */
  public Object getNonWrappableObject() {
    return nonWrappableObject;
  }
}
