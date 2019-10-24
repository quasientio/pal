package com.ittera.cometa.core;

public class PeerException extends Exception {

  public enum FatalCode {

    /** TODO use i18n resources for messages */
    ERROR_LOADING_PROPERTIES(1, "Error loading application properties"),
    ERROR_REGISTERING_PEER(2, "Error registering peer"),
    ERROR_NO_LOG_GIVEN(3, "Offset given but no log to read from"),
    ERROR_INITIALIZING_LOGS(4, "Error initializing IN/OUT logs"),
    ERROR_SERVICE_MANAGER_FAILED(5, "Service manager failure"),
    ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST(6, "JAR not found or missing MANIFEST"),
    ERROR_NO_MAINCLASS_IN_JAR_MANIFEST(7, "No Main-Class in MANIFEST"),
    ERROR_FINDING_REQ_SOCKET(8, "Error finding local random port for REQs"),
    ERROR_PARSING_REQ_PORT_NUMBER(9, "Invalid TCP_REQ port");

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
