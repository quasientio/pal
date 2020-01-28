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

package net.ittera.pal.common.znodes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class UTCTimestampedInfo {

  protected OffsetDateTime ctime;
  protected OffsetDateTime mtime;

  public void setCtime(long ctime) {
    Instant instant = Instant.ofEpochMilli(ctime);
    this.ctime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  public void setMtime(long mtime) {
    Instant instant = Instant.ofEpochMilli(mtime);
    this.mtime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  public OffsetDateTime getCTime() {
    return ctime;
  }

  public OffsetDateTime getMTime() {
    return mtime;
  }
}
