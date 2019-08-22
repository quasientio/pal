package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;

import com.ittera.cometa.cxn.PeerLogDirectory;

import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.Injector;

class LogConfigurator {

	protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);
	private final PeerOptions peerOptions;
	private final Properties appProps;
	private final Injector injector;

	LogConfigurator(PeerOptions peerOptions, Properties appProps, Injector injector) {
		this.peerOptions = peerOptions;
		this.appProps = appProps;
		this.injector = injector;
	}

	private LogInfo registerNewLog() throws Exception {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		final String kafkaTopicPrefix = appProps.getProperty("kafkaTopic");
		return registry.createLog(kafkaTopicPrefix);
	}

	private LogInfo registerGivenLog(LogInfo givenLogInfo) throws Exception {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		final LogInfo logInfo;

		// register given log if not registered
		if (registry.logExists(givenLogInfo.getName())) {
			logInfo = registry.getLogInfo(givenLogInfo.getName());
		} else {
			logInfo = registry.addGivenLog(givenLogInfo.getName());
		}

		return logInfo;
	}

	private void readFromLog(LogInfo log, boolean inAndOutAreSameLog, Long initialOffset) throws Exception {
		KafkaDataMessageReader logMessageReader = injector.getInstance(KafkaDataMessageReader.class);
		logMessageReader.readFromLog(log.getName(), inAndOutAreSameLog, initialOffset);
	}

	private void writeToLog(LogInfo outLog, LogInfo inLog) {
		KafkaDataMessageWriter logMessageWriter = injector.getInstance(KafkaDataMessageWriter.class);
		boolean publishOffsets = outLog.equals(inLog);
		logMessageWriter.writeToLog(outLog, inLog, publishOffsets);
	}

	void init() throws Exception {

		// register log(s)
		LogInfo inLog, outLog, newLog = null;

		if (peerOptions.inLog != null) {
			inLog = registerGivenLog(peerOptions.inLog);
		} else { // no log given, create new
			inLog = registerNewLog();
			newLog = inLog;
		}

		if (peerOptions.outLog != null) {
			outLog = registerGivenLog(peerOptions.outLog);
		} else { // no log given, create new if not done already
			if (newLog == null) {
				newLog = registerNewLog();
			}
			outLog = newLog;
		}

		// init log reader+writer
		boolean inAndOutAreSame = inLog.equals(outLog);
		readFromLog(inLog, inAndOutAreSame, peerOptions.offset);
		writeToLog(outLog, inLog);
	}
}
