package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import javax.inject.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IncomingMessageDispatcher {

	protected static final Logger logger = LoggerFactory.getLogger(IncomingMessageDispatcher.class);

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

	/**
	 *
	 * @param dataMessage Message to invoke
	 * @param isDirect true if message comes from this or another peer, false if it comes from a log
	 * @return the returnValue message
	 */
	public DataMessage incomingCall(DataMessage dataMessage, boolean isDirect) {

		if (dataMessage.hasConstructorCall()) {
			return constructorDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasClassMethodCall()) {
			return classMethodDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasInstanceMethodCall()) {
			return instanceMethodDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasStaticFieldGet()) {
			return getClassVariableDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasInstanceFieldGet()) {
			return getInstanceVariableDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasStaticFieldPut()) {
			return setClassVariableDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else if (dataMessage.hasInstanceFieldPut()) {
			return setInstanceVariableDispatcher.dispatchIncoming(dataMessage, isDirect);
		} else {
			throw new IllegalArgumentException(String.format("Incoming message with uuid ignored - no handler:%n%s",
				dataMessage));
		}
	}
}
