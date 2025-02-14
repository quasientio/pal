/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.logging;

/**
 * Provides a custom {@code ConsoleAppender} that maintains the original package and class name
 * after Maven shading. This ensures that the {@code logback.xml} configuration can reference this
 * appender without requiring changes to the package name. By extending Logback's {@link
 * ch.qos.logback.core.ConsoleAppender}, this class allows relocation of dependencies without
 * affecting internal classes.
 *
 * @see ch.qos.logback.core.ConsoleAppender
 * @see <a href="https://gitlab.com/cometera/pal/-/issues/168">Issue #168</a>
 */
@SuppressWarnings("rawtypes")
public final class PeerConsoleAppender extends ch.qos.logback.core.ConsoleAppender {}
