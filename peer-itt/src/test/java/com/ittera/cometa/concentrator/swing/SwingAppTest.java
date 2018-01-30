package com.ittera.cometa.concentrator.swing;

import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

public class SwingAppTest {

	protected static final DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();

	protected static final String className = "com.ittera.cometa.apps.SwingApp";

	public static void main(String[] args) throws Exception {
		ThinPeer thinPeer = new ThinPeer("/tests.properties");
		final String methodName = "main";

		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};

		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(),
			className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
		DataMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
	}
}
