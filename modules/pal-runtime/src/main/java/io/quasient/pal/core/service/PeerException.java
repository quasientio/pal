/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

/**
 * Represents a custom exception used to signal fatal errors in peer operations within the PAL
 * runtime. This exception encapsulates a {@link FatalCode} that provides a unique error
 * identification and a descriptive error message.
 */
public class PeerException extends Exception {

  /**
   * Enumerates fatal error codes encountered during peer operations. Each constant holds a unique
   * numeric identifier and an associated descriptive message.
   */
  public enum FatalCode {

    /** Error loading application properties from file or resource. */
    ERROR_LOADING_PROPERTIES(1, "Error loading application properties"),

    /** Error validating application properties. */
    ERROR_VALIDATING_PROPERTIES(2, "Error validating application properties"),

    /** Failure when registering this peer in the Pal Directory. */
    ERROR_REGISTERING_SELF(3, "Error registering self as peer"),

    /** Failure when registering the logs used by this peer. */
    ERROR_REGISTERING_SELF_LOGS(4, "Error registering logs used by self"),

    /** Offset was provided but no log was specified to read from. */
    ERROR_NO_LOG_GIVEN(5, "Offset given but no log to read from"),

    /** No Kafka bootstrap servers configured, required for Source/Write-Ahead logs. */
    ERROR_NO_KAFKA_SERVERS_GIVEN(6, "No kafka servers given, required for Source/Write-Ahead logs"),

    /** Error initializing Source and/or Write-Ahead logs. */
    ERROR_INITIALIZING_LOGS(7, "Error initializing Source/Write-Ahead logs"),

    /** Service manager reported a failure during startup or shutdown. */
    ERROR_SERVICE_MANAGER_FAILED(8, "Service manager failure"),

    /** JAR file not found or missing its MANIFEST file. */
    ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST(9, "JAR not found or missing MANIFEST"),

    /** No Main-Class entry defined in the JAR’s MANIFEST. */
    ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST(10, "No Main-Class in MANIFEST"),

    /** Could not find a free random port for socket binding. */
    ERROR_FINDING_RND_PORT(11, "Error finding local random port for socket"),

    /** Invalid or non-numeric ZMQ-RPC port value provided. */
    ERROR_PARSING_ZMQ_RPC_PORT_NUMBER(12, "Invalid ZMQ-RPC port"),

    /** Invalid or non-numeric JSON-RPC port value provided. */
    ERROR_PARSING_JSON_RPC_PORT_NUMBER(13, "Invalid JSON-RPC port"),

    /** PAL Directory (etcd) is unreachable or unhealthy. */
    ERROR_UNREACHABLE_ETCD(14, "PAL directory unreachable"),

    /** Unexpected failure while launching the requested main entry point. */
    UNEXPECTED_ERROR_LAUNCHING_MAIN(15, "Unexpected error launching main");

    /** Unique numeric code representing the fatal error condition. */
    private final int code;

    /** Descriptive error message associated with this fatal error. */
    private final String message;

    /**
     * Initializes a fatal error code with its corresponding error message and explicit numeric
     * code.
     *
     * @param code the stable numeric code for this fatal error
     * @param message the descriptive error message for this fatal error.
     */
    FatalCode(int code, String message) {
      this.code = code;
      this.message = message;
    }

    /**
     * Returns the unique numeric code for this fatal error.
     *
     * @return the numeric code identifying the error condition.
     */
    public int getCode() {
      return this.code;
    }

    /**
     * Returns the descriptive error message for this fatal error.
     *
     * @return the error message.
     */
    public String getMessage() {
      return this.message;
    }
  }

  /** The fatal error code associated with this exception. */
  private final FatalCode fatalCode;

  /**
   * Constructs a new PeerException using the specified fatal error code. The exception message is
   * derived from the provided error code's descriptive message.
   *
   * @param fatalCode the fatal error code representing the underlying error condition.
   */
  public PeerException(FatalCode fatalCode) {
    super(fatalCode.getMessage());
    this.fatalCode = fatalCode;
  }

  /**
   * Retrieves the fatal error code that triggered this exception.
   *
   * @return the {@link FatalCode} instance representing the specific error condition.
   */
  public FatalCode getFatalCode() {
    return fatalCode;
  }
}
