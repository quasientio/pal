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

package net.ittera.pal.common.util;

/**
 * The purpose of this subclass is to not have to change package name in logback.xml after
 * mvn-shading (see issue #168 more details). Having our own FileAppender class means we can leave
 * the full package and class unchanged since we only relocate dependencies, not our own classes
 */
public final class PeerFileAppender extends ch.qos.logback.core.FileAppender {}
