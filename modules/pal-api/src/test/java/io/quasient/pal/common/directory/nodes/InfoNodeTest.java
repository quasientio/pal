/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
