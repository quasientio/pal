package com.ittera.cometa;

import com.ittera.cometa.client.DualPeer;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

public class SwingAppTest {

    protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();

    protected static final String className = "com.ittera.cometa.apps.SwingApp";

    public static void main(String[] args) throws Exception {
        DualPeer dualPeer = new DualPeer("/tests.properties");
        final String methodName = "main";

        Class[] parameterTypes = new Class[]{String[].class};
        String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        Object[] parameters = new Object[]{new String[]{}};

        DataMessage requestMsg = dataMessageBuilder.buildClassMethod(dualPeer.getPeerUuid(),
                    className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
        DataMessage replyMsg = dualPeer.sendAndReceive(requestMsg);
    }
}
