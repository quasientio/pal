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

package net.ittera.pal.core.exec.java;

public abstract class AbstractMethodDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_noArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_noArgs_ok() throws Exception;

  public abstract void dispatch_withArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withArgs_ok() throws Exception;

  public abstract void dispatch_withPrimitiveArgs_ok() throws Throwable;

  public abstract void dispatchIncoming_withPrimitiveArgs_ok() throws Exception;

  //	public abstract void dispatch_withObjectRefArgs_ok(); TODO
  public abstract void dispatchIncoming_withObjectRefArgs_ok() throws Exception;

  //	public abstract void dispatch_withNullArgs_ok(); TODO
  public abstract void dispatchIncoming_withNullArgs_ok() throws Exception;

  public abstract void dispatch_varargs_ok() throws Throwable;

  public abstract void dispatchIncoming_varargs_ok() throws Exception;

  public abstract void dispatch_throwsException_exceptionThrown() throws Throwable;

  public abstract void dispatchIncoming_throwsException_exceptionThrown() throws Exception;
}
