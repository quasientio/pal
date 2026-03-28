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
