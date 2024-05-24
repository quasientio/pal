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

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.nodes.InterceptRequest;

/**
 * Event that is fired when an intercept is added or removed.
 *
 * @param type The type of the event. INTERCEPT_ADDED: An intercept was added. INTERCEPT_REMOVED: An
 *     intercept was removed.
 * @param interceptPath The path of the intercept.
 * @param peerUuid The UUID of the peer that owns the new or removed intercept.
 * @param interceptUuid The UUID of the intercept that was added or removed.
 * @param interceptRequest The intercept request that was added. Null if the intercept was removed.
 */
public record InterceptEvent(
    @Nonnull InterceptEvent.Type type,
    @Nonnull String interceptPath,
    @Nonnull UUID peerUuid,
    @Nonnull UUID interceptUuid,
    @Nullable InterceptRequest<?> interceptRequest) {

  public InterceptEvent {
    Objects.requireNonNull(type, "type cannot be null");
    validatePath(interceptPath);
    Objects.requireNonNull(peerUuid, "peerUuid cannot be null");
    Objects.requireNonNull(interceptUuid, "interceptUuid cannot be null");
    if (type == Type.INTERCEPT_ADDED) {
      Objects.requireNonNull(interceptRequest, "interceptRequest cannot be null");
    }
  }

  public enum Type {
    INTERCEPT_ADDED,
    INTERCEPT_REMOVED,
  }

  private static void validatePath(String path) {
    Objects.requireNonNull(path, "interceptPath cannot be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("interceptPath cannot be empty");
    }
    // path should consist of 4 parts separated by '/'
    Iterable<String> parts = Splitter.on('/').split(path);
    List<String> nonEmptyPathElements = new ArrayList<>();
    for (String part : parts) {
      if (!part.isEmpty()) {
        nonEmptyPathElements.add(part);
      }
    }
    if (nonEmptyPathElements.size() != 4) {
      throw new IllegalArgumentException(
          "interceptPath should consist of 4 non-empty parts separated by '/'");
    }
  }
}
