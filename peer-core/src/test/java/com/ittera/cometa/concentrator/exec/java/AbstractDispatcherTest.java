package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.BiMapObjectService;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.common.ObjectService;

import java.util.UUID;

import org.mockito.AdditionalAnswers;

import static org.mockito.Mockito.*;

public abstract class AbstractDispatcherTest {

	protected UUID peerUuid = UUID.randomUUID();

	protected ObjectService objectService = new BiMapObjectService();

	protected DataMessageBuilder messageBuilder = new ProtobufDataMessageBuilder(objectService);

	protected DispatcherConnector dispatcherConnector;

	protected AbstractDispatcherTest() {

		// set up mock dispatcher so it returns always the sent message
		dispatcherConnector = mock(DispatcherConnector.class);
		when(dispatcherConnector.sendAndRecv(any())).then(AdditionalAnswers.returnsFirstArg());
	}

	protected void verifyDispatcherCalledTwice() {
		verify(dispatcherConnector, times(2)).sendAndRecv(any());
	}
}
