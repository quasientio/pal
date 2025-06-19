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

package com.quasient.pal.core;

import com.google.inject.Injector;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures and manages Log I/O for the PAL runtime.
 *
 * <p>This class initializes input and output Logs based on specified log names, application
 * properties, and the availability of a directory service. Depending on the configuration, Log
 * entries may be automatically registered or retrieved from the Pal directory, and corresponding
 * reading and writing operations are initiated.
 */
class LogConfigurator {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);

  /** Configured name for the input Log; may be set to "auto" for automatic registration. */
  private final String inLogName;

  /** Configured name for the output Log; may be set to "auto" for automatic registration. */
  private final String outLogName;

  /** Initial offset used when reading from the input Log. */
  private final Long inLogOffset;

  /**
   * Application properties containing configuration such as "paldir_url",
   * "kafka.bootstrap.servers", and "kafkaTopicPrefix".
   */
  private final Properties appProps;

  /** Flag indicating if a PalDirectory URL is provided; if not, Log names are used literally. */
  private final boolean noPaldir;

  /** Dependency injection container for obtaining required service instances. */
  private final Injector injector;

  /** Kafka server endpoints derived from the application properties. */
  private final String kafkaServers;

  /** LogInfo instance for the input Log after initialization; may be null if not configured. */
  private LogInfo inLog;

  /** LogInfo instance for the output Log after initialization; may be null if not configured. */
  private LogInfo outLog;

  /**
   * Constructs a LogConfigurator with the given log parameters, application properties, and
   * dependency injector.
   *
   * @param inLogName the name of the input Log or "auto" to request automatic name registration
   * @param inLogOffset the starting offset for reading the input Log
   * @param outLogName the name of the output Log or "auto" to request automatic name registration
   * @param appProps the configuration properties including keys like "paldir_url",
   *     "kafka.bootstrap.servers", and "kafkaTopicPrefix"
   * @param injector the dependency injection container providing required service instances
   * @throws IllegalArgumentException if the required Kafka servers property
   *     ("kafka.bootstrap.servers") is missing
   */
  LogConfigurator(
      String inLogName,
      Long inLogOffset,
      String outLogName,
      Properties appProps,
      Injector injector) {
    this.inLogName = inLogName;
    this.inLogOffset = inLogOffset;
    this.outLogName = outLogName;
    this.appProps = appProps;
    this.injector = injector;
    final String givenPaldirUrl = appProps.getProperty("paldir_url");
    noPaldir = givenPaldirUrl == null || givenPaldirUrl.equals(PalDirectory.NO_URL);
    kafkaServers = appProps.getProperty("kafka.bootstrap.servers");
    if (kafkaServers == null) {
      throw new IllegalArgumentException("No kafka servers given.");
    }
  }

  /**
   * Registers a new Log entry in the PalDirectory service.
   *
   * <p>Retrieves a PalDirectory instance via dependency injection and registers a new Log entry
   * based on the Kafka topic prefix and Kafka servers provided in the configuration.
   *
   * @return the newly created LogInfo instance
   * @throws Exception if the directory connection or Log registration fails
   */
  private LogInfo registerNewLog() throws Exception {
    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    return palDirectory.newLog(appProps.getProperty("kafkaTopicPrefix"), kafkaServers);
  }

  /**
   * Retrieves or registers a Log with the specified name using the PalDirectory service.
   *
   * <p>If the Log entry already exists, its associated LogInfo is returned. Otherwise, a new
   * LogInfo is created and registered in the directory.
   *
   * @param logName the name of the Log to retrieve or register
   * @return the LogInfo corresponding to the provided Log name
   * @throws Exception if the directory connection fails or Log registration encounters an error
   */
  private LogInfo getOrRegisterGivenLog(String logName) throws Exception {

    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    final LogInfo logInfo;

    // register given log if not registered
    if (palDirectory.logExists(logName)) {
      logInfo = palDirectory.getLogInfo(logName);
    } else {
      logInfo = new LogInfo(logName, kafkaServers);
      palDirectory.registerLog(logInfo);
    }

    return logInfo;
  }

  /**
   * Initiates reading from the specified input Log starting at the provided offset.
   *
   * <p>Obtains a LogReader instance via dependency injection and begins reading Log entries.
   *
   * @param inLog the LogInfo instance representing the input Log
   * @param inAndOutAreSameLog flag indicating whether the input and output Logs are the same
   *     instance
   * @param initialOffset the starting offset for reading the Log
   * @throws Exception if an error occurs during the Log reading process
   */
  private void readFromLog(LogInfo inLog, boolean inAndOutAreSameLog, Long initialOffset)
      throws Exception {
    LogReader logMessageReader = injector.getInstance(LogReader.class);
    logMessageReader.readFromLog(inLog, inAndOutAreSameLog, initialOffset);
  }

  /**
   * Initiates writing to the specified output Log.
   *
   * <p>Acquires a LogWriter instance via dependency injection and sets up the Log writing process.
   *
   * @param outLog the LogInfo instance representing the output log
   */
  private void writeToLog(LogInfo outLog) {
    LogWriter logMessageWriter = injector.getInstance(LogWriter.class);
    logMessageWriter.writeToLog(outLog, true);
  }

  /**
   * Initializes the Log configuration for input and output operations.
   *
   * <p>Based on the provided Log names and the presence (or absence) of a PalDirectory URL, this
   * method either registers new Log entries or retrieves existing ones. For an "auto" setting, it
   * may create a single Log to serve both input and output roles.
   *
   * @throws Exception if Log retrieval, registration, or read/write operations fail
   */
  void init() throws Exception {

    // register Log(s)
    LogInfo newLog = null;

    if ("auto".equalsIgnoreCase(inLogName)) {
      if (noPaldir) {
        inLog = new LogInfo(inLogName, kafkaServers);
      } else {
        inLog = registerNewLog();
        newLog = inLog;
      }
    } else if (inLogName != null) {
      inLog = noPaldir ? new LogInfo(inLogName, kafkaServers) : getOrRegisterGivenLog(inLogName);
    }

    if ("auto".equalsIgnoreCase(outLogName)) {
      if (noPaldir) {
        outLog = new LogInfo(outLogName, kafkaServers);
      } else {
        outLog = newLog != null ? newLog : registerNewLog();
      }
    } else if (outLogName != null) {
      outLog = noPaldir ? new LogInfo(outLogName, kafkaServers) : getOrRegisterGivenLog(outLogName);
    }

    // init Log reader
    if (inLog != null) {
      readFromLog(inLog, Objects.equals(inLog, outLog), inLogOffset);
    }

    // init Log writer
    if (outLog != null) {
      writeToLog(outLog);
    }
  }

  /**
   * Retrieves the configured input Log.
   *
   * @return an Optional containing the LogInfo for the input Log if configured, otherwise an empty
   *     Optional
   */
  Optional<LogInfo> getInLog() {
    return Optional.ofNullable(inLog);
  }

  /**
   * Retrieves the configured output Log.
   *
   * @return an Optional containing the LogInfo for the output Log if configured, otherwise an empty
   *     Optional
   */
  Optional<LogInfo> getOutLog() {
    return Optional.ofNullable(outLog);
  }
}
