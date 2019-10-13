package com.ittera.cometa.core;

import com.ittera.cometa.common.lang.annotation.Before;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import static java.lang.String.format;

import org.zeromq.ZContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterceptProcessor {

	private static final Logger logger = LoggerFactory.getLogger(InterceptProcessor.class);
	private final UUID peerUuid;
	private final PALDirectory palDirectory;
	private final ZContext zmqContext;
	private final MessageBuilder messageBuilder;
	private final DispatcherConnector connector;

	@Inject
	InterceptProcessor(UUID peerUuid, PALDirectory palDirectory, ZContext zmqContext, MessageBuilder messageBuilder,
										 DispatcherConnector connector) {
		this.peerUuid = peerUuid;
		this.palDirectory = palDirectory;
		this.zmqContext = zmqContext;
		this.messageBuilder = messageBuilder;
		this.connector = connector;
	}

	public void process(Class clazz) {
		if (logger.isDebugEnabled()) {
			logger.debug("inspecting class '{}' for annotations", clazz.getName());
		}
		List<InterceptRequest> interceptRequests = new ArrayList<>();

		// collect annotations and batch messages
		for (Method method : clazz.getDeclaredMethods()) {
			// process @Before annotation
			Annotation annotation = method.getDeclaredAnnotation(Before.class);
			if (annotation != null) {
				Class<? extends Annotation> type = annotation.annotationType();
				String className, methodName, fieldName;
				try {
					className = (String) type.getDeclaredMethod("clazz").invoke(annotation, (Object[]) null);
					methodName = (String) type.getDeclaredMethod("method").invoke(annotation, (Object[]) null);
					fieldName = (String) type.getDeclaredMethod("field").invoke(annotation, (Object[]) null);
//							String[] args() default {};
					if (logger.isDebugEnabled()) {
						logger.debug(" className: {}, methodName: {}, fieldName: {}", className, methodName, fieldName);
					}
				} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
					logger.error(format("Error processing @before annotation found in method '%s' of class '%s", method.getName(),
						clazz.getName()), e);
					continue;
				}

				// build and queue request message
				interceptRequests.add(messageBuilder.buildInterceptRequest(peerUuid, className, methodName, fieldName,
					clazz.getName(), method.getName()));
			}
		}


		// TODO process @After annotation


		// send all messages at once
		interceptRequests.forEach(connector::sendInterceptRequestMessage);
	}
}
