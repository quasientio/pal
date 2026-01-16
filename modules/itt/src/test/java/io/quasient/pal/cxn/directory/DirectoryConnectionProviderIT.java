/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.AbstractIntegrationTest;
import org.junit.After;
import org.junit.Test;

public class DirectoryConnectionProviderIT extends AbstractIntegrationTest {
  private PalDirectory palDirectory;

  @After
  public void cleanUp() {
    if (palDirectory != null) {
      palDirectory.close();
      palDirectory = null;
    }
  }

  @Test
  public void getConnection_noPaldirUrl() {
    DirectoryConnectionProvider connectionFactory =
        new DirectoryConnectionProvider(PalDirectory.NO_URL);
    assertThat(connectionFactory.get().isPresent(), is(false));
  }

  @Test
  public void getConnection() {
    DirectoryConnectionProvider connectionFactory =
        new DirectoryConnectionProvider(getPalDirectoryUrl());
    assertThat(connectionFactory.get().isPresent(), is(true));
  }
}
