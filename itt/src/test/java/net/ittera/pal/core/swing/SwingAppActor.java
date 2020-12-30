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
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Primitives;

public class SwingAppActor extends AbstractSwingTest {

  protected static final MessageBuilder MESSAGE_BUILDER = new ProtobufMessageBuilder();

  protected static final String swingAppClassName = "net.ittera.pal.apps.SwingApp";

  SwingAppActor() throws Exception {
    final ThinPeer thinPeer = getThinPeer();
    String methodName;

    methodName = "main";
    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[] {new String[] {}};

    final ExecMessage mainRequest =
        MESSAGE_BUILDER.buildClassMethod(
            thinPeer.getPeerUuid(),
            swingAppClassName,
            methodName,
            parameterTypesNamesArray,
            null,
            null,
            parameters,
            new ObjectRef[parameterTypes.length]);

    // start the swingapp by calling main in background
    new Thread(() -> thinPeer.sendToLogAndForget(mainRequest)).start();

    // wait for put of JFrame field;
    String fieldName = "frame";
    thinPeer.waitFor(ExecMessageType.PUT_STATIC_DONE, fieldName);

    // now get the jframe
    ExecMessage requestMsg =
        MESSAGE_BUILDER.buildGetStatic(thinPeer.getPeerUuid(), swingAppClassName, fieldName);
    ExecMessage replyMsg = thinPeer.sendAndReceive(requestMsg, true);
    Primitives.Object myFrame = replyMsg.getReturnValue().getObject();

    for (int i = 0; i < 5; i++) {
      sleep(1);

      // set visible = false
      String fieldClassName = "javax.swing.JFrame";
      methodName = "setVisible";
      parameters = new Object[] {false};
      parameterTypesNamesArray = new String[] {"boolean"};
      requestMsg =
          MESSAGE_BUILDER.buildInstanceMethod(
              thinPeer.getPeerUuid(),
              fieldClassName,
              methodName,
              null,
              ObjectRef.from(myFrame.getRef()),
              parameterTypesNamesArray,
              parameters,
              new ObjectRef[parameters.length]);
      thinPeer.sendAndReceive(requestMsg, true);

      sleep(1);

      // reset visible = true
      parameters = new Object[] {Boolean.TRUE};
      requestMsg =
          MESSAGE_BUILDER.buildInstanceMethod(
              thinPeer.getPeerUuid(),
              fieldClassName,
              methodName,
              null,
              ObjectRef.from(myFrame.getRef()),
              parameterTypesNamesArray,
              parameters,
              new ObjectRef[parameters.length]);
      thinPeer.sendAndReceive(requestMsg, true);
    }

    // finalize
    thinPeer.close();
  }

  protected static void sleep(int secs) {
    try {
      Thread.sleep(secs * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    new SwingAppActor();
  }
}
