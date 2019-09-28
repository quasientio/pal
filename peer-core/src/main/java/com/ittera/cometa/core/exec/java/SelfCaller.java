package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.core.PeerException;
import com.ittera.cometa.core.RunOptions;
import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.primitives.Longs;

import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
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
	private final ExecMessageBuilder messageBuilder;
	private final ClassLoader customClassloader;
	private final ZContext context;
	private final String offsetPubAddress;
	private final EnumSet<RunOptions> runOptions;

	@Inject
	SelfCaller(UUID peerUuid, IncomingMessageDispatcher incomingMessageDispatcher, ExecMessageBuilder messageBuilder,
						 CustomClassloader customClassloader, ZContext context, @Named("offset.pub") String offsetPubAddress,
						 EnumSet<RunOptions> runOptions) {
		this.peerUuid = peerUuid;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.messageBuilder = messageBuilder;
		this.customClassloader = customClassloader;
		this.context = context;
		this.offsetPubAddress = offsetPubAddress;
		this.runOptions = runOptions;
	}

	public ExecMessage callMain(String className, List<String> argList) {
		if (logger.isDebugEnabled()) {
			logger.debug("Preparing message to call {}.main() with args: [{}]", className,
				argList == null ? "" : String.join(",", argList));
		}

		// prepare arrays for message construction
		final Class[] parameterTypes = new Class[]{String[].class};
		final String[] parameterTypesNamesArray = new String[parameterTypes.length];
		IntStream.range(0, parameterTypes.length).forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
		final Object[] parameters = new Object[]{new String[]{}};
		if (argList != null) {
			parameters[0] = argList.toArray(new String[0]);
		}

		final List<ExecMessage> replies = new ArrayList<>();

		// dispatch it with a new named thread, also provided with our custom classloader
		Thread invokingThread = new Thread(() -> {
			// build request message
			ExecMessage request = messageBuilder.buildClassMethod(peerUuid, className, "main",
				parameterTypesNamesArray, this, null, parameters, new ObjectRef[parameterTypes.length]);
			replies.add(incomingMessageDispatcher.incomingCall(request, true));
		});
		invokingThread.setName("self-caller");
		invokingThread.setContextClassLoader(customClassloader);

		// prepare offset subscriber
		Socket offsetSubscriber = null;
		if (!runOptions.contains(RunOptions.LOGLESS)) {
			offsetSubscriber = context.createSocket(SocketType.SUB);
			offsetSubscriber.connect(offsetPubAddress);
			offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
		}

		// start thread and wait for completion
		invokingThread.start();
		try {
			invokingThread.join();
		} catch (InterruptedException e) {
			logger.error("Thread interrupted", e);
		}
		// get reply message
		final ExecMessage reply = replies.get(0);

		// wait for the reply message offset, to ensure all msg's from have been written to the log
		if (!runOptions.contains(RunOptions.LOGLESS)) {
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
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Returning reply message with uuid={}", reply.getMessageUuid());
			}
		}
		return reply;
	}

	public ExecMessage callJar(String jarFile, List<String> argList) throws PeerException {
		if (logger.isDebugEnabled()) {
			logger.debug("Call jar `{}` with args: [{}]",
				jarFile, argList == null ? "" : String.join(",", argList));
		}

		final Attributes attributes;

		try (JarFile jar = new JarFile(jarFile)) {
			attributes = jar.getManifest().getMainAttributes();
		} catch (IOException e) {
			logger.error("Error loading Manifest from JAR", e);
			throw new PeerException(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST);
		}
		final String mainClass = attributes.getValue("Main-Class");
		if (mainClass == null) {
			throw new PeerException(PeerException.FatalCode.ERROR_NO_MAINCLASS_IN_JAR_MANIFEST);
		}
		return callMain(mainClass, argList);
	}
}
