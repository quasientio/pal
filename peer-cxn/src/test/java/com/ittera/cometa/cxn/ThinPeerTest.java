package com.ittera.cometa.cxn;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;

import java.util.concurrent.atomic.AtomicInteger;

public class ThinPeerTest {

    protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();

    //        protected final String className = "com.ittera.cometa.apps.PrintHelloWorld";
    protected final String className = "com.ittera.cometa.apps.App";

    public void runReqsWithOneClient(int requests) throws Exception {
        ThinPeer thinPeer = new ThinPeer("/tests.properties");

        long start = System.currentTimeMillis();

        final String methodName = "main";

        Class[] parameterTypes = new Class[]{String[].class};
        String[] parameterTypesNamesArray = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesNamesArray[i] = parameterTypes[i].getName();
        }
        Object[] parameters = new Object[]{new String[]{}};

        for (int i = 0; i < requests; i++) {
            DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
            // send async to log and forget
//            thinPeer.sendToLogAndForget(requestMsg);
            // send and wait for reply
            DataMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
        }

        System.out.println("runReqsWithOneClient took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    public void runAsyncReqsWithNClients(int clients, final int requestsPerClient) throws Exception {
        long start = System.currentTimeMillis();

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
                    ThinPeer thinPeer = null;
                    try {
                        thinPeer = new ThinPeer("/tests.properties");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    for (int i = 0; i < requestsPerClient; i++) {
                        DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
                        // send async to log and forget
//                        thinPeer.sendToLogAndForget(requestMsg);
                        // send and wait for reply
                        DataMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
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
        if (args.length != 2) {
            System.out.println("Usage: ..ThinPeerTest <requests> <clients>");
            System.exit(1);
        }

        int requests = Integer.parseInt(args[0]);
        int clients = Integer.parseInt(args[1]);
        Double reqsPerClients = Double.valueOf(requests / clients);

        ThinPeerTest thinPeerTest = new ThinPeerTest();
        thinPeerTest.runReqsWithOneClient(requests);
        thinPeerTest.runAsyncReqsWithNClients(clients, reqsPerClients.intValue());
    }
}
