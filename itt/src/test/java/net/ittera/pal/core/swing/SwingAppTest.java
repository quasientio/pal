package net.ittera.pal.core.swing;

import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;

public class SwingAppTest extends AbstractSwingTest {

  protected static final MessageBuilder MESSAGE_BUILDER = new ProtobufMessageBuilder();

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
