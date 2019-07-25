package com.ittera.cometa;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

public class LogInfoTest {

	@Test
	public void logInfo_nameOnly_newLogInfo() {

		String logName = "MyApp";
		LogInfo logInfo = new LogInfo(logName);

		assertEquals(logName, logInfo.getName());
		assertNull(logInfo.getBootstrapServers());
	}

	@Test
	public void logInfo_nameAndOneBroker_bootstrapServersOK() {

		String logName = "MyApp";
		KafkaBrokerEndpoint[] brokerEndpoints = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		KafkaBrokerInfo brokerInfo = new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints, "328972349");
		Set<KafkaBrokerInfo> brokerInfoSet = new HashSet<>();
		brokerInfoSet.add(brokerInfo);

		LogInfo logInfo = new LogInfo(logName, brokerInfoSet);

		// verify log name
		assertEquals(logName, logInfo.getName());

		// verify bootstrap servers
		String[] expectedBootstrapServers = {
			"localdomain.localhost:4091",
			"127.0.0.1:4092"
		};
		assertArrayEquals(
			Arrays.stream(expectedBootstrapServers).sorted().toArray(),
			Arrays.stream(logInfo.getBootstrapServers().split(",")).sorted().toArray());
	}

	@Test
	public void logInfo_nameAndTwoBrokers_bootstrapServersOK() {

		String logName = "MyApp";
		Set<KafkaBrokerInfo> brokerInfoSet = new HashSet<>();

		// broker 1
		KafkaBrokerEndpoint[] brokerEndpoints1 = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		brokerInfoSet.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints1, "328972349"));

		// broker 2
		KafkaBrokerEndpoint[] brokerEndpoints2 = {
			new KafkaBrokerEndpoint("PLAINTEXT", "somedomain.com:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "localhost:4092")};

		brokerInfoSet.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints2, "328972349"));

		LogInfo logInfo = new LogInfo(logName, brokerInfoSet);

		// verify log name
		assertEquals(logName, logInfo.getName());

		// verify bootstrap servers
		String[] expectedBootstrapServers = {
			"localdomain.localhost:4091",
			"127.0.0.1:4092",
			"somedomain.com:4091",
			"localhost:4092"};
		System.out.println(logInfo.getBootstrapServers());
		assertArrayEquals(
			Arrays.stream(expectedBootstrapServers).sorted().toArray(),
			Arrays.stream(logInfo.getBootstrapServers().split(",")).sorted().toArray());
	}

	@Test
	public void equals_differentNameSameBrokers_false() {

		String logName1 = "MyApp";
		String logName2 = "MyOtherApp";
		Set<KafkaBrokerInfo> brokerInfoSet = new HashSet<>();

		// broker
		KafkaBrokerEndpoint[] brokerEndpoints1 = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		brokerInfoSet.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints1, "328972349"));

		LogInfo logInfo1 = new LogInfo(logName1, brokerInfoSet);
		LogInfo logInfo2 = new LogInfo(logName2, brokerInfoSet);

		assertFalse(logInfo1.equals(logInfo2));
	}

	@Test
	public void equals_sameNameDifferentBrokers_false() {

		String logName = "MyApp";
		Set<KafkaBrokerInfo> brokerInfoSet1 = new HashSet<>();
		Set<KafkaBrokerInfo> brokerInfoSet2 = new HashSet<>();

		// broker 1
		KafkaBrokerEndpoint[] brokerEndpoints1 = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		brokerInfoSet1.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints1, "328972349"));

		// broker 2
		KafkaBrokerEndpoint[] brokerEndpoints2 = {
			new KafkaBrokerEndpoint("PLAINTEXT", "somedomain.com:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "localhost:4092")};

		brokerInfoSet2.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints2, "328972349"));

		LogInfo logInfo1 = new LogInfo(logName, brokerInfoSet1);
		LogInfo logInfo2 = new LogInfo(logName, brokerInfoSet2);

		assertFalse(logInfo1.equals(logInfo2));
	}

	@Test
	public void equals_sameNameSameBrokers_true() {

		String logName = "MyApp";
		Set<KafkaBrokerInfo> brokerInfoSet1 = new HashSet<>();
		Set<KafkaBrokerInfo> brokerInfoSet2 = new HashSet<>();

		// broker 1
		KafkaBrokerEndpoint[] brokerEndpoints1 = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		brokerInfoSet1.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints1, "328972349"));

		// broker 2
		KafkaBrokerEndpoint[] brokerEndpoints2 = {
			new KafkaBrokerEndpoint("SSL", "localdomain.localhost:4091"),
			new KafkaBrokerEndpoint("PLAINTEXT", "127.0.0.1:4092")};

		brokerInfoSet2.add(new KafkaBrokerInfo(1, "localhost", 4090, 2001,
			brokerEndpoints2, "328972349"));

		LogInfo logInfo1 = new LogInfo(logName, brokerInfoSet1);
		LogInfo logInfo2 = new LogInfo(logName, brokerInfoSet2);

		assertTrue(logInfo1.equals(logInfo2));
	}
}
