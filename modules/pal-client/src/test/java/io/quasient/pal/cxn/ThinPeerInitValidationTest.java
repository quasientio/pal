/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn;

import static org.junit.Assert.assertThrows;

import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import org.junit.Test;

public class ThinPeerInitValidationTest {

  @Test
  public void init_throws_whenBothDirUrlAndProviderGiven() {
    ThinPeer p =
        new ThinPeer()
            .withDirectoryUrl("http://127.0.0.1:2379")
            .withDirectoryProvider(new DirectoryConnectionProvider(PalDirectory.NO_URL));
    assertThrows(IllegalArgumentException.class, p::init);
  }

  @Test
  public void init_throws_whenRegisterSelfWithoutDirectory() {
    ThinPeer p = new ThinPeer().withSelfRegistration(true);
    assertThrows(IllegalArgumentException.class, p::init);
  }
}
