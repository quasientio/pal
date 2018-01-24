package com.ittera.cometa.concentrator;

import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.BiMapObjectService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;


public abstract class AbstractPeerIntegrationTest extends PeerMessageAssertions {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

	protected final static UUID clientId = UUID.randomUUID();

	protected static DataMessageBuilder dataMessageBuilder;
	private static ThinPeer thinPeer;

	@BeforeClass
	public static void initialize() throws Exception {

		// configure wiring
		AbstractModule module = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ObjectService.class).to(BiMapObjectService.class).asEagerSingleton();
				bind(DataMessageBuilder.class).to(ProtobufDataMessageBuilder.class).asEagerSingleton();
			}
		};

		final Injector injector = Guice.createInjector(module);
		dataMessageBuilder = injector.getInstance(DataMessageBuilder.class);
		dataMessageBuilder.dontStoreObjects();

		// configure services
		final Set<Service> services = new HashSet<>();
		services.add((Service) injector.getInstance(ObjectService.class));
		final ServiceManager manager = new ServiceManager(services);
		manager.startAsync();

		// we run tests read-writing exclusively from log (no p2p talk)
		boolean allowP2P = Boolean.parseBoolean(System.getProperty("peer.allowP2P", "true"));
		thinPeer = new ThinPeer("/tests.properties", allowP2P);
	}

	protected DataMessage sendAndReceive(DataMessage message) throws Exception {
		return thinPeer.sendAndReceive(message);
	}

	@AfterClass
	public static void finalizeStuff() {
		logger.debug("Finalizing after tests...");
		if (thinPeer != null) {
			thinPeer.close();
		}
	}
}

