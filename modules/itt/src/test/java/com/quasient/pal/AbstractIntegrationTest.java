/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.util.Base62UuidGenerator;
import com.quasient.pal.common.util.IdGenerator;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.types.RpcType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * This class provides infrastructure for loading environment variables and configuration properties
 * for PalDirectory and Kafka. It also provides utility methods for launching transient peers that
 * don't conflict with peer4itts.sh by using different ports and logging configurations.
 */
public abstract class AbstractIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

  private static String PAL_DIRECTORY_URL;
  private static final IdGenerator idGenerator = new Base62UuidGenerator();

  protected static final int PROCESS_TIMEOUT_SECONDS =
      15; // Increased to allow for Kafka health check timeout

  /** Timeout in seconds to wait for a transient peer to become ready. */
  private static final int PEER_READY_TIMEOUT_SECONDS = 10;

  protected static Properties getKafkaConsumerProperties() throws IOException {
    var properties = new Properties();
    try (final InputStream stream =
        AbstractIntegrationTest.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    return properties;
  }

  protected static Properties getKafkaProducerProperties() throws IOException {
    var properties = new Properties();
    try (final InputStream stream =
        AbstractIntegrationTest.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    return properties;
  }

  protected static String getPalDirectoryUrl() {
    if (PAL_DIRECTORY_URL == null) {
      final String palDirectoryUrl = System.getenv("PAL_DIRECTORY");
      if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
        throw new RuntimeException(
            "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2379)");
      }
      PAL_DIRECTORY_URL = palDirectoryUrl;
    }
    return PAL_DIRECTORY_URL;
  }

  protected static String getKafkaServers() {
    final String kafkaServers = System.getenv("KAFKA_SERVERS");
    if (kafkaServers == null || kafkaServers.isEmpty()) {
      throw new RuntimeException(
          "Please set the environment variable KAFKA_SERVERS (eg. KAFKA_SERVERS=localhost:9092)");
    }
    return kafkaServers;
  }

  protected static String getKafkaServersOrDefault(String defaultServers) {
    return System.getenv().getOrDefault("KAFKA_SERVERS", defaultServers);
  }

  protected static Optional<PeerInfo> findRpcPeer(
      RpcType rpcType, DirectoryConnectionProvider directoryConnectionProvider)
      throws ExecutionException, InterruptedException {
    Predicate<PeerInfo> hasRpcType =
        peerInfo -> {
          if (rpcType == RpcType.ZMQ_RPC) {
            return peerInfo.getZmqRpcAddress() != null;
          } else {
            return peerInfo.getJsonrpcAddress() != null;
          }
        };
    PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    return palDirectory.listPeers().stream().filter(hasRpcType).findFirst();
  }

  protected static ZContext createZmqContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  protected static String generateId() {
    return idGenerator.nextId();
  }

  /**
   * Runs a pal command with the given arguments and returns the process result. Uses different
   * ports than peer4itts.sh to avoid conflicts.
   *
   * @param args the command-line arguments to pass to pal.sh run
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPalCommand(String... args) throws IOException, InterruptedException {
    return runPalCommandWithEnv(null, args);
  }

  /**
   * Runs a pal command with custom environment variables.
   *
   * @param palDirectory the PAL_DIRECTORY value to set, or null to remove it
   * @param args the command-line arguments to pass to pal.sh run
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPalCommandWithEnv(String palDirectory, String... args)
      throws IOException, InterruptedException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal.sh").toString());
    command.add("run");

    // Add the test arguments
    command.addAll(Arrays.asList(args));

    logger.info("Running command: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().put("PAL_HOME", palHome);

    // Configure logging
    pb.environment()
        .put(
            "PAL_PEER_LOGGING_CONFIG",
            Paths.get(palHome, "config", "transient-peer-logging.xml").toString());

    // Set or remove PAL_DIRECTORY based on parameter
    if (palDirectory != null) {
      pb.environment().put("PAL_DIRECTORY", palDirectory);
    } else {
      pb.environment().remove("PAL_DIRECTORY");
    }

    // Remove other environment variables that would interfere with tests
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    Process process = pb.start();

    // Capture stdout and stderr
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();

    Thread stdoutReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stdout.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stdout", e);
              }
            });

    Thread stderrReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stderr.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stderr", e);
              }
            });

    stdoutReader.start();
    stderrReader.start();

    boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("Process timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
    }

    stdoutReader.join(1000);
    stderrReader.join(1000);

    int exitCode = process.exitValue();
    String stdoutStr = stdout.toString();
    String stderrStr = stderr.toString();

    logger.info("Process exited with code: {}", exitCode);
    if (!stdoutStr.isEmpty()) {
      logger.debug("Process stdout: {}", stdoutStr);
    }
    if (!stderrStr.isEmpty()) {
      logger.debug("Process stderr: {}", stderrStr);
    }

    return new ProcessResult(exitCode, stdoutStr, stderrStr);
  }

  /**
   * Parses a logback XML configuration file to extract the log file path from the first
   * FileAppender.
   *
   * <p>This method uses a simple XML parsing approach to find the {@code <file>} element within the
   * first {@code <appender>} that has a {@code class} attribute containing "FileAppender". The path
   * is resolved relative to PAL_HOME if it's not absolute.
   *
   * @param logbackConfigPath path to the logback XML configuration file
   * @return absolute path to the log file
   * @throws IOException if the config file cannot be read or parsed
   */
  private static Path parseLogbackLogFilePath(Path logbackConfigPath) throws IOException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    try {
      // Read the entire XML file
      String xmlContent = java.nio.file.Files.readString(logbackConfigPath);

      // Find the first appender with FileAppender class
      int appenderStart = 0;
      while ((appenderStart = xmlContent.indexOf("<appender", appenderStart)) != -1) {
        int appenderEnd = xmlContent.indexOf("</appender>", appenderStart);
        if (appenderEnd == -1) break;

        String appenderBlock = xmlContent.substring(appenderStart, appenderEnd);

        // Check if this appender is a FileAppender
        if (appenderBlock.contains("FileAppender")) {
          // Extract the <file> tag content
          int fileStart = appenderBlock.indexOf("<file>");
          int fileEnd = appenderBlock.indexOf("</file>");

          if (fileStart != -1 && fileEnd != -1) {
            String logFile = appenderBlock.substring(fileStart + 6, fileEnd).trim();

            // Resolve the path relative to PAL_HOME if not absolute
            Path logPath = Paths.get(logFile);
            if (!logPath.isAbsolute()) {
              logPath = Paths.get(palHome, logFile);
            }

            return logPath;
          }
        }

        appenderStart = appenderEnd;
      }

      throw new IOException("No FileAppender with <file> element found in " + logbackConfigPath);

    } catch (IOException e) {
      throw new IOException("Failed to parse logback config: " + logbackConfigPath, e);
    }
  }

  /**
   * Launches a transient peer in the background and waits for it to be ready.
   *
   * <p>This method starts a peer process in the background, polls the peer log file for the
   * "Managed services ready" line (indicating the peer is ready to accept requests), and returns
   * the Process handle.
   *
   * <p>The peer log is configured to write to {@code logs/peer.log} relative to PAL_HOME.
   *
   * <p>The caller is responsible for stopping the peer via {@link Process#destroy()} or {@link
   * Process#destroyForcibly()}.
   *
   * @param args command-line arguments to pass to {@code pal run}
   * @return Process handle for the running peer
   * @throws IOException if process execution fails
   * @throws InterruptedException if interrupted while waiting for peer to be ready
   * @throws IllegalStateException if peer does not become ready within the timeout period
   */
  protected Process launchTransientPeer(String... args) throws IOException, InterruptedException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal.sh").toString());
    command.add("run");
    // Add increased Kafka timeout for slow/loaded test environments
    command.add("--kafka-timeout");
    command.add("20000");
    command.addAll(Arrays.asList(args));

    logger.info("Launching transient peer: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new java.io.File(palHome));
    pb.environment().put("PAL_HOME", palHome);

    // Configure logging - CRITICAL for debugging test failures
    pb.environment()
        .put(
            "PAL_PEER_LOGGING_CONFIG",
            Paths.get(palHome, "config", "transient-peer-logging.xml").toString());

    // Remove environment variables that would interfere with tests
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    // Don't redirect output - we need to capture it for debugging
    Process process = pb.start();

    // Start capturing stdout/stderr in background threads
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();

    Thread stdoutReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stdout.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stdout from transient peer", e);
              }
            });

    Thread stderrReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stderr.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stderr from transient peer", e);
              }
            });

    stdoutReader.start();
    stderrReader.start();

    // Wait for peer to be ready by polling log file
    // Parse the logback config to find the actual log file path
    Path loggingConfigPath = Paths.get(palHome, "config", "transient-peer-logging.xml");
    Path logPath;
    try {
      logPath = parseLogbackLogFilePath(loggingConfigPath);
      logger.info("Parsed log file path from {}: {}", loggingConfigPath, logPath);
    } catch (IOException e) {
      // Fall back to default if parsing fails
      logger.warn("Failed to parse log file path from {}, using default", loggingConfigPath, e);
      logPath = Paths.get(palHome, "logs", "peer.log");
    }

    boolean ready = waitForPeerReady(logPath, PEER_READY_TIMEOUT_SECONDS);

    if (!ready) {
      // Capture any available output before killing the process
      process.destroy();
      boolean exited = process.waitFor(5, TimeUnit.SECONDS);
      if (!exited) {
        process.destroyForcibly();
      }

      // Wait for output capture threads to finish (with timeout)
      try {
        stdoutReader.join(2000);
        stderrReader.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // Log captured output for debugging
      String capturedStdout = stdout.toString();
      String capturedStderr = stderr.toString();

      if (!capturedStdout.isEmpty()) {
        logger.error("Transient peer stdout:\n{}", capturedStdout);
      }
      if (!capturedStderr.isEmpty()) {
        logger.error("Transient peer stderr:\n{}", capturedStderr);
      }

      throw new IllegalStateException(
          String.format(
              "Peer did not become ready within %d seconds. Check logs above for peer output.",
              PEER_READY_TIMEOUT_SECONDS));
    }

    logger.info("Transient peer is ready");
    return process;
  }

  /**
   * Waits for peer to be ready by polling log file for the "Managed services ready" line.
   *
   * @param logPath path to peer log file
   * @param timeoutSeconds timeout in seconds
   * @return true if ready line found, false if timeout exceeded
   */
  private boolean waitForPeerReady(Path logPath, int timeoutSeconds) {
    long startTime = System.currentTimeMillis();
    long timeoutMillis = timeoutSeconds * 1000L;
    String readyLine = "Managed services ready";

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      if (java.nio.file.Files.exists(logPath)) {
        try {
          List<String> lines = java.nio.file.Files.readAllLines(logPath, UTF_8);
          for (String line : lines) {
            if (line.contains(readyLine)) {
              return true;
            }
          }
        } catch (IOException e) {
          logger.warn("Error reading log file: {}", logPath, e);
        }
      }

      // Sleep briefly before checking again
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    return false;
  }

  /** Container for process execution results. */
  protected record ProcessResult(int exitCode, String stdout, String stderr) {}
}
