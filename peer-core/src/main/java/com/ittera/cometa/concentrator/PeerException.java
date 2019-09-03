package com.ittera.cometa.concentrator;

public class PeerException extends Exception {

	public enum FatalCode {

		/**
		 * TODO use i18n resources for messages
		 */
		ERROR_LOADING_PROPERTIES(1, "Error loading application properties"),
		ERROR_CONNECTING_TO_DIRECTORY(2, "Error connecting to directory"),
		ERROR_REGISTERING_PEER(3, "Error registering peer"),
		ERROR_NO_LOG_GIVEN(4, "Offset given but no log to read from"),
		ERROR_INITIALIZING_LOGS(5, "Error initializing IN/OUT logs"),
		ERROR_SERVICE_MANAGER_FAILED(6, "Service manager failure"),
		ERROR_NO_ZOOKEEPER_URL_GIVEN(7, "Missing zookeeper URL"),
		ERROR_NO_MAINCLASS_IN_JAR_MANIFEST(8, "No Main-Class in MANIFEST");

		private final int code;
		private final String message;

		FatalCode(int code, String message) {
			this.code = code;
			this.message = message;
		}

		public int getCode() {
			return this.code;
		}

		public String getMessage() {
			return this.message;
		}
	}

	private FatalCode fatalCode;

	public PeerException(FatalCode fatalCode) {
		super(fatalCode.getMessage());
		this.fatalCode = fatalCode;
	}

	public FatalCode getFatalCode() {
		return fatalCode;
	}
}