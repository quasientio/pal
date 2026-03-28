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
package io.quasient.pal.common.logging;

/**
 * Provides a custom {@code ConsoleAppender} that maintains the original package and class name
 * after Maven shading. This ensures that the {@code logback.xml} configuration can reference this
 * appender without requiring changes to the package name. By extending Logback's {@link
 * ch.qos.logback.core.ConsoleAppender}, this class allows relocation of dependencies without
 * affecting internal classes. See issue #168 for more details.
 *
 * @see ch.qos.logback.core.ConsoleAppender
 */
@SuppressWarnings({"rawtypes", "PMD.NoFullyQualifiedTypes"})
public final class PeerConsoleAppender extends ch.qos.logback.core.ConsoleAppender {}
