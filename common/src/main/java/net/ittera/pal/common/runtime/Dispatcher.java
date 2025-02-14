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

package net.ittera.pal.common.runtime;

/**
 * Represents a dispatcher responsible for executing operations within a specific runtime context.
 *
 * @see Context
 */
public interface Dispatcher {

  /**
   * Dispatches an operation within the given context.
   *
   * @param ctxt the runtime context in which the dispatch is executed
   * @param sender the object initiating the dispatch
   * @param target the object upon which the dispatch is directed
   * @param args the arguments required for the dispatch operation
   * @return the result of the dispatch operation
   * @throws Throwable if an error occurs during the dispatch process
   */
  Object dispatch(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;
}
