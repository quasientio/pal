package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DualPeerTest {

    protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder(null);

    //        protected final String className = "com.ittera.cometa.apps.PrintHelloWorld";
    protected final String className = "com.ittera.cometa.apps.App";

    public void runReqsWithOneClient() throws Exception {
        DualPeer dualPeer = new DualPeer("/tests.properties");

        long start = System.currentTimeMillis();

        final String methodName = "main";

        Class[] parameterTypes = new Class[]{String[].class};
        String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        Object[] parameters = new Object[]{new String[]{}};

        final int requests = 5000;
        for (int i = 0; i < requests; i++) {
            DataMessage requestMsg = dataMessageBuilder.buildClassMethod(dualPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
            // send async to log and forget
//            dualPeer.sendToLogAndForget(requestMsg);
            // send and wait for reply
            DataMessage replyMsg = dualPeer.sendAndReceive(requestMsg);
        }

        System.out.println("runReqsWithOneClient took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    public void runAsyncReqsWithNClients() throws Exception {
        long start = System.currentTimeMillis();

        final int clients = 100;
        final int requests = 50;

        //test main
        final String methodName = "main";

        // we can reuse this. so better just once
        final Class[] parameterTypes = new Class[]{String[].class};
        final String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        final Object[] parameters = new Object[]{new String[]{}};
        Thread[] clientList = new Thread[clients];

        final AtomicInteger finishedThreads = new AtomicInteger(0);

        // create all threads
        for (int i = 0; i < clients; i++) {
            Thread client = new Thread() {
                @Override
                public void run() {
                    DualPeer dualPeer = null;
                    try {
                        dualPeer = new DualPeer("/tests.properties");
                    } catch (IOException ie) {
                        ie.printStackTrace();
                    }
                    for (int i = 0; i < requests; i++) {
                        DataMessage requestMsg = dataMessageBuilder.buildClassMethod(dualPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
                        // send async to log and forget
//                        dualPeer.sendToLogAndForget(requestMsg);
                        // send and wait for reply
                        DataMessage replyMsg = dualPeer.sendAndReceive(requestMsg);
                    }
                    finishedThreads.incrementAndGet();
                }
            };
            clientList[i] = client;
        }


        // then start all clients at once
        for (int i = 0; i < clients; i++) {
            clientList[i].start();
        }

        while (finishedThreads.get() < clients) {
            Thread.sleep(1);
        }

        System.out.println("runAsyncReqsWithNClients took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    public static void main(String[] args) throws Exception {
        DualPeerTest dualPeerTest = new DualPeerTest();
        dualPeerTest.runReqsWithOneClient();
        dualPeerTest.runAsyncReqsWithNClients();
    }
}
