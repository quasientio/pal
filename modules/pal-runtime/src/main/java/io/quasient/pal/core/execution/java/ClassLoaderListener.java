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

/**
 * Interface for receiving notifications when a class is loaded.
 *
 * <p>This interface should be implemented by classes that need to take action immediately after a
 * class is loaded, such as for logging, monitoring, or further processing of the loaded class.
 */
public interface ClassLoaderListener {

  /**
   * Invoked when a class has been successfully loaded by the class loader.
   *
   * <p>This method acts as a callback, providing the loaded class to the listener so that it may
   * perform operations such as inspection or registration. The parameter should not be null.
   *
   * @param clazz the class that has been loaded
   */
  void classLoaded(Class<?> clazz);
}
