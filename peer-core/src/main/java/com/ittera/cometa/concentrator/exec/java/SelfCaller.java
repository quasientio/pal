package com.ittera.cometa.concentrator.exec.java;

import com.google.common.primitives.Longs;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.google.inject.name.Named;

@Singleton
public class SelfCaller {

	private static final Logger logger = LoggerFactory.getLogger(SelfCaller.class);

	private final UUID peerUuid;
	private final IncomingMessageDispatcher incomingMessageDispatcher;
	private final DataMessageBuilder messageBuilder;
	private final ClassLoader customClassloader;

	private final ZContext context;
	private final String offsetPubAddress;

	@Inject
	SelfCaller(UUID peerUuid, IncomingMessageDispatcher incomingMessageDispatcher, DataMessageBuilder messageBuilder,
						 CustomClassloader customClassloader, ZContext context, @Named("offset.pub") String offsetPubAddress) {
		this.peerUuid = peerUuid;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.messageBuilder = messageBuilder;
		this.customClassloader = customClassloader;
		this.context = context;
		this.offsetPubAddress = offsetPubAddress;
	}

	public DataMessage callMain(String className, List<String> argList) {
		if (logger.isDebugEnabled()) {
			logger.debug("Preparing message to call {}.main() with args: [{}]", className,
				argList == null ? "" : String.join(",", argList));
		}

		// prepare arrays for message construction
		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		IntStream.range(0, parameterTypes.length).forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
		Object[] parameters = new Object[]{new String[]{}};
		if (argList != null) {
			parameters[0] = argList.toArray(new String[0]);
		}

		List<DataMessage> replies = new ArrayList<>();

		// dispatch it with a new named thread, also provided with our custom classloader
		Thread invokingThread = new Thread(() -> {
			// build request message
			DataMessage request = messageBuilder.buildClassMethod(peerUuid, className, "main",
				parameterTypesNamesArray, this, null, parameters, new ObjectRef[parameterTypes.length]);
			replies.add(incomingMessageDispatcher.incomingCall(request, true));
		});
		invokingThread.setName("self-caller");
		invokingThread.setContextClassLoader(customClassloader);

		// prepare offset subscriber
		Socket offsetSubscriber = context.createSocket(SocketType.SUB);
		offsetSubscriber.connect(offsetPubAddress);
		offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

		// start thread and wait for completion
		invokingThread.start();
		try {
			invokingThread.join();
		} catch (InterruptedException e) {
			logger.error("Thread interrupted", e);
		}
		// get reply message
		DataMessage reply = replies.get(0);

		// wait for the reply message offset, to ensure all msg's from have been written to the log
		boolean offsetPublished = false;
		long offset = -1;
		UUID uuid = null;
		while (!offsetPublished) {
			// multi-part msg: 1) offset as byte[], 2) uuid as byte[]
			offset = Longs.fromByteArray(offsetSubscriber.recv());
			uuid = UUIDUtils.fromBytes(offsetSubscriber.recv());
			if (reply.getMessageUuid().equalsIgnoreCase(uuid.toString())) {
				offsetPublished = true;
			}
		}

		// close socket
		offsetSubscriber.close();

		if (logger.isDebugEnabled()) {
			logger.debug("Returning reply message with offset={} and uuid={}", offset, uuid);
		}
		return reply;
	}
}
