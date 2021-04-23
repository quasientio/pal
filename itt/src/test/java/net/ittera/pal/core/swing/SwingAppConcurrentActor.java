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
import net.ittera.pal.messages.ExecMessageType;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.colfer.MessageBuilder;

public class SwingAppConcurrentActor extends AbstractSwingTest {

  protected static final MessageBuilder MESSAGE_BUILDER = new MessageBuilder();

  protected static final String swingAppClassName = "net.ittera.pal.apps.SwingApp";
  protected static final String TEST_PROPERTIES_PATH = "/tests.properties";

  private static class SwingActor implements Runnable {
    ThinPeer thinPeer;
    final String jframeRef;

    SwingActor(ThinPeer thinPeer, String jframeRef) {
      this.jframeRef = jframeRef;
      this.thinPeer = thinPeer;
    }

    @Override
    public void run() {
      for (int i = 0; i < 15; i++) {
        sleep(300);

        setFrameVisible(i % 2 == 0);
      }
      // finalize
      thinPeer.close();
    }

    private void setFrameVisible(boolean visible) {
      String methodName = "setVisible";
      Object[] parameters = new Object[] {false};
      String[] parameterTypesNamesArray = new String[] {"boolean"};
      ExecMessage requestMsg, replyMsg;
      String fieldClassName = "javax.swing.JFrame";

      parameters = new Object[] {visible};
      requestMsg =
          MESSAGE_BUILDER.buildInstanceMethod(
              thinPeer.getPeerUuid(),
              fieldClassName,
              methodName,
              null,
              ObjectRef.from(jframeRef),
              parameterTypesNamesArray,
              parameters,
              new ObjectRef[parameters.length]);
      try {
        thinPeer.sendAndReceive(requestMsg, true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    protected static void sleep(long millisecs) {
      try {
        Thread.sleep(millisecs);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  SwingAppConcurrentActor() throws Exception {
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
    Obj myFrame = replyMsg.getReturnValue().getObject();

    // start some actors and pass them the frame to play with
    final Thread[] actors = new Thread[5];
    for (int i = 0; i < actors.length; i++) {
      actors[i] = new Thread(new SwingActor(thinPeer, myFrame.getRef()));
      // introduce some delay and start
      Thread.sleep(150);
      actors[i].start();
    }

    thinPeer.close();
  }

  public static void main(String[] args) throws Exception {
    new SwingAppConcurrentActor();
  }
}
