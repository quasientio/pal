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
package io.quasient.pal.common.directory.nodes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

public class InfoNodeTest {

  private InfoNode infoNode;
  private final long ctime = 23832734;
  private final long mtime = 84508445;

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
