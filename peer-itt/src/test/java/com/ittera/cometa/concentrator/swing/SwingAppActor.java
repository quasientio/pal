package com.ittera.cometa.concentrator.swing;

import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

public class SwingAppActor {

	protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();

	protected static final String swingAppClassName = "com.ittera.cometa.apps.SwingApp";

	public static void main(String[] args) throws Exception {

		final ThinPeer thinPeer = new ThinPeer("/tests.properties");
		String methodName;

		methodName = "main";
		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};


		final DataMessage mainRequest = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(),
			swingAppClassName, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);

		// start the swingapp by calling main in background
		Thread asyncSend = new Thread() {
			@Override
			public void run() {
				thinPeer.sendToLogAndForget(mainRequest);
			}
		};
		asyncSend.start();

		// wait for put of JFrame field;
		String fieldName = "frame";
		thinPeer.waitFor(Type.PUT_STATIC_DONE, fieldName);

		// now get the jframe
		DataMessage requestMsg = dataMessageBuilder.buildGetStatic(thinPeer.getPeerUuid(), swingAppClassName, fieldName);
		DataMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
		Primitives.Object myFrame = replyMsg.getReturnValue().getObject();

		for (int i = 0; i < 5; i++) {
			sleep(1);

			// set visible = false
			String fieldClassName = "javax.swing.JFrame";
			methodName = "setVisible";
			parameters = new Object[]{false};
			parameterTypesNamesArray = new String[]{"boolean"};
			requestMsg = dataMessageBuilder.buildInstanceMethod(thinPeer.getPeerUuid(), fieldClassName,
				methodName, myFrame.getRef(), parameterTypesNamesArray, parameters, new String[parameters.length]);
			replyMsg = thinPeer.sendAndReceive(requestMsg);

			sleep(1);

			// reset visible = true
			parameters = new Object[]{Boolean.TRUE};
			requestMsg = dataMessageBuilder.buildInstanceMethod(thinPeer.getPeerUuid(), fieldClassName,
				methodName, myFrame.getRef(), parameterTypesNamesArray, parameters, new String[parameters.length]);
			replyMsg = thinPeer.sendAndReceive(requestMsg);
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
}
