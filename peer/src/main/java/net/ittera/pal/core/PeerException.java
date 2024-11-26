/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

public class PeerException extends Exception {

  private static int counter = 1;

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

    private final int code;
    private final String message;

    FatalCode(String message) {
      this.code = counter++;
      this.message = message;
    }

    public int getCode() {
      return this.code;
    }

    public String getMessage() {
      return this.message;
    }
  }

  private final FatalCode fatalCode;

  public PeerException(FatalCode fatalCode) {
    super(fatalCode.getMessage());
    this.fatalCode = fatalCode;
  }

  public FatalCode getFatalCode() {
    return fatalCode;
  }
}
