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

package net.ittera.pal.core.swing;

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.ColferMessageBuilder;

public class SwingAppTest extends AbstractSwingTest {

  protected static final ColferMessageBuilder MESSAGE_BUILDER = new ColferMessageBuilder();

  protected static final String className = "net.ittera.pal.apps.SwingApp";

  SwingAppTest() throws Exception {

    final ThinPeer thinPeer = getThinPeer();
    final String methodName = "main";

    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[] {new String[] {}};

    ExecMessage requestMsg =
        MESSAGE_BUILDER.buildClassMethod(
            thinPeer.getPeerUuid(),
            className,
            methodName,
            parameterTypesNamesArray,
            null,
            null,
            parameters,
            new ObjectRef[parameterTypes.length]);
    ExecMessage replyMsg = thinPeer.sendAndReceive(requestMsg, true);
  }

  public static void main(String[] args) throws Exception {
    new SwingAppTest();
  }
}
