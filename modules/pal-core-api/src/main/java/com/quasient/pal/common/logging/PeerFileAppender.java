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

package com.quasient.pal.common.logging;

/**
 * Custom FileAppender that preserves the fully qualified class name in logback.xml after
 * mvn-shading. This allows the relocation of dependencies without requiring changes to the logging
 * configuration. By extending Logback's {@link ch.qos.logback.core.FileAppender}, it ensures
 * consistent logging behavior while maintaining the original package structure for configuration
 * purposes. See issue #168 for more details.
 *
 * @see ch.qos.logback.core.FileAppender
 */
@SuppressWarnings("rawtypes")
public final class PeerFileAppender extends ch.qos.logback.core.FileAppender {}
