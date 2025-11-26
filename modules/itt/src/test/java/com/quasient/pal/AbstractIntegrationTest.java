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
import com.quasient.pal.messages.Marshallable;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * This class provides infrastructure for loading environment variables and configuration properties
 * for PalDirectory and Kafka. It also provides utility methods for launching required peers with
 * different flags, ports and logging configurations.
 */
public abstract class AbstractIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

  private static String PAL_DIRECTORY_URL;
  private static final IdGenerator idGenerator = new Base62UuidGenerator();

  protected static final int PROCESS_TIMEOUT_SECONDS =
      15; // Increased to allow for Kafka health check timeout

  /** Timeout in seconds to wait for a launched peer to become ready. */
  private static final int PEER_READY_TIMEOUT_SECONDS = 10;

  /** Peer ready line - expected in peer log at level INFO when */
  private static final String READY_LINE_TMPL = "Peer %s up and running";

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
   * Gets the JSON-RPC WebSocket address for a peer from the directory.
   *
   * <p>Looks up the peer by UUID in the PAL directory and returns its JSON-RPC address (format:
   * "ws://host:port").
   *
   * @param peerUuid the UUID of the peer to look up
   * @return the JSON-RPC WebSocket address (e.g., "ws://localhost:9001"), or null if peer not found
   *     or has no JSON-RPC address
   * @throws Exception if directory access fails
   */
  protected static String getPeerJsonRpcAddress(UUID peerUuid) throws Exception {
    PalDirectory palDirectory = new PalDirectory(getPalDirectoryUrl(), null, true);
    PeerInfo peerInfo = palDirectory.getPeer(peerUuid);
    palDirectory.close();
    if (peerInfo == null) {
      logger.warn("Peer with UUID {} not found in directory", peerUuid);
      return null;
    }
    return peerInfo.getJsonrpcAddress();
  }

  /**
   * Executes `pal run` with the given arguments, waits for it to finish and returns the process
   * result.
   *
   * @param args the command-line arguments to pass to `pal run`
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPeer(String... args) throws IOException, InterruptedException {
    return runPeerWithEnv(null, args);
  }

  /**
   * Executes `pal run` with the given arguments, while adding/removing environment variables, waits
   * for it to finish and returns the process result.
   *
   * @param palDirectory the PAL_DIRECTORY value to set, or null to remove it from Environment
   * @param args the command-line arguments to pass to `pal run`
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPeerWithEnv(String palDirectory, String... args)
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
    pb.directory(new java.io.File(palHome));
    pb.environment().put("PAL_HOME", palHome);

    // Configure logging
    pb.environment()
        .put(
            "PAL_PEER_LOGGING_CONFIG", Paths.get(palHome, "config", "peer-logging.xml").toString());

    // Set or remove PAL_DIRECTORY based on parameter
    if (palDirectory != null) {
      pb.environment().put("PAL_DIRECTORY", palDirectory);
    } else {
      pb.environment().remove("PAL_DIRECTORY");
    }

    // Remove other environment variables that would interfere with tests
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("CHRONICLE_BASE_DIR");
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
      String xmlContent = Files.readString(logbackConfigPath);

      // Find the first appender with FileAppender class
      int appenderStart = 0;
      while ((appenderStart = xmlContent.indexOf("<appender", appenderStart)) != -1) {
        int appenderEnd = xmlContent.indexOf("</appender>", appenderStart);
        if (appenderEnd == -1) break;

        String appenderBlock = xmlContent.substring(appenderStart, appenderEnd);

        // Check if this appender is a FileAppender or PeerFileAppender
        if (appenderBlock.contains("FileAppender") || appenderBlock.contains("PeerFileAppender")) {
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
   * Launches a new peer in the background and waits for it to be ready.
   *
   * <p>This method starts a peer process in the background, polls the peer log file for the
   * "Managed services ready" line (indicating the peer is ready to accept requests), and returns
   * the Process handle.
   *
   * <p>The peer log is configured to write to {@code logs/peer.log} relative to PAL_HOME.
   *
   * <p><b>Note:</b> This method removes PAL_DIRECTORY and KAFKA_SERVERS from the environment to
   * ensure tests are explicit. Tests should pass configuration via command-line arguments (e.g.,
   * {@code "-d", palDirectory}).
   *
   * <p>The caller is responsible for stopping the peer via {@link Process#destroy()} or {@link
   * Process#destroyForcibly()}.
   *
   * @param peerId the UUID to assign to the peer
   * @param args command-line arguments to pass to {@code pal run}
   * @return Process handle for the running peer
   * @throws IOException if process execution fails
   * @throws InterruptedException if interrupted while waiting for peer to be ready
   * @throws IllegalStateException if peer does not become ready within the timeout period
   */
  protected Process launchPeer(UUID peerId, String... args)
      throws IOException, InterruptedException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal.sh").toString());
    command.add("run");

    // Set the peer Id so we can identify it in the logs
    command.add("--uuid");
    command.add(peerId.toString());

    // Add increased Kafka timeout for slow/loaded test environments
    command.add("--kafka-timeout");
    command.add("20000");

    // Add given args
    command.addAll(Arrays.asList(args));

    logger.info("Launching new peer: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new java.io.File(palHome));
    pb.environment().put("PAL_HOME", palHome);

    // Extract peer name from args (value after -n flag)
    String peerName = null;
    for (int i = 0; i < args.length - 1; i++) {
      if ("-n".equals(args[i])) {
        peerName = args[i + 1];
        break;
      }
    }

    // Configure logging - CRITICAL for debugging test failures
    // For interceptable and interceptor peers, create separate logging configs for easier debugging
    String loggingConfigPath;
    if ("interceptable-peer".equals(peerName) || "interceptor-peer".equals(peerName)) {
      loggingConfigPath = createPeerSpecificLoggingConfig(peerName, palHome);
    } else {
      loggingConfigPath = Paths.get(palHome, "config", "peer-logging.xml").toString();
    }

    pb.environment().put("PAL_PEER_LOGGING_CONFIG", loggingConfigPath);

    // Remove environment variables that would interfere with tests
    // Tests must pass configuration explicitly via command-line arguments (e.g., "-d", "-k")
    pb.environment().remove("PAL_DIRECTORY");
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("CHRONICLE_BASE_DIR");
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
                logger.warn("Error reading stdout from launched peer", e);
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
                logger.warn("Error reading stderr from launched peer", e);
              }
            });

    stdoutReader.start();
    stderrReader.start();

    // Parse the logback config to find the actual log file path
    Path loggingConfigFilePath = Paths.get(loggingConfigPath);
    Path logPath;
    try {
      logPath = parseLogbackLogFilePath(loggingConfigFilePath);
      logger.info("Parsed log file path from {}: {}", loggingConfigFilePath, logPath);
    } catch (IOException e) {
      // Fall back to default if parsing fails
      logger.warn("Failed to parse log file path from {}, using default", loggingConfigFilePath, e);
      logPath = Paths.get(palHome, "logs", "peer.log");
    }

    // Wait for peer to be ready by polling log file
    boolean ready = waitForPeerReady(logPath, peerId, PEER_READY_TIMEOUT_SECONDS);

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
        logger.error("Launched peer stdout:\n{}", capturedStdout);
      }
      if (!capturedStderr.isEmpty()) {
        logger.error("Launched peer stderr:\n{}", capturedStderr);
      }

      throw new IllegalStateException(
          String.format(
              "Peer did not become ready within %d seconds. Check logs above for peer output.",
              PEER_READY_TIMEOUT_SECONDS));
    }

    logger.info("Peer is ready");
    return process;
  }

  /**
   * Waits for peer to be ready by polling log file for the "Managed services ready" line.
   *
   * @param logPath path to peer log file
   * @param peerId ID of peer, needed to identify READY line among similar lines of older peers
   * @param timeoutSeconds timeout in seconds
   * @return true if ready line found, false if timeout exceeded
   */
  private boolean waitForPeerReady(Path logPath, UUID peerId, int timeoutSeconds) {
    long startTime = System.currentTimeMillis();
    long timeoutMillis = timeoutSeconds * 1000L;

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      if (Files.exists(logPath)) {
        try {
          List<String> lines = Files.readAllLines(logPath, UTF_8);
          for (String line : lines) {
            if (line.contains(READY_LINE_TMPL.formatted(peerId))) {
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

  /**
   * Waits for a peer process to complete naturally.
   *
   * <p>This method blocks until the peer process exits on its own or the timeout is reached. If the
   * timeout is reached, the process is forcibly terminated.
   *
   * <p>Use this method when you need to wait for the peer to finish its work before making
   * assertions (e.g., checking logs after peer has completed).
   *
   * @param process the peer process to wait for
   * @param timeoutSeconds maximum time to wait for the process to complete, in seconds
   * @return the exit code of the process
   * @throws InterruptedException if interrupted while waiting for process termination
   * @throws IllegalStateException if the process does not complete within the timeout
   */
  protected int joinPeer(Process process, int timeoutSeconds) throws InterruptedException {
    if (process == null || !process.isAlive()) {
      return process != null ? process.exitValue() : 0;
    }

    logger.info("Waiting for peer process to complete (timeout: {} seconds)", timeoutSeconds);
    boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

    if (!exited) {
      logger.warn("Peer did not complete within {} seconds, force killing", timeoutSeconds);
      process.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
      throw new IllegalStateException(
          String.format("Peer process did not complete within %d seconds", timeoutSeconds));
    }

    int exitCode = process.exitValue();
    logger.info("Peer process completed with exit code: {}", exitCode);
    return exitCode;
  }

  /**
   * Stops a running peer gracefully.
   *
   * <p>Destroys the process and waits up to 5 seconds for it to terminate. If still alive, force
   * kills it.
   *
   * @param process the peer process to stop
   * @throws InterruptedException if interrupted while waiting for process termination
   */
  protected void stopPeer(Process process) throws InterruptedException {
    if (process == null || !process.isAlive()) {
      logger.info("Process is null or not alive, nothing to stop");
      return;
    }

    logger.info("Stopping peer process, calling destroy()");
    process.destroy();
    logger.info("destroy() called, now waiting for exit (5s timeout)");
    boolean exited = process.waitFor(5, TimeUnit.SECONDS);
    logger.info("waitFor returned, exited={}", exited);

    if (!exited) {
      logger.warn("Peer did not exit gracefully, force killing");
      process.destroyForcibly();
      logger.info("destroyForcibly() called, waiting for exit (2s timeout)");
      process.waitFor(2, TimeUnit.SECONDS);
      logger.info("Second waitFor returned after destroyForcibly");
    }

    logger.info("Peer stopped");
  }

  /**
   * Creates a peer-specific logging configuration file for intercept test debugging.
   *
   * <p>This method copies the peer-logging.xml.example template and modifies it to:
   *
   * <ul>
   *   <li>Change the log file path from logs/peer.log to logs/{peerName}.log
   *   <li>Add a DEBUG-level logger for com.quasient.pal.core package
   * </ul>
   *
   * <p>This enables separate log files for interceptable-peer and interceptor-peer, making it
   * easier to debug intercept callback flows without logs from both peers mixed together.
   *
   * @param peerName the name of the peer (e.g., "interceptable-peer", "interceptor-peer")
   * @param palHome the PAL_HOME directory path
   * @return the absolute path to the created logging configuration file
   * @throws IOException if the config file cannot be read or written
   */
  private String createPeerSpecificLoggingConfig(String peerName, String palHome)
      throws IOException {
    Path sourceConfig = Paths.get(palHome, "config", "peer-logging.xml.example");
    Path targetConfig = Paths.get(palHome, "config", peerName + "-logging.xml");

    // Copy and modify the config
    List<String> lines = Files.readAllLines(sourceConfig);
    List<String> modifiedLines = new ArrayList<>();

    for (String line : lines) {
      // Replace log file path to use peer-specific log file
      if (line.contains("logs/peer.log")) {
        modifiedLines.add(line.replace("logs/peer.log", "logs/" + peerName + ".log"));
      } else if (line.contains("<logger name=\"com.quasient.pal\" level=\"info\"")) {
        // Add DEBUG logger for com.quasient.pal.core before the general logger
        modifiedLines.add(
            "    <logger name=\"com.quasient.pal.core\" level=\"DEBUG\" additivity=\"false\">");
        modifiedLines.add("        <appender-ref ref=\"peer\"/>");
        modifiedLines.add("    </logger>");
        modifiedLines.add("");
        modifiedLines.add(line);
      } else {
        modifiedLines.add(line);
      }
    }

    Files.write(targetConfig, modifiedLines);
    String configPath = targetConfig.toString();
    logger.info("Created peer-specific logging config for {}: {}", peerName, configPath);
    return configPath;
  }

  /** Helper method to pretty-print a Marshallable (i.e. colfer) message */
  protected String colferToPrettyJson(Marshallable message) {
    return ColferUtils.toJson(message, true);
  }

  /** Container for process execution results. */
  protected record ProcessResult(int exitCode, String stdout, String stderr) {}
}
