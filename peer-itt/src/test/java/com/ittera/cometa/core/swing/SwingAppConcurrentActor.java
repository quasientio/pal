package com.ittera.cometa.core.swing;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import java.util.Properties;

import java.io.InputStream;

public class SwingAppConcurrentActor {

	protected static final MessageBuilder MESSAGE_BUILDER = new ProtobufMessageBuilder();

	protected static final String swingAppClassName = "com.ittera.cometa.apps.SwingApp";
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
			Object[] parameters = new Object[]{false};
			String[] parameterTypesNamesArray = new String[]{"boolean"};
			ExecMessage requestMsg, replyMsg;
			String fieldClassName = "javax.swing.JFrame";

			parameters = new Object[]{visible};
			requestMsg = MESSAGE_BUILDER.buildInstanceMethod(thinPeer.getPeerUuid(), fieldClassName,
				methodName, null, ObjectRef.from(jframeRef), parameterTypesNamesArray, parameters,
				new ObjectRef[parameters.length]);
			try {
				thinPeer.sendAndReceive(requestMsg);
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

	public static void main(String[] args) throws Exception {

		final Properties properties = new Properties();
		try (final InputStream stream = SwingAppConcurrentActor.class.getResourceAsStream(TEST_PROPERTIES_PATH)) {
			properties.load(stream);
		}
		final ThinPeer thinPeer = new ThinPeer(properties);
		String methodName;

		methodName = "main";
		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};


		final ExecMessage mainRequest = MESSAGE_BUILDER.buildClassMethod(thinPeer.getPeerUuid(),
			swingAppClassName, methodName, parameterTypesNamesArray, null, null, parameters,
			new ObjectRef[parameterTypes.length]);

		// start the swingapp by calling main in background
		new Thread(() -> thinPeer.sendToLogAndForget(mainRequest)).start();

		// wait for put of JFrame field;
		String fieldName = "frame";
		thinPeer.waitFor(Type.PUT_STATIC_DONE, fieldName);

		// now get the jframe
		ExecMessage requestMsg = MESSAGE_BUILDER.buildGetStatic(thinPeer.getPeerUuid(), swingAppClassName,
			fieldName);
		ExecMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
		Primitives.Object myFrame = replyMsg.getReturnValue().getObject();

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
}
