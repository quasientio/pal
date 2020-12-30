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

package net.ittera.pal.common.directory.nodes;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

public class InfoNodeTest {

  private InfoNode infoNode;
  long ctime = 23832734;
  long mtime = 84508445;

  @Before
  public void setUp() throws Exception {
    infoNode = new InfoNode() {};
  }

  @Test
  public void setCtime() {
    infoNode.setCtime(ctime);

    Instant instant = Instant.ofEpochMilli(ctime);
    assertThat(infoNode.getCTime(), is(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)));
  }

  @Test
  public void setMtime() {
    infoNode.setMtime(mtime);

    Instant instant = Instant.ofEpochMilli(mtime);
    assertThat(infoNode.getMTime(), is(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)));
  }
}
