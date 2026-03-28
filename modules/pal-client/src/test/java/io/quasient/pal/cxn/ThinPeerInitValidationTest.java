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
