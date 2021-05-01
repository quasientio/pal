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

package net.ittera.pal.common.api.rmi;

import net.ittera.pal.common.objects.ObjectRef;

public class InstanceMethodCall extends MethodCall {

  private ObjectRef instance;

  public InstanceMethodCall(String classname, String method) {
    super(classname, method);
  }

  public InstanceMethodCall(Class clazz, String method) {
    super(clazz, method);
  }

  public InstanceMethodCall withInstance(ObjectRef instance) {
    this.instance = instance;
    return this;
  }

  public InstanceMethodCall withParameterTypes(String[] parameterTypes) {
    this.parameterTypes = parameterTypes;
    return this;
  }

  public InstanceMethodCall withArgs(Object[] args) {
    this.args = args;
    return this;
  }

  public ObjectRef getInstance() {
    return instance;
  }
}
