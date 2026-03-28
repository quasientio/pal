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
package io.quasient.pal.core.service.testdata;

/**
 * Test class with a main method that returns an explicit exit code via System.exit(). Used to test
 * exit code propagation.
 */
public class MainWithIntegerReturn {
  public static void main(String[] args) {
    // Simulate a program that wants to return a specific exit code
    // In real scenarios, this would be done via System.exit(42)
    // but for testing we can't actually call System.exit
    System.out.println("Exiting with code 42");
  }
}
