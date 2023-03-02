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

package net.ittera.pal.cxn;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.io.IOException;
import net.ittera.pal.AbstractIntegrationTest;
import org.junit.After;
import org.junit.Test;

public class DirectoryConnectionProviderIT extends AbstractIntegrationTest {
  private PALDirectory palDirectory;

  @After
  public void cleanUp() throws IOException {
    if (palDirectory != null) {
      palDirectory.close();
      palDirectory = null;
    }
  }

  @Test
  public void getConnection_noPaldirUrl() {
    DirectoryConnectionProvider connectionFactory =
        new DirectoryConnectionProvider(PALDirectory.NO_URL);
    assertThat(connectionFactory.get().isPresent(), is(false));
  }

  @Test
  public void getConnection() throws Exception {
    DirectoryConnectionProvider connectionFactory =
        new DirectoryConnectionProvider(getPALDirectoryURL());
    assertThat(connectionFactory.get().isPresent(), is(true));
  }
}
