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

public class InstanceFieldPut extends FieldPut {

  private ObjectRef instance;

  protected InstanceFieldPut(String classname, String field) {
    super(classname, field);
  }

  public InstanceFieldPut(Class clazz, String field) {
    super(clazz, field);
  }

  public InstanceFieldPut withInstance(ObjectRef instance) {
    this.instance = instance;
    return this;
  }

  public InstanceFieldPut withValue(Object value) {
    this.value = value;
    return this;
  }

  public ObjectRef getInstance() {
    return instance;
  }
}
