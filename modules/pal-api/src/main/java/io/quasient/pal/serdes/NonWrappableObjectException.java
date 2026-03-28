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
package io.quasient.pal.serdes;

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
