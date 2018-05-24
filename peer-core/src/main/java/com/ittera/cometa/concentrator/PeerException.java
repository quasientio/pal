package com.ittera.cometa.concentrator;

/**
 * TODO use i18n resources for messages
 */
enum PeerFatalCode {

	ERROR_LOADING_PROPERTIES(1, "Error loading application properties"),
	ERROR_CONNECTING_TO_DIRECTORY(2, "Error connecting to directory"),
	ERROR_REGISTERING_PEER(3, "Error registering peer"),
	ERROR_NO_LOG_GIVEN(4, "Offset given but no log to read from"),
	ERROR_INITIALIZING_LOGS(5, "Error initializing IN/OUT logs"),
	ERROR_SERVICE_MANAGER_FAILED(6, "Service manager failure"),
	ERROR_NO_ZOOKEEPER_URL_GIVEN(7, "Missing zookeeper URL");

	private final int code;
	private final String message;

	PeerFatalCode(int code, String message) {
		this.code = code;
		this.message = message;
	}

	int getCode() {
		return this.code;
	}

	String getMessage() {
		return this.message;
	}
}
