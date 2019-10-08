package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.BiMapObjectService;
import com.ittera.cometa.common.ObjectService;

import com.ittera.cometa.core.exec.DispatcherConnector;

import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;

import java.util.UUID;
import java.util.Arrays;

import static java.util.stream.Collectors.*;

import org.junit.Before;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.mockito.AdditionalAnswers;

import static org.mockito.Mockito.*;

public abstract class AbstractDispatcherTest {

	protected UUID peerUuid = UUID.randomUUID();

	protected ObjectService objectService = new BiMapObjectService();

	protected MessageBuilder messageBuilder = new ProtobufMessageBuilder();

	protected DispatcherConnector dispatcherConnector;

	protected AbstractDispatcherTest() {

		// set up mock dispatcher so it returns always the sent message
		dispatcherConnector = mock(DispatcherConnector.class);
		when(dispatcherConnector.sendAndRecv(any())).then(AdditionalAnswers.returnsFirstArg());
	}

	private void verifyDispatcherConnectorCalledTimes(int n) {
		verify(dispatcherConnector, times(n)).sendAndRecv(any());
	}

	protected void verifyDispatcherConnectorCalledTwice() {
		verifyDispatcherConnectorCalledTimes(2);
	}

	protected void verifyDispatcherConnectorCalledOnce() {
		verifyDispatcherConnectorCalledTimes(1);
	}

	protected String[] toNames(Class[] types) {
		return Arrays.stream(types).map(p -> p.getName()).collect(toList()).toArray(new String[types.length]);
	}

	@Before
	public void clearStuff() {
		objectService.clear();
		assertThat(objectService.size(), is(0));
	}
}
