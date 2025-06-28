/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

/**
 * Represents a custom exception used to signal fatal errors in peer operations within the PAL
 * runtime. This exception encapsulates a {@link FatalCode} that provides a unique error
 * identification and a descriptive error message.
 */
public class PeerException extends Exception {

  /** Static counter used to generate unique numeric codes for each {@link FatalCode} instance. */
  private static int counter = 1;

  /**
   * Enumerates fatal error codes encountered during peer operations. Each constant holds a unique
   * numeric identifier and an associated descriptive message.
   */
  public enum FatalCode {

    // TODO use i18n resources for messages
    ERROR_LOADING_PROPERTIES("Error loading application properties"),
    ERROR_REGISTERING_SELF("Error registering self as peer"),
    ERROR_REGISTERING_SELF_LOGS("Error registering logs used by self"),
    ERROR_NO_LOG_GIVEN("Offset given but no log to read from"),

    ERROR_NO_KAFKA_SERVERS_GIVEN("No kafka servers given, required for IN/OUT logs"),
    ERROR_INITIALIZING_LOGS("Error initializing IN/OUT logs"),
    ERROR_SERVICE_MANAGER_FAILED("Service manager failure"),
    ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST("JAR not found or missing MANIFEST"),
    ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST("No Main-Class in MANIFEST"),
    ERROR_FINDING_RND_PORT("Error finding local random port for socket"),
    ERROR_PARSING_RPC_PORT_NUMBER("Invalid RPC port"),
    ERROR_PARSING_JSONRPC_PORT_NUMBER("Invalid JSONRPC port");

    /** Unique numeric code representing the fatal error condition. */
    private final int code;

    /** Descriptive error message associated with this fatal error. */
    private final String message;

    /**
     * Initializes a fatal error code with its corresponding error message. A unique numeric code is
     * automatically assigned based on a static counter.
     *
     * @param message the descriptive error message for this fatal error.
     */
    FatalCode(String message) {
      this.code = counter++;
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
