package com.ittera.cometa.concentrator.exec.java;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.UUID;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.common.ObjectService;
import org.zeromq.ZContext;

@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest {

	//	@Mock
	UUID peerUuid;

	@Mock
	DataMessageBuilder messageBuilder;

	//	@Mock
	String outCellAddress;

	@Mock
	ZContext zmqContext;

	@Mock
	protected static ObjectService objectService;

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void dispatch() {
	}
}