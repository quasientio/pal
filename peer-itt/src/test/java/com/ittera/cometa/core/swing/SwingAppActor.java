package com.ittera.cometa.core.swing;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import java.util.Properties;

import java.io.InputStream;

public class SwingAppActor {

	protected static final ExecMessageBuilder execMessageBuilder = new ProtobufExecMessageBuilder();

	protected static final String swingAppClassName = "com.ittera.cometa.apps.SwingApp";
	protected static final String TEST_PROPERTIES_PATH = "/tests.properties";

	public static void main(String[] args) throws Exception {
		final Properties properties = new Properties();
		try (final InputStream stream = SwingAppActor.class.getResourceAsStream(TEST_PROPERTIES_PATH)) {
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


		final ExecMessage mainRequest = execMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(),
			swingAppClassName, methodName, parameterTypesNamesArray, null, null,
			parameters, new ObjectRef[parameterTypes.length]);

		// start the swingapp by calling main in background
		new Thread(() -> thinPeer.sendToLogAndForget(mainRequest)).start();

		// wait for put of JFrame field;
		String fieldName = "frame";
		thinPeer.waitFor(Type.PUT_STATIC_DONE, fieldName);

		// now get the jframe
		ExecMessage requestMsg = execMessageBuilder.buildGetStatic(thinPeer.getPeerUuid(), swingAppClassName, fieldName);
		ExecMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
		Primitives.Object myFrame = replyMsg.getReturnValue().getObject();

		for (int i = 0; i < 5; i++) {
			sleep(1);

			// set visible = false
			String fieldClassName = "javax.swing.JFrame";
			methodName = "setVisible";
			parameters = new Object[]{false};
			parameterTypesNamesArray = new String[]{"boolean"};
			requestMsg = execMessageBuilder.buildInstanceMethod(thinPeer.getPeerUuid(), fieldClassName,
				methodName, null, ObjectRef.from(myFrame.getRef()), parameterTypesNamesArray, parameters,
				new ObjectRef[parameters.length]);
			thinPeer.sendAndReceive(requestMsg);

			sleep(1);

			// reset visible = true
			parameters = new Object[]{Boolean.TRUE};
			requestMsg = execMessageBuilder.buildInstanceMethod(thinPeer.getPeerUuid(), fieldClassName,
				methodName, null, ObjectRef.from(myFrame.getRef()), parameterTypesNamesArray, parameters,
				new ObjectRef[parameters.length]);
			thinPeer.sendAndReceive(requestMsg);
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
