package com.ittera.cometa.core.swing;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.Properties;

import java.io.InputStream;

public class SwingAppTest {

	protected static final MessageBuilder MESSAGE_BUILDER = new ProtobufMessageBuilder();

	protected static final String className = "com.ittera.cometa.apps.SwingApp";
	protected static final String TEST_PROPERTIES_PATH = "/tests.properties";

	public static void main(String[] args) throws Exception {

		final Properties properties = new Properties();
		try (final InputStream stream = SwingAppConcurrentActor.class.getResourceAsStream(TEST_PROPERTIES_PATH)) {
			properties.load(stream);
		}
		final ThinPeer thinPeer = new ThinPeer(properties);
		final String methodName = "main";

		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};

		ExecMessage requestMsg = MESSAGE_BUILDER.buildClassMethod(thinPeer.getPeerUuid(),
			className, methodName, parameterTypesNamesArray, null, null, parameters,
			new ObjectRef[parameterTypes.length]);
		ExecMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
	}
}
