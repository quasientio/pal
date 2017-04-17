package com.ittera.cometa;

import com.ittera.cometa.concentrator.DualPeer;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.Type;

public class SwingAppActor {

    protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder(null);

    protected static final String swingAppClassName = "com.ittera.cometa.apps.SwingApp";

    public static void main(String[] args) throws Exception {

        final DualPeer dualPeer = new DualPeer("/tests.properties");
        final String methodName;

        methodName = "main";
        Class[] parameterTypes = new Class[]{String[].class};
        String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        Object[] parameters = new Object[]{new String[]{}};


        final DataMessage requestMsg = dataMessageBuilder.buildClassMethod(dualPeer.getPeerUuid(),
                    swingAppClassName, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);

        // call main
        Thread asyncSend = new Thread() {
            @Override
            public void run() {
                dualPeer.sendToLogAndForget(requestMsg);
            }
        };
        asyncSend.start();

        // wait for put of javax.swing.JFrame;
//        DataMessage msg = dualPeer.waitFor(Type.PUT_STATIC,"javax.swing.JFrame" );
        DataMessage msg = dualPeer.waitFor(Type.PUT_STATIC,"frame" );

        // finalize
        dualPeer.close();
    }
}
