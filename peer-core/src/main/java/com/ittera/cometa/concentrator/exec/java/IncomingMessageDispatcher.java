package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import javax.inject.Singleton;
import javax.inject.Inject;

@Singleton
public class IncomingMessageDispatcher {

	// constructor & method dispatchers
	@Inject
	private ConstructorDispatcher constructorDispatcher;
	@Inject
	private ClassMethodDispatcher classMethodDispatcher;
	@Inject
	private InstanceMethodDispatcher instanceMethodDispatcher;

	// fieldop dispatchers
	@Inject
	private GetClassVariableDispatcher getClassVariableDispatcher;
	@Inject
	private SetClassVariableDispatcher setClassVariableDispatcher;
	@Inject
	private GetInstanceVariableDispatcher getInstanceVariableDispatcher;
	@Inject
	private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

	public DataMessage incomingCall(DataMessage dataMessage) {

		if (dataMessage.hasConstructorCall()) {
			return constructorDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasClassMethodCall()) {
			return classMethodDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasInstanceMethodCall()) {
			return instanceMethodDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasStaticFieldGet()) {
			return getClassVariableDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasInstanceFieldGet()) {
			return getInstanceVariableDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasStaticFieldPut()) {
			return setClassVariableDispatcher.dispatchIncoming(dataMessage);
		} else if (dataMessage.hasInstanceFieldPut()) {
			return setInstanceVariableDispatcher.dispatchIncoming(dataMessage);
		} else {
			throw new IllegalArgumentException(String.format("Incoming message with uuid ignored - no handler:\n%s",
				dataMessage));
		}
	}
}
