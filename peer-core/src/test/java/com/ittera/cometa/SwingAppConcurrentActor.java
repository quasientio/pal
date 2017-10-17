package com.ittera.cometa;

import com.ittera.cometa.client.DualPeer;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

public class SwingAppConcurrentActor {

    protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();

    protected static final String swingAppClassName = "com.ittera.cometa.apps.SwingApp";

    private static class SwingActor implements Runnable {
        DualPeer dualPeer;
        final String jframeRef;

        SwingActor(String jframeRef) {
            this.jframeRef = jframeRef;
            try {
                this.dualPeer = new DualPeer("/tests.properties");
            } catch (Exception ex) {
                System.err.println("error starting dual peer");
                ex.printStackTrace();
            }
        }

        @Override
        public void run() {
            for (int i = 0; i < 15; i++) {
                sleep(300);

                setFrameVisible(i % 2 == 0);
            }
            // finalize
            dualPeer.close();
        }

        private void setFrameVisible(boolean visible) {
            String methodName = "setVisible";
            Object[] parameters = new Object[]{false};
            String[] parameterTypesNamesArray = new String[]{"boolean"};
            DataMessage requestMsg, replyMsg;
            String fieldClassName = "javax.swing.JFrame";

            parameters = new Object[]{visible};
            requestMsg = dataMessageBuilder.buildInstanceMethod(dualPeer.getPeerUuid(), fieldClassName,
                    methodName, jframeRef, parameterTypesNamesArray, parameters, new String[parameters.length]);
            replyMsg = dualPeer.sendAndReceive(requestMsg);

        }

        protected static void sleep(long millisecs) {
            try {
                Thread.sleep(millisecs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        final DualPeer dualPeer = new DualPeer("/tests.properties");
        String methodName;

        methodName = "main";
        Class[] parameterTypes = new Class[]{String[].class};
        String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        Object[] parameters = new Object[]{new String[]{}};


        final DataMessage mainRequest = dataMessageBuilder.buildClassMethod(dualPeer.getPeerUuid(),
                swingAppClassName, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);

        // start the swingapp by calling main in background
        Thread asyncSend = new Thread() {
            @Override
            public void run() {
                dualPeer.sendToLogAndForget(mainRequest);
            }
        };
        asyncSend.start();

        // wait for put of JFrame field;
        String fieldName = "frame";
        dualPeer.waitFor(Type.PUT_STATIC_DONE, fieldName);

        // now get the jframe
        DataMessage requestMsg = dataMessageBuilder.buildGetStatic(dualPeer.getPeerUuid(), swingAppClassName, fieldName);
        DataMessage replyMsg = dualPeer.sendAndReceive(requestMsg);
        Primitives.Object myFrame = replyMsg.getReturnValue().getObject();

        // start some actors and pass them the frame to play with
        final Thread[] actors = new Thread[5];
        for (int i = 0; i < actors.length; i++) {
            actors[i] = new Thread(new SwingActor(myFrame.getRef()));
            // introduce some delay and start
            Thread.sleep(150);
            actors[i].start();
        }

        dualPeer.close();
    }
}
