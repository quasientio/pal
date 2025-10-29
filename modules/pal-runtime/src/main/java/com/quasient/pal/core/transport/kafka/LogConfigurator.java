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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.inject.Injector;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.transport.SourceLogReader;
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

  /**
   * Determines if a log specification refers to a Chronicle queue.
   *
   * @param logSpec the log specification (e.g., "file:/tmp/mylog", "file:mylog", or
   *     "my-kafka-topic")
   * @return true if it's a Chronicle queue (starts with "file:"), false otherwise
   */
  private static boolean isChronicleLog(String logSpec) {
    return logSpec != null && logSpec.startsWith("file:");
  }

  /**
   * Extracts the actual path/name from a log specification.
   *
   * @param logSpec the log specification
   * @return the path for Chronicle (without "file:" prefix, preserving leading slash) or the topic
   *     name for Kafka
   */
  private static String extractLogName(String logSpec) {
    if (isChronicleLog(logSpec)) {
      return logSpec.substring("file:".length());
    }
    return logSpec;
  }

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

  /** Flag indicating whether to perform Kafka health check during initialization. */
  private final boolean performHealthCheck;

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
   * @param performHealthCheck whether to perform Kafka connectivity health check during init()
   * @throws IllegalArgumentException if the required Kafka servers property
   *     ("kafka.bootstrap.servers") is missing
   */
  public LogConfigurator(
      String sourceLogName,
      Long sourceLogOffset,
      String writeAheadLogName,
      Properties appProps,
      Injector injector,
      boolean performHealthCheck) {
    this.sourceLogName = sourceLogName;
    this.sourceLogOffset = sourceLogOffset;
    this.writeAheadLogName = writeAheadLogName;
    this.appProps = appProps;
    this.injector = injector;
    this.performHealthCheck = performHealthCheck;
    final String givenPaldirUrl = appProps.getProperty("paldir_url");
    noPaldir = givenPaldirUrl == null || givenPaldirUrl.equals(PalDirectory.NO_URL);
    kafkaServers = appProps.getProperty("kafka.bootstrap.servers");
    // Note: kafkaServers may be null if only using Chronicle queues
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
   * Creates a LogInfo instance for a Chronicle queue without registering it in PalDirectory.
   *
   * <p>This is used when running without a directory (noPaldir=true).
   *
   * @param queuePath the path to the Chronicle queue
   * @return a new LogInfo instance configured for Chronicle queue
   */
  private LogInfo createChronicleLogInfo(String queuePath) {
    LogInfo logInfo = new LogInfo(queuePath);
    logInfo.setLogType(LogInfo.LogType.CHRONICLE);
    logInfo.setUuid(java.util.UUID.randomUUID());
    return logInfo;
  }

  /**
   * Retrieves or registers a Chronicle log with the specified path using the PalDirectory service.
   *
   * <p>If the Log entry already exists in the directory, its associated LogInfo is returned.
   * Otherwise, a new LogInfo is created and registered.
   *
   * @param queuePath the path to the Chronicle queue
   * @return the LogInfo corresponding to the provided Chronicle queue path
   * @throws Exception if the directory connection fails or Log registration encounters an error
   */
  private LogInfo getOrRegisterGivenChronicleLog(String queuePath) throws Exception {

    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    final LogInfo logInfo;

    // register given Chronicle log if not registered
    if (palDirectory.logExists(queuePath)) {
      logInfo = palDirectory.getLogInfo(queuePath);
    } else {
      logInfo = createChronicleLogInfo(queuePath);
      palDirectory.createLog(logInfo);
    }

    return logInfo;
  }

  /**
   * Initiates reading from the specified source Log starting at the provided offset.
   *
   * <p>Obtains a SourceLogReader instance via dependency injection and begins reading Log entries.
   *
   * @param sourceLog the LogInfo instance representing the source Log
   * @param sourceAndWalAreSameLog flag indicating whether the source and write-ahead logs are the
   *     same instance
   * @param initialOffset the starting offset for reading the Log
   * @throws Exception if an error occurs during the Log reading process
   */
  private void readFromLog(LogInfo sourceLog, boolean sourceAndWalAreSameLog, Long initialOffset)
      throws Exception {
    var logMessageReader = injector.getInstance(SourceLogReader.class);
    logMessageReader.readFromLog(
        sourceLog, sourceAndWalAreSameLog, initialOffset, sourceAndWalAreSameLog);
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
   * Performs a health check on the Kafka cluster by attempting to list topics with a timeout.
   *
   * <p>This method creates a temporary AdminClient to verify Kafka connectivity before initializing
   * log operations. If Kafka is not reachable within the configured timeout, an exception is
   * thrown.
   *
   * @throws Exception if Kafka is not reachable or the health check times out
   */
  private void performKafkaHealthCheck() throws Exception {
    int timeoutMs = Integer.parseInt(appProps.getProperty("kafka.connect.timeout.ms", "5000"));

    logger.info("Performing Kafka health check with timeout {}ms...", timeoutMs);

    // Fast TCP connectivity pre-check against the first bootstrap server
    try {
      String first =
          Iterables.get(Splitter.on(',').trimResults().omitEmptyStrings().split(kafkaServers), 0);
      int idx = first.lastIndexOf(':');
      if (idx > 0 && idx < first.length() - 1) {
        String host = first.substring(0, idx);
        int port = Integer.parseInt(first.substring(idx + 1));
        try (java.net.Socket s = new java.net.Socket()) {
          s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
        }
      }
    } catch (Exception e) {
      String msg =
          String.format(
              "Kafka health check failed: TCP connect to %s within %dms", kafkaServers, timeoutMs);
      logger.error(msg);
      throw new Exception(msg, e);
    }

    Properties adminProps = getKafkaAdminProperties(timeoutMs);

    try (org.apache.kafka.clients.admin.AdminClient adminClient =
        org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {

      // Attempt to list topics as a health check
      adminClient.listTopics().names().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

      logger.info("Kafka health check passed - cluster is reachable at {}", kafkaServers);

    } catch (java.util.concurrent.TimeoutException e) {
      String msg =
          String.format(
              "Kafka health check failed: timeout after %dms trying to connect to %s",
              timeoutMs, kafkaServers);
      logger.error(msg);
      throw new Exception(msg, e);
    } catch (Exception e) {
      String msg =
          String.format(
              "Kafka health check failed: unable to connect to %s - %s",
              kafkaServers, e.getMessage());
      logger.error(msg);
      throw new Exception(msg, e);
    }
  }

  /**
   * Initializes a set of properties to use by the Kafka admin client performing the health-check at
   * startup.
   *
   * @param timeoutMs value of timeout, in millis, to use in several properties
   * @return the created {@link Properties}
   */
  private Properties getKafkaAdminProperties(int timeoutMs) {
    Properties adminProps = new Properties();
    adminProps.put("bootstrap.servers", kafkaServers);
    adminProps.put("request.timeout.ms", String.valueOf(timeoutMs));
    adminProps.put("connections.max.idle.ms", String.valueOf(timeoutMs));
    // Ensure fast failure on unreachable brokers across Kafka client layers
    adminProps.put("default.api.timeout.ms", String.valueOf(timeoutMs));
    adminProps.put("socket.connection.setup.timeout.ms", String.valueOf(timeoutMs));
    adminProps.put("socket.connection.setup.timeout.max.ms", String.valueOf(timeoutMs));
    adminProps.put("retry.backoff.ms", "250");
    return adminProps;
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

    // Determine if we're using Kafka or Chronicle
    boolean usesKafka =
        (sourceLogName != null && !isChronicleLog(sourceLogName))
            || (writeAheadLogName != null && !isChronicleLog(writeAheadLogName));

    // Perform Kafka health check before initializing logs (if enabled and using Kafka)
    if (performHealthCheck && usesKafka && kafkaServers != null) {
      performKafkaHealthCheck();
    }

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
      if (isChronicleLog(sourceLogName)) {
        // Chronicle queue specification
        String queuePath = extractLogName(sourceLogName);
        sourceLog =
            noPaldir
                ? createChronicleLogInfo(queuePath)
                : getOrRegisterGivenChronicleLog(queuePath);
      } else {
        // Kafka topic
        sourceLog =
            noPaldir
                ? new LogInfo(sourceLogName, kafkaServers)
                : getOrRegisterGivenLog(sourceLogName);
      }
    }

    if ("auto".equalsIgnoreCase(writeAheadLogName)) {
      if (noPaldir) {
        writeAheadLog = new LogInfo(writeAheadLogName, kafkaServers);
      } else {
        writeAheadLog = newLog != null ? newLog : registerNewLog();
      }
    } else if (writeAheadLogName != null) {
      if (isChronicleLog(writeAheadLogName)) {
        // Chronicle queue specification
        String queuePath = extractLogName(writeAheadLogName);
        writeAheadLog =
            noPaldir
                ? createChronicleLogInfo(queuePath)
                : getOrRegisterGivenChronicleLog(queuePath);
      } else {
        // Kafka topic
        writeAheadLog =
            noPaldir
                ? new LogInfo(writeAheadLogName, kafkaServers)
                : getOrRegisterGivenLog(writeAheadLogName);
      }
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
