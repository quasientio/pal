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

package net.ittera.pal.common.directory.events;

import java.util.UUID;
import javax.annotation.Nonnull;
import net.ittera.pal.common.directory.nodes.InterceptRequest;

public record InterceptEvent(
    @Nonnull net.ittera.pal.common.directory.events.InterceptEvent.Type type,
    @Nonnull String interceptPath,
    @Nonnull UUID peerUuid,
    @Nonnull UUID interceptUuid,
    @Nonnull InterceptRequest<?> interceptRequest) {

  public enum Type {
    INTERCEPT_ADDED,
    INTERCEPT_REMOVED,
  }
}
