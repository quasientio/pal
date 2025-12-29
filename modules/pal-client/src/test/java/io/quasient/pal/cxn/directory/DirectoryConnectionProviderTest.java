/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public class DirectoryConnectionProviderTest {

  @Test
  public void get_returnsEmpty_whenNoUrl() {
    DirectoryConnectionProvider p =
        new DirectoryConnectionProvider(PalDirectory.NO_URL, /*namespace*/ null, false);
    Optional<PalDirectory> got = p.get();
    assertThat(got.isPresent(), is(false));
    assertThat(p.getConnectionString(), is(PalDirectory.NO_URL));
  }

  @Test
  public void get_returnsCachedInstance_whenUrlGiven() {
    // Use a dummy URL; constructor should not block because we don't pass 'blocking=true'.
    DirectoryConnectionProvider p =
        new DirectoryConnectionProvider("http://127.0.0.1:2379", /*namespace*/ "testns", false);
    Optional<PalDirectory> first = p.get();
    Optional<PalDirectory> second = p.get();
    assertTrue(first.isPresent());
    assertTrue(second.isPresent());
    // Same instance should be returned (cached)
    assertThat(first.get() == second.get(), is(true));
  }
}
