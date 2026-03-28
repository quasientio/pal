/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quasient.pal.core.service.PeerException.FatalCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for {@link PeerException} and {@link FatalCode}.
 *
 * <p>Verifies that all fatal error codes are properly defined with unique codes and descriptive
 * messages.
 */
public class PeerExceptionTest {

  // ===== Tests for FatalCode enum =====

  @Test
  public void fatalCode_allValuesHaveUniqueCodes() {
    Set<Integer> codes = new HashSet<>();
    for (FatalCode code : FatalCode.values()) {
      boolean added = codes.add(code.getCode());
      assertThat("Duplicate code found for " + code.name(), added, is(true));
    }
  }

  @Test
  public void fatalCode_allValuesHaveNonEmptyMessages() {
    for (FatalCode code : FatalCode.values()) {
      assertThat(code.name() + " should have non-null message", code.getMessage(), notNullValue());
      assertThat(
          code.name() + " should have non-empty message", code.getMessage().isEmpty(), is(false));
    }
  }

  @Test
  public void fatalCode_valuesCount_is15() {
    assertThat(FatalCode.values().length, is(15));
  }

  @Test
  public void fatalCode_codesRangeFrom1To15() {
    for (FatalCode code : FatalCode.values()) {
      assertThat(code.name() + " code should be >= 1", code.getCode() >= 1, is(true));
      assertThat(code.name() + " code should be <= 15", code.getCode() <= 15, is(true));
    }
  }

  // ===== Tests for specific FatalCode values =====

  @Test
  public void fatalCode_errorLoadingProperties_hasCode1() {
    assertThat(FatalCode.ERROR_LOADING_PROPERTIES.getCode(), is(1));
    assertThat(FatalCode.ERROR_LOADING_PROPERTIES.getMessage(), containsString("loading"));
  }

  @Test
  public void fatalCode_errorValidatingProperties_hasCode2() {
    assertThat(FatalCode.ERROR_VALIDATING_PROPERTIES.getCode(), is(2));
    assertThat(FatalCode.ERROR_VALIDATING_PROPERTIES.getMessage(), containsString("validating"));
  }

  @Test
  public void fatalCode_errorRegisteringSelf_hasCode3() {
    assertThat(FatalCode.ERROR_REGISTERING_SELF.getCode(), is(3));
    assertThat(FatalCode.ERROR_REGISTERING_SELF.getMessage(), containsString("registering"));
  }

  @Test
  public void fatalCode_errorRegisteringSelfLogs_hasCode4() {
    assertThat(FatalCode.ERROR_REGISTERING_SELF_LOGS.getCode(), is(4));
    assertThat(FatalCode.ERROR_REGISTERING_SELF_LOGS.getMessage(), containsString("logs"));
  }

  @Test
  public void fatalCode_errorNoLogGiven_hasCode5() {
    assertThat(FatalCode.ERROR_NO_LOG_GIVEN.getCode(), is(5));
    assertThat(FatalCode.ERROR_NO_LOG_GIVEN.getMessage(), containsString("log"));
  }

  @Test
  public void fatalCode_errorNoKafkaServersGiven_hasCode6() {
    assertThat(FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(), is(6));
    assertThat(FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage(), containsString("kafka"));
  }

  @Test
  public void fatalCode_errorInitializingLogs_hasCode7() {
    assertThat(FatalCode.ERROR_INITIALIZING_LOGS.getCode(), is(7));
    assertThat(FatalCode.ERROR_INITIALIZING_LOGS.getMessage(), containsString("logs"));
  }

  @Test
  public void fatalCode_errorServiceManagerFailed_hasCode8() {
    assertThat(FatalCode.ERROR_SERVICE_MANAGER_FAILED.getCode(), is(8));
    assertThat(FatalCode.ERROR_SERVICE_MANAGER_FAILED.getMessage(), containsString("Service"));
  }

  @Test
  public void fatalCode_errorJarNotFound_hasCode9() {
    assertThat(FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getCode(), is(9));
    assertThat(
        FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getMessage(), containsString("JAR"));
  }

  @Test
  public void fatalCode_errorNoMainClassInJar_hasCode10() {
    assertThat(FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.getCode(), is(10));
    assertThat(FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.getMessage(), containsString("Main"));
  }

  @Test
  public void fatalCode_errorFindingRndPort_hasCode11() {
    assertThat(FatalCode.ERROR_FINDING_RND_PORT.getCode(), is(11));
    assertThat(FatalCode.ERROR_FINDING_RND_PORT.getMessage(), containsString("port"));
  }

  @Test
  public void fatalCode_errorParsingZmqRpcPort_hasCode12() {
    assertThat(FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getCode(), is(12));
    assertThat(FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getMessage(), containsString("ZMQ"));
  }

  @Test
  public void fatalCode_errorParsingJsonRpcPort_hasCode13() {
    assertThat(FatalCode.ERROR_PARSING_JSON_RPC_PORT_NUMBER.getCode(), is(13));
    assertThat(FatalCode.ERROR_PARSING_JSON_RPC_PORT_NUMBER.getMessage(), containsString("JSON"));
  }

  @Test
  public void fatalCode_errorUnreachableEtcd_hasCode14() {
    assertThat(FatalCode.ERROR_UNREACHABLE_ETCD.getCode(), is(14));
    assertThat(FatalCode.ERROR_UNREACHABLE_ETCD.getMessage(), containsString("directory"));
  }

  @Test
  public void fatalCode_unexpectedErrorLaunchingMain_hasCode15() {
    assertThat(FatalCode.UNEXPECTED_ERROR_LAUNCHING_MAIN.getCode(), is(15));
    assertThat(FatalCode.UNEXPECTED_ERROR_LAUNCHING_MAIN.getMessage(), containsString("main"));
  }

  // ===== Tests for PeerException class =====

  @Test
  public void peerException_constructorWithFatalCode_setsFatalCodeAndMessage() {
    PeerException ex = new PeerException(FatalCode.ERROR_LOADING_PROPERTIES);

    assertThat(ex.getFatalCode(), is(FatalCode.ERROR_LOADING_PROPERTIES));
    assertThat(ex.getMessage(), is(FatalCode.ERROR_LOADING_PROPERTIES.getMessage()));
  }

  @Test
  public void peerException_eachFatalCode_createsValidException() {
    for (FatalCode code : FatalCode.values()) {
      PeerException ex = new PeerException(code);

      assertThat(ex.getFatalCode(), is(code));
      assertThat(ex.getMessage(), is(code.getMessage()));
    }
  }

  @Test
  public void peerException_isCheckedException() {
    // Verify PeerException extends Exception (checked) but not RuntimeException (unchecked)
    assertThat(Exception.class.isAssignableFrom(PeerException.class), is(true));
    assertThat(RuntimeException.class.isAssignableFrom(PeerException.class), is(false));
  }

  @Test
  public void peerException_canBeThrownAndCaught() {
    boolean caught = false;
    try {
      throw new PeerException(FatalCode.ERROR_SERVICE_MANAGER_FAILED);
    } catch (PeerException e) {
      caught = true;
      assertThat(e.getFatalCode(), is(FatalCode.ERROR_SERVICE_MANAGER_FAILED));
    }
    assertThat(caught, is(true));
  }
}
