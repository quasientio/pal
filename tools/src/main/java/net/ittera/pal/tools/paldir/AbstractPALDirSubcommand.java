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

package net.ittera.pal.tools.paldir;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import net.ittera.pal.cxn.PALDirectory;

public abstract class AbstractPALDirSubcommand implements Callable<Integer> {
  protected final PALDirectory palDirectory;
  protected PrintStream out;
  protected PrintStream err;

  protected AbstractPALDirSubcommand(PALDirectory palDirectory) {
    this.palDirectory = palDirectory;
    out = System.out;
    err = System.err;
  }

  public abstract void validateInput();

  protected abstract void closeResources() throws IOException;

  protected abstract int runCommand() throws Exception;

  public Integer call() throws Exception {
    try {
      validateInput();
    } catch (RuntimeException e) {
      err.println(e.getMessage());
      return 1;
    }
    int result = runCommand();
    closeResources();
    return result;
  }
}
