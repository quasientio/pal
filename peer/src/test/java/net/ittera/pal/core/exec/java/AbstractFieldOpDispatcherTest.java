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

public abstract class AbstractFieldOpDispatcherTest extends AbstractDispatcherTest {

  public abstract void dispatch_primitive_ok() throws Throwable;

  public abstract void dispatchIncoming_primitive_ok() throws Exception;

  public abstract void dispatch_primitiveArray_ok() throws Throwable;

  public abstract void dispatchIncoming_primitiveArray_ok() throws Exception;

  public abstract void dispatch_wrapper_ok() throws Throwable;

  public abstract void dispatchIncoming_wrapper_ok() throws Exception;

  public abstract void dispatch_string_ok() throws Throwable;

  public abstract void dispatchIncoming_string_ok() throws Exception;

  public abstract void dispatch_object_ok() throws Throwable;

  public abstract void dispatchIncoming_object_ok() throws Exception;

  public abstract void dispatch_nullObject_ok() throws Throwable;

  public abstract void dispatchIncoming_nullObject_ok() throws Exception;

  public abstract void dispatch_objectArray_ok() throws Throwable;

  public abstract void dispatchIncoming_objectArray_ok() throws Exception;

  public abstract void dispatch_throwable_ok() throws Throwable;

  public abstract void dispatchIncoming_throwable_ok() throws Exception;
}
