/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.kafka;

import com.google.inject.Injector;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures and manages Log I/O for the PAL runtime.
 *
 * <p>This class initializes source and write-ahead Logs based on specified log names, application
 * properties, and the availability of a directory service. Depending on the configuration, Log
 * entries may be automatically registered or retrieved from the Pal directory, and corresponding
 * reading and writing operations are initiated.
 */
public class LogConfigurator {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);

  /** Configured name for the source Log; may be set to "auto" for automatic registration. */
  private final String sourceLogName;

  /** Configured name for the WAL Log; may be set to "auto" for automatic registration. */
  private final String writeAheadLogName;

  /** Initial offset used when reading from the source Log. */
  private final Long sourceLogOffset;

  /** Application properties containing configuration not parameterized through CLI options. */
  private final Properties appProps;

  /** Flag indicating if a PalDirectory URL is provided; if not, Log names are used literally. */
  private final boolean noPaldir;

  /** Dependency injection container for obtaining required service instances. */
  private final Injector injector;

  /** Kafka server endpoints derived from the application properties. */
  private final String kafkaServers;

  /** LogInfo instance for the source Log after initialization; may be null if not configured. */
  private LogInfo sourceLog;

  /**
   * LogInfo instance for the Write-Ahead Log after initialization; may be null if not configured.
   */
  private LogInfo writeAheadLog;

  /**
   * Constructs a LogConfigurator with the given log parameters, application properties, and
   * dependency injector.
   *
   * @param sourceLogName the name of the source Log or "auto" to request automatic name
   *     registration
   * @param sourceLogOffset the starting offset for reading the source Log
   * @param writeAheadLogName the name of the writeAheadLog or "auto" to request automatic name
   *     registration
   * @param appProps the configuration properties including keys like kafka consumer/producer
   *     properties
   * @param injector the dependency injection container providing required service instances
   * @throws IllegalArgumentException if the required Kafka servers property
   *     ("kafka.bootstrap.servers") is missing
   */
  public LogConfigurator(
      String sourceLogName,
      Long sourceLogOffset,
      String writeAheadLogName,
      Properties appProps,
      Injector injector) {
    this.sourceLogName = sourceLogName;
    this.sourceLogOffset = sourceLogOffset;
    this.writeAheadLogName = writeAheadLogName;
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
    return palDirectory.createAutoLog(appProps.getProperty("logPrefix"), kafkaServers);
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
      palDirectory.createLog(logInfo);
    }

    return logInfo;
  }

  /**
   * Initiates reading from the specified source Log starting at the provided offset.
   *
   * <p>Obtains a LogReader instance via dependency injection and begins reading Log entries.
   *
   * @param sourceLog the LogInfo instance representing the source Log
   * @param sourceAndWalAreSameLog flag indicating whether the source and write-ahead logs are the
   *     same instance
   * @param initialOffset the starting offset for reading the Log
   * @throws Exception if an error occurs during the Log reading process
   */
  private void readFromLog(LogInfo sourceLog, boolean sourceAndWalAreSameLog, Long initialOffset)
      throws Exception {
    LogReader logMessageReader = injector.getInstance(LogReader.class);
    logMessageReader.readFromLog(sourceLog, sourceAndWalAreSameLog, initialOffset);
  }

  /**
   * Initiates writing to the specified Write-Ahead Log.
   *
   * <p>Acquires a WalWriter instance via dependency injection and sets up the Log writing process.
   *
   * @param writeAheadLog the LogInfo instance representing the write-ahead log
   */
  private void writeToLog(LogInfo writeAheadLog) {
    WalWriter logMessageWriter = injector.getInstance(WalWriter.class);
    logMessageWriter.writeToLog(writeAheadLog, true);
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
  public void init() throws Exception {

    // register Log(s)
    LogInfo newLog = null;

    if ("auto".equalsIgnoreCase(sourceLogName)) {
      if (noPaldir) {
        sourceLog = new LogInfo(sourceLogName, kafkaServers);
      } else {
        sourceLog = registerNewLog();
        newLog = sourceLog;
      }
    } else if (sourceLogName != null) {
      sourceLog =
          noPaldir
              ? new LogInfo(sourceLogName, kafkaServers)
              : getOrRegisterGivenLog(sourceLogName);
    }

    if ("auto".equalsIgnoreCase(writeAheadLogName)) {
      if (noPaldir) {
        writeAheadLog = new LogInfo(writeAheadLogName, kafkaServers);
      } else {
        writeAheadLog = newLog != null ? newLog : registerNewLog();
      }
    } else if (writeAheadLogName != null) {
      writeAheadLog =
          noPaldir
              ? new LogInfo(writeAheadLogName, kafkaServers)
              : getOrRegisterGivenLog(writeAheadLogName);
    }

    // init Log reader
    if (sourceLog != null) {
      readFromLog(sourceLog, Objects.equals(sourceLog, writeAheadLog), sourceLogOffset);
    }

    // init WAL writer
    if (writeAheadLog != null) {
      writeToLog(writeAheadLog);
    }
  }

  /**
   * Retrieves the configured source Log.
   *
   * @return an Optional containing the LogInfo for the source Log if configured, otherwise an empty
   *     Optional
   */
  public Optional<LogInfo> getSourceLog() {
    return Optional.ofNullable(sourceLog);
  }

  /**
   * Retrieves the configured Write-Ahead Log.
   *
   * @return an Optional containing the LogInfo for the write-ahead Log if configured, otherwise an
   *     empty Optional
   */
  public Optional<LogInfo> getWriteAheadLog() {
    return Optional.ofNullable(writeAheadLog);
  }
}
