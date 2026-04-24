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

import static java.lang.String.format;
import static picocli.CommandLine.Option;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.util.Strings;
import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.dispatcher.LogRpcExecutor;
import io.quasient.pal.core.dispatcher.SocketRpcExecutor;
import io.quasient.pal.core.dispatcher.thread.ThreadPool;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.execution.java.DynamicResourceBundleControlProvider;
import io.quasient.pal.core.intercept.InterceptInformer;
import io.quasient.pal.core.intercept.InterceptMatcher;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.replay.DivergenceReport;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayInputInjector;
import io.quasient.pal.core.rpc.policy.RpcPolicyFileWatcher;
import io.quasient.pal.core.runtime.session.SessionService;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.core.transport.kafka.LogConfigurator;
import io.quasient.pal.core.transport.websocket.JsonRpcRequestServer;
import io.quasient.pal.core.transport.zmq.ZmqRpcServer;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.EtcdUnavailableException;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.cxn.directory.PeerLease;
import io.quasient.pal.messages.OutboundMsg;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Entry point for a Pal peer execution (i.e. pal run).
 *
 * <p>This class orchestrates initialization of logging, configuration properties, ZeroMQ context,
 * custom classloading, dependency injection, and managed services. It processes command-line
 * options to launch a main class, execute a JAR file, or run as a persistent service.
 */
@Command(
    name = "peer",
    description = "Run a new peer",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    usageHelpWidth = 90,
    customSynopsis = {
      "pal run [OPTIONS] class [args...]",
      "            (to execute a class)",
      "    or pal run [OPTIONS] -jar jarFile [args...]",
      "            (to execute a jar file)",
      "    or pal run [OPTIONS]",
      "            (to run as service - ie. no entry point)%n",
    })
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "SIC_INNER_SHOULD_BE_STATIC_ANON", "URF_UNREAD_FIELD"},
    justification =
        "Main class with complex initialization; anonymous inner classes for service lifecycle")
public class Main implements Callable<Integer> {

  /** Exit code indicating successful peer execution. */
  public static final int EXIT_SUCCESS = 0;

  /**
   * Exit code when the specified main class cannot be found on the classpath.
   *
   * <p>Returned when the class given as a positional argument (or via {@code -jar} manifest) cannot
   * be resolved by the custom classloader.
   */
  public static final int EXIT_CLASS_NOT_FOUND = 1;

  /**
   * Exit code when replay detects divergences between live execution and the WAL oracle.
   *
   * <p>Only returned when the peer is launched with {@code --replay-wal} and the divergence report
   * is non-empty. If the application itself exits non-zero, that exit code takes precedence.
   */
  public static final int EXIT_REPLAY_DIVERGENCES = 2;

  /** Reference to the parent PAL command instance providing common command-line options. */
  @SuppressWarnings("unused")
  @ParentCommand
  private PalCommand palCommand;

  /** injected by picocli, allows us to access cmd-line as typed */
  @Spec CommandSpec spec;

  /**
   * Classpath configuration for the peer. Specifies folders or JAR files to load classes from.
   * Corresponds to the CLI options -c, -cp, or --classpath and the CLASSPATH environment variable.
   */
  @Option(
      names = {"-c", "-cp", "--classpath"},
      order = 1,
      paramLabel = "CLASSPATH", // corresponding ENV var: CLASSPATH
      description = "load classes from given folders/jars")
  private String classpath;

  /**
   * URL for the Pal directory used to register the peer. Resolved from the inherited {@code -d}
   * option (defined on the root {@code pal} command), the {@code PAL_DIRECTORY} environment
   * variable, or left {@code null} to run unregistered.
   */
  private String palDirectoryUrl;

  /**
   * Unique identifier for this peer. If not provided, a random UUID is generated. Mapped from the
   * PEER_UUID environment variable.
   */
  @Option(
      names = {"-u", "--uuid"},
      order = 3,
      paramLabel = "uuid",
      description = "uuid for this peer (default: <random>) [env: PAL_PEER_UUID]")
  private UUID uuid; // corresponding ENV var: PAL_PEER_UUID

  /**
   * Human-readable name for this peer. Used during registration and corresponds to the PEER_NAME
   * environment variable.
   */
  @Option(
      names = {"-n", "--name"},
      order = 4,
      arity = "1",
      paramLabel = "name",
      description = "name for this peer [env: PAL_PEER_NAME]")
  private String name; // corresponding ENV var: PAL_PEER_NAME

  /**
   * Flag indicating whether to continue running as a service after executing the main class or JAR.
   */
  @Option(
      names = {"--as-service"},
      order = 5,
      description = "keep running after call to mainClass/jar returns")
  private boolean asService = false;

  /**
   * Specifies the Log name from which messages are read. When set to 'auto', a new Log is created
   * with an autogenerated name (uses --log-prefix).
   */
  @Option(
      names = {"-s", "--source-log"},
      order = 10,
      paramLabel = "name|auto|file:/path",
      description =
          "Kafka topic or Chronicle queue to consume messages from. Use 'auto' to let Pal generate"
              + " a Kafka topic name with --log-prefix ('auto' works only with <pal_directory>)."
              + " Use 'file:/path' for Chronicle queue (absolute or relative path)."
              + " [env: PAL_SOURCE_LOG]")
  private String sourceLog; // corresponding ENV var: PAL_SOURCE_LOG

  /**
   * Specifies the starting offset/index for reading messages from the source-log. For Kafka, this
   * is the offset; for Chronicle, this is the queue index.
   */
  @Option(
      names = {"-O", "--start-offset"},
      order = 11,
      paramLabel = "offset",
      description =
          "seek the source-log to this offset/index before reading (Kafka offset or Chronicle index)")
  private Long startOffset;

  /**
   * Specifies the Log name to which WAL messages are written. When set to 'auto', a new Log is
   * created with an autogenerated name (uses --log-prefix).
   */
  @Option(
      names = {"-w", "--wal"},
      order = 12,
      paramLabel = "name|auto|file:/path",
      description =
          "Kafka topic or Chronicle queue where Pal writes its write-ahead log. Use 'auto' to let"
              + " Pal generate a Kafka topic name with --log-prefix ('auto' works only with"
              + " <pal_directory>). Use 'file:/path' for Chronicle queue (absolute or relative path)."
              + " [env: PAL_WAL]")
  private String wal; // corresponding ENV var: PAL_WAL

  /**
   * Flag to enable writing incoming RPC calls (from ZMQ, JSON-RPC, and CLI channels) to WAL/PUB in
   * both BEFORE and AFTER phases, consistent with the hot-path {@code dispatch()} behavior.
   * Messages arriving via LOG_RPC are excluded; use {@code --wal-all-incoming-rpc} to include
   * those.
   */
  @Option(
      names = {"--wal-incoming-rpc"},
      order = 13,
      negatable = true,
      fallbackValue = "true",
      description =
          "Write incoming RPC calls to WAL (in addition to locally-initiated calls) [env: PAL_WAL_INCOMING_RPC]")
  private boolean walIncomingRpc = true;

  /**
   * Flag to enable writing ALL incoming RPC calls to WAL/PUB, including LOG_RPC channel messages.
   * Implies {@code --wal-incoming-rpc}. Intended for scenarios where the source log and WAL are
   * different; when they are the same log, the circularity guard overrides this option.
   */
  @Option(
      names = {"--wal-all-incoming-rpc"},
      order = 14,
      description =
          "Write ALL incoming RPC calls to WAL including LOG_RPC"
              + " (implies --wal-incoming-rpc) [env: PAL_WAL_ALL_INCOMING_RPC]")
  private boolean walAllIncomingRpc = false;

  /**
   * Flag to enable writing incoming CLI bootstrap calls (from {@code SelfBootstrapInvoker}) to
   * WAL/PUB in both BEFORE and AFTER phases. This is independent of {@code --wal-incoming-rpc},
   * which controls ZMQ and WebSocket RPC channels.
   */
  @Option(
      names = {"--wal-incoming-cli"},
      order = 15,
      negatable = true,
      fallbackValue = "true",
      description = "Write incoming CLI bootstrap calls to WAL [env: PAL_WAL_INCOMING_CLI]")
  private boolean walIncomingCli = true;

  /**
   * Specifies the WAL path for deterministic replay mode. When set, the peer re-executes the
   * application from {@code main()} while verifying each operation against the pre-recorded WAL.
   * Mutually exclusive with {@code --wal}, {@code --source-log}, and {@code --log}.
   *
   * <p>Accepts either a Chronicle Queue path ({@code file:/path}) or a Kafka topic name. When using
   * a Kafka topic, {@code --kafka-servers} must also be provided.
   */
  @Option(
      names = {"--replay-wal"},
      order = 40,
      paramLabel = "name|file:/path",
      description =
          "WAL path for deterministic replay: file:/path for Chronicle, topic name for Kafka"
              + " (mutually exclusive with --wal, --source-log, and --log)")
  private String replayWalPath;

  /**
   * Divergence handling policy for deterministic replay mode. Controls what happens when live
   * execution diverges from the WAL oracle. Only relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-divergence-policy"},
      order = 41,
      paramLabel = "WARN|HALT|IGNORE",
      defaultValue = "WARN",
      description =
          "Divergence handling policy for replay: WARN, HALT, IGNORE (default: ${DEFAULT-VALUE})")
  private String replayDivergencePolicy;

  /**
   * Thread ordering mode for multi-threaded deterministic replay. Controls whether entry-point
   * injection follows WAL-offset ordering or runs without ordering constraints. Only relevant when
   * {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-threading"},
      order = 42,
      paramLabel = "ordered|unordered",
      defaultValue = "ordered",
      description =
          "Thread ordering for multi-threaded replay: ordered (default) or unordered"
              + " (default: ${DEFAULT-VALUE})")
  private String replayThreading;

  /**
   * Delay in milliseconds before processing each OPERATION entry during replay. Used for
   * slow-motion replay visualization. A value of {@code 0} disables the delay. Only relevant when
   * {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-delay"},
      order = 43,
      paramLabel = "milliseconds",
      defaultValue = "0",
      description =
          "Delay in milliseconds before each operation entry for slow-motion replay visualization"
              + " (default: ${DEFAULT-VALUE})")
  private String replayDelay;

  /**
   * Path to a YAML replay policy file that defines per-class/method rules for re-executing or
   * stubbing operations during replay. Only relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-policy"},
      order = 44,
      paramLabel = "path",
      description = "Path to YAML replay policy file for side-effect shielding")
  private String replayPolicyPath;

  /**
   * Enables built-in I/O stubbing rules that stub non-deterministic operations such as {@code
   * System.currentTimeMillis()}, {@code Math.random()}, and standard I/O classes during replay.
   * Only relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-shield-io"},
      order = 45,
      description = "Enable built-in I/O stubbing rules for non-deterministic operations")
  private boolean replayShieldIo;

  /**
   * Enables built-in JavaFX stubbing rules that stub wall-clock-dependent operations such as {@code
   * Animation.play()}, {@code Timeline.play()}, and {@code AnimationTimer.start()} during replay.
   * Only relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-shield-fx"},
      order = 46,
      description = "Enable built-in JavaFX stubbing rules for animation/timing operations")
  private boolean replayShieldFx;

  /**
   * Comma-separated Ant-style patterns for classes/methods to re-execute during replay. Only
   * relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-re-execute"},
      order = 47,
      paramLabel = "patterns",
      split = ",",
      description = "Comma-separated Ant-style patterns for classes to re-execute during replay")
  private String[] replayReExecutePatterns;

  /**
   * Comma-separated Ant-style patterns for classes/methods to stub from the WAL during replay.
   * Stubbed operations return WAL-recorded values without executing. Only relevant when {@code
   * --replay-wal} is set.
   */
  @Option(
      names = {"--replay-stub"},
      order = 48,
      paramLabel = "patterns",
      split = ",",
      description = "Comma-separated Ant-style patterns for classes to stub from WAL during replay")
  private String[] replayStubPatterns;

  /**
   * When set, all operations not matching a {@code --replay-re-execute} pattern are stubbed from
   * the WAL. Only relevant when {@code --replay-wal} is set.
   */
  @Option(
      names = {"--replay-stub-all-else"},
      order = 49,
      description = "Stub all operations not matching --replay-re-execute patterns")
  private boolean replayStubAllElse;

  /**
   * When set, proceeds with replay even when the side-effect analyzer detects unsafe stubs. Without
   * this flag, the replay fails fast on unsafe stubs. Only relevant when {@code --replay-wal} is
   * set.
   */
  @Option(
      names = {"--replay-force-stub"},
      order = 50,
      description = "Proceed even when unsafe stubs are detected by side-effect analysis")
  private boolean replayForceStub;

  // ---- Recording scope options ----

  /**
   * Ant-style class patterns for operations to record to WAL/PUB. Repeatable, comma-separated. When
   * specified, only matching operations are recorded (unless overridden by {@code
   * --scope-default}).
   */
  @Option(
      names = {"--scope"},
      order = 30,
      paramLabel = "patterns",
      split = ",",
      description =
          "Ant-style class patterns for operations to record to WAL/PUB"
              + " (repeatable, comma-separated)")
  private String[] scopePatterns;

  /**
   * Ant-style class patterns for operations to exclude from recording. Repeatable, comma-separated.
   */
  @Option(
      names = {"--scope-exclude"},
      order = 31,
      paramLabel = "patterns",
      split = ",",
      description =
          "Ant-style class patterns for operations to exclude from recording"
              + " (repeatable, comma-separated)")
  private String[] scopeExcludePatterns;

  /**
   * Include common I/O boundary operations (JDBC, HTTP, file I/O, time, random) in recording scope.
   */
  @Option(
      names = {"--scope-io"},
      order = 32,
      description =
          "Include common I/O boundary operations"
              + " (JDBC, HTTP, file I/O, time, random) in recording scope")
  private boolean scopeIo;

  /** Path to a YAML recording scope policy file. */
  @Option(
      names = {"--scope-policy"},
      order = 33,
      paramLabel = "path",
      description = "Path to YAML recording scope policy file")
  private String scopePolicyPath;

  /**
   * Default scope action when no rule matches. Accepted values: {@code record} or {@code skip}. If
   * not specified, the default is inferred from the flags provided.
   */
  @Option(
      names = {"--scope-default"},
      order = 34,
      paramLabel = "record|skip",
      description =
          "Default scope action when no rule matches" + " (inferred from flags if not specified)")
  private String scopeDefaultAction;

  /**
   * Log configuration specifying the Log name for both reading and writing. Using 'auto' works only
   * when a PAL directory is specified.
   */
  @Option(
      names = {"-l", "--log"},
      order = 16,
      paramLabel = "name|auto|file:/path",
      description =
          "Shorthand: use the same Kafka topic or Chronicle queue for both source and wal. Use 'auto'"
              + " to let Pal generate a Kafka topic name with --log-prefix ('auto' works only with"
              + " <pal_directory>). Use 'file:/path' for Chronicle queue (absolute or relative path)."
              + " [env: PAL_LOG]")
  private String log; // corresponding ENV var: PAL_LOG

  /**
   * Comma-separated list of Kafka bootstrap servers. Required when Log options are provided. Maps
   * to the PAL_KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      order = 17,
      paramLabel = "bootstrap_servers",
      description =
          "connect to given kafka servers (required with -l/--log, -s/--source-log and -w/--wal)"
              + " [env: PAL_KAFKA_SERVERS]")
  private String kafkaServers; // corresponding ENV var: PAL_KAFKA_SERVERS

  /**
   * Prefix to be used when generating Log names for --log auto / --source-log auto / --wal auto.
   */
  @Option(
      names = {"--log-prefix"},
      order = 18,
      paramLabel = "prefix",
      description =
          "prefix to generate Log names when specified as 'auto' (default: ${DEFAULT-VALUE})"
              + " [env: PAL_LOG_PREFIX]",
      defaultValue = "app",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
  private String logPrefix; // corresponding ENV var: PAL_LOG_PREFIX

  /** Base directory for Chronicle queues when using relative paths with file: prefix. */
  @Option(
      names = {"--chronicle-base-dir"},
      order = 19,
      paramLabel = "path",
      description =
          "base directory for relative Chronicle paths (file:mylog). Absolute paths (file:/path)"
              + " ignore this. Default: current working directory"
              + " [env: PAL_CHRONICLE_BASE_DIR]")
  private String chronicleBaseDir; // corresponding ENV var: PAL_CHRONICLE_BASE_DIR

  /**
   * Path to an external properties file that overlays the built-in {@code pal.properties} defaults.
   * Individual property overrides can be supplied via {@code -D} system properties (through {@code
   * PAL_JAVA_OPTS} or {@code pal.vmoptions}).
   */
  @Option(
      names = {"--properties"},
      order = 90,
      paramLabel = "path",
      description =
          "path to external properties file (overlays built-in pal.properties defaults)"
              + " [env: PAL_PROPERTIES]")
  private String propertiesFile; // corresponding ENV var: PAL_PROPERTIES

  /**
   * Timeout in milliseconds for Kafka connection health check during initialization. If Kafka
   * doesn't respond within this time, the peer will fail to start.
   */
  @Option(
      names = {"--kafka-timeout"},
      order = 91,
      paramLabel = "milliseconds",
      description =
          "timeout for Kafka connection health check in milliseconds (default: ${DEFAULT-VALUE})"
              + " [env: PAL_KAFKA_TIMEOUT_MS]",
      defaultValue = "5000")
  private Integer kafkaConnectTimeout;

  /**
   * Timeout in milliseconds for etcd connection health check during initialization when using a PAL
   * directory. Applied to preflight TCP/HTTP checks and jetcd status check.
   */
  @Option(
      names = {"--etcd-timeout"},
      order = 92,
      paramLabel = "milliseconds",
      description =
          "timeout for etcd connection health check in milliseconds (default: ${DEFAULT-VALUE})"
              + " [env: PAL_ETCD_TIMEOUT_MS]",
      defaultValue = "5000")
  private Integer etcdConnectTimeout;

  /**
   * Configuration for TCP-based message publication via ZeroMQ. It accepts a format of
   * "[HOST:]PORT" or "auto" for automatic assignment. Mapped from the TCP_PUB environment variable.
   */
  @Option(
      names = {"-p", "--tcp-pub"},
      order = 70,
      paramLabel = "[HOST:]PORT|auto",
      description =
          "publish messages to ZeroMQ socket (auto = localhost:random_port) [env: PAL_TCP_PUB]")
  private String tcpPub; // corresponding ENV var: PAL_TCP_PUB

  /**
   * Configuration for the ZMQ-RPC listener over ZeroMQ. Accepts "[HOST:]PORT" or "auto" and
   * corresponds to the ZMQ_RPC environment variable.
   */
  @Option(
      names = {"-r", "--zmq-rpc"},
      order = 71,
      paramLabel = "[HOST:]PORT|auto",
      description =
          "listen for RPC requests on ZeroMQ socket (auto = localhost:random_port)"
              + " [env: PAL_ZMQ_RPC]")
  private String zmqRpc; // corresponding ENV var: PAL_ZMQ_RPC

  /**
   * Configuration for the JSON-RPC listener over WebSocket. Accepts "[HOST:]PORT" or "auto" and
   * corresponds to the JSON_RPC environment variable.
   */
  @Option(
      names = {"-j", "--json-rpc"},
      order = 72,
      paramLabel = "[HOST:]PORT|auto",
      description =
          "listen for JSON-RPC requests on WebSocket (auto = localhost:random_port)"
              + " [env: PAL_JSON_RPC]")
  private String jsonRpc; // corresponding ENV var: PAL_JSON_RPC

  /** Number of threads allocated for handling RPC requests. The default value is 1. */
  @Option(
      names = {"--rpc-threads"},
      order = 73,
      defaultValue = "1",
      paramLabel = "num_threads",
      description =
          "number of threads for RPC requests (default: ${DEFAULT-VALUE}) [env: PAL_RPC_THREADS]")
  private Integer rpcThreads; // corresponding ENV var: PAL_RPC_THREADS

  /** Path to an RPC access policy YAML file that controls which operations are allowed via RPC. */
  @Option(
      names = {"--rpc-policy"},
      order = 74,
      description = "path to RPC access policy YAML file [env: PAL_RPC_POLICY]")
  private String rpcPolicyPath; // corresponding ENV var: PAL_RPC_POLICY

  /**
   * Comma-separated list of built-in preset names to enable (e.g., {@code
   * deny-unsafe,deny-jdk-internals}).
   */
  @Option(
      names = {"--rpc-policy-preset"},
      order = 75,
      description =
          "comma-separated preset names (e.g., deny-unsafe,deny-jdk-internals)"
              + " [env: PAL_RPC_POLICY_PRESET]")
  private String rpcPolicyPresets; // corresponding ENV var: PAL_RPC_POLICY_PRESET

  /**
   * Default RPC action when no policy rule matches. Must be {@code ALLOW} or {@code DENY}. Defaults
   * to {@code DENY} so that peers deny all RPC operations unless explicitly allowed by a policy.
   */
  @Option(
      names = {"--rpc-default-action"},
      order = 76,
      description =
          "default RPC action when no rule matches: ALLOW or DENY (default: DENY)"
              + " [env: PAL_RPC_DEFAULT_ACTION]")
  private String rpcDefaultAction; // corresponding ENV var: PAL_RPC_DEFAULT_ACTION

  /**
   * Poll interval in milliseconds for the RPC policy file watcher. When set, the peer watches the
   * policy YAML file for changes and reloads the policy automatically. A value of {@code 0}
   * disables file watching. When not specified, the default interval from {@link
   * io.quasient.pal.core.rpc.policy.RpcPolicyFileWatcher#DEFAULT_POLL_INTERVAL_MS} is used.
   */
  @Option(
      names = {"--rpc-policy-watch-interval"},
      order = 77,
      description =
          "policy file poll interval in ms (default: 2000, 0 = disable watching)"
              + " [env: PAL_RPC_POLICY_WATCH_INTERVAL]")
  private Integer rpcPolicyWatchInterval; // corresponding ENV var: PAL_RPC_POLICY_WATCH_INTERVAL

  /**
   * Flag indicating whether message interception is enabled. Only applicable when registering with
   * a PAL directory.
   */
  @Option(
      names = {"--interceptable"},
      order = 60,
      description = "allow message interception [env: PAL_INTERCEPTABLE]")
  private boolean interceptable = false;

  /**
   * Flag to enable JavaFX Application Thread execution for RPC calls that request {@code fx-thread}
   * affinity. When enabled, the peer registers a {@code JavaFxInvocationExecutor} that routes
   * matching RPC calls to the JavaFX Application Thread via {@code Platform.runLater()}.
   */
  @Option(
      names = {"--fx-thread"},
      order = 78,
      description =
          "enable JavaFX Application Thread execution for RPC calls that request"
              + " 'fx-thread' affinity. When using this option, consider --rpc-threads 2+"
              + " to prevent RPC starvation during long UI operations"
              + " (default: ${DEFAULT-VALUE}) [env: PAL_FX_THREAD]",
      defaultValue = "false",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
  private boolean fxThread;

  /**
   * Regex pattern for service request handler thread names. Entry points on matching threads are
   * tagged with 'service-request' affinity and wrapped in a CDI request context during replay.
   */
  @Option(
      names = {"--service-thread"},
      order = 51,
      paramLabel = "<pattern>",
      description =
          "regex pattern for service request handler thread names. Entry points on matching"
              + " threads are tagged with 'service-request' affinity and wrapped in CDI"
              + " request context during replay [env: PAL_SERVICE_THREAD]")
  private String serviceThreadPattern;

  /**
   * Flag to include source context in Log messages. When enabled, additional source information is
   * included.
   */
  @Option(
      names = {"--with-source-context"},
      order = 20,
      description = "include source context in messages [env: PAL_WITH_SOURCE_CONTEXT]")
  private boolean includeSourceContext = false;

  /**
   * Flag to enable in-flight dispatch tracking for intercept coordination. When enabled, intercept
   * registration will wait for in-flight method calls to complete before activating the intercept,
   * ensuring guaranteed quiescence.
   */
  @Option(
      names = {"--in-flight-tracking"},
      order = 61,
      description =
          "enable in-flight dispatch tracking for intercept coordination (default: ${DEFAULT-VALUE})"
              + " [env: PAL_IN_FLIGHT_TRACKING]",
      defaultValue = "true",
      fallbackValue = "true",
      arity = "0..1",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
  private Boolean inFlightTracking; // corresponding ENV var: PAL_IN_FLIGHT_TRACKING

  /**
   * Timeout in milliseconds for drain operations when waiting for in-flight dispatches to complete
   * before activating an intercept.
   */
  @Option(
      names = {"--drain-timeout-ms"},
      order = 62,
      paramLabel = "milliseconds",
      description =
          "timeout for drain operations when waiting for in-flight dispatches (default: ${DEFAULT-VALUE})"
              + " [env: PAL_DRAIN_TIMEOUT_MS]",
      defaultValue = "5000",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
  private Integer drainTimeoutMs; // corresponding ENV var: PAL_DRAIN_TIMEOUT_MS

  /**
   * Default timeout in milliseconds for receiving responses from synchronous intercept callback
   * requests. Controls how long the intercepted peer waits for a callback peer to respond. Can be
   * overridden per-intercept via the intercept registration.
   *
   * <p>A value of 0 means no timeout (infinite wait).
   */
  @Option(
      names = {"--callback-timeout-ms"},
      order = 63,
      paramLabel = "milliseconds",
      description =
          "default timeout for intercept callback responses, 0 = no timeout (default: ${DEFAULT-VALUE})"
              + " [env: PAL_CALLBACK_TIMEOUT_MS]",
      defaultValue = "3000",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
  private Integer callbackTimeoutMs; // corresponding ENV var: PAL_CALLBACK_TIMEOUT_MS

  /**
   * Global default exception propagation policy for intercept callbacks. Determines how exceptions
   * thrown by callbacks propagate to callers. Does not apply to ASYNC intercepts (which always use
   * SWALLOW_ALL).
   */
  @Option(
      names = {"--exception-policy"},
      order = 64,
      paramLabel = "POLICY",
      description =
          "global exception propagation policy for intercept callbacks. Valid values: "
              + "PROPAGATE_ALL, PROPAGATE_EXPLICIT_ONLY, SWALLOW_ALL, PROPAGATE_CONTROLLED_ONLY "
              + "(default: PROPAGATE_CONTROLLED_ONLY) [env: PAL_EXCEPTION_POLICY]")
  private String exceptionPolicy; // corresponding ENV var: PAL_EXCEPTION_POLICY

  /**
   * Global default checked exception policy for intercept callbacks. Determines how checked
   * exceptions set via setExceptionToThrow are handled when not declared by the intercepted method.
   */
  @Option(
      names = {"--checked-exception-policy"},
      order = 65,
      paramLabel = "POLICY",
      description =
          "global checked exception policy for intercept callbacks. Valid values: "
              + "WRAP, REJECT, ALLOW_ALL (default: WRAP) [env: PAL_CHECKED_EXCEPTION_POLICY]")
  private String checkedExceptionPolicy; // corresponding ENV var: PAL_CHECKED_EXCEPTION_POLICY

  /**
   * Flag to trigger display of the help message for command-line usage. Handled automatically by
   * the CLI parser.
   */
  @Option(
      names = {"-h", "--help"},
      order = 6,
      usageHelp = true,
      description = "display this help message")
  @SuppressWarnings("unused")
  private boolean helpRequested = false;

  /** Specifies a JAR file to execute. This hidden option is used internally when running a JAR. */
  @Option(
      names = {"-jar"},
      description = "execute jar file",
      hidden = true)
  private String jarFile;

  /**
   * List of command-line arguments passed to the executable. This parameter is hidden from standard
   * usage.
   */
  @SuppressWarnings("unused")
  @Parameters(hidden = true)
  private List<String> cmdArgList;

  /**
   * Positional arguments extracted from the command-line, excluding the main class name or jar
   * specification.
   */
  private List<String> argList;

  /** Fully qualified name of the main class to execute, derived from command-line arguments. */
  private String className;

  /** Properties used to configure the peer. Loaded from the properties file and environment. */
  private final Properties properties = new Properties();

  /** Set of runtime options determined from the command-line arguments and environment. */
  private Set<RunOptions> runOptions;

  /** Custom classloader configured with specified classpath entries and JAR file. */
  private CustomClassloader customClassloader;

  /** ZeroMQ context for message communication. */
  private ZContext zmqContext;

  /** ZeroMQ socket used for synchronization and readiness signaling. */
  private Socket syncSocket;

  /** Lease for maintaining this peer's state liveness in the pal Directory. */
  private PeerLease peerLease;

  /** RPC policy file watcher, or {@code null} if file watching is not configured. */
  @Nullable private RpcPolicyFileWatcher rpcPolicyFileWatcher;

  /** Latch used to prevent premature exit when running in service mode. */
  private final CountDownLatch runAsServiceLatch = new CountDownLatch(1);

  /**
   * Guards the replay divergence report so it is printed exactly once, whether the main thread
   * reaches the post-replay code on normal exit or the shutdown hook runs first on SIGTERM.
   */
  private final AtomicBoolean replayDivergenceReportPrinted = new AtomicBoolean(false);

  /** SLF4J logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /** Path to the peer properties file in the classpath. */
  private static final String PROPERTIES_FILE = "/pal.properties";

  /** Path to the default logging configuration file in the classpath. */
  private static final String LOGGING_CONFIG = "/peer-logging-fallback.xml";

  /** Duration to wait for managed services to stop. */
  private static final Duration SERVICE_MANAGER_AWAIT_TERM = Duration.of(15, ChronoUnit.SECONDS);

  /** Duration to wait for exec service to stop. */
  private static final Duration EXECUTOR_AWAIT_TERM = Duration.of(1, ChronoUnit.SECONDS);

  /** Default value, in seconds, for this peer's keep-alive. */
  private static final long PEER_KA_SECS_DEFAULT = 60;

  /** Default initial capacity for WAL and PUB queue (must be power of 2). */
  private static final int MPSC_INITIAL_DEFAULT = 1 << 10; // 1024

  /** Default chunk size for WAL and PUB queue (must be power of 2). */
  private static final int MPSC_CHUNK_DEFAULT = 1 << 10; // 1024

  /** Default maximum capacity for WAL and PUB queue (must be power of 2). */
  private static final int MPSC_MAX_DEFAULT = 1 << 20; // 1 048 576

  /** Default capacity for MessagePublisher's internal SPSC queue (must be power of 2). */
  private static final int SPSC_SIZE_DEFAULT = 1 << 18; // 262144

  /** Container for default ZeroMQ configuration properties and internal endpoint mappings. */
  private static final class ZmqProperties {
    /** Default linger period (in milliseconds) for the ZeroMQ context. */
    private static final String ZMQ_LINGER_DEFAULT = "1000";

    /** Default receive high watermark for the ZeroMQ context. */
    private static final String ZMQ_RCVHWM_DEFAULT = "10000";

    /** Default send high watermark for the ZeroMQ context. */
    private static final String ZMQ_SNDHWM_DEFAULT = "10000";

    /** Property key for the output publication channel configuration. */
    private static final String OUT_PUB_CHANNEL = "out.pub";

    /** Default hostname for TCP PUB interface when auto-assigned. */
    private static final String DEFAULT_PUB_HOSTNAME = "localhost";

    /** Default hostname for the ZMQ-RPC listener when auto-assigned. */
    private static final String DEFAULT_ZMQ_RPC_HOSTNAME = "localhost";

    /** Default hostname for the JSON-RPC listener when auto-assigned. */
    private static final String DEFAULT_JSONRPC_HOSTNAME = "localhost";

    /** Properties holding internal in-process ZeroMQ endpoint mappings. */
    private static final Properties inprocEndpoints = new Properties();

    static {
      inprocEndpoints.put("source.log", "inproc://source_log");
      inprocEndpoints.put("in.dealer", "inproc://deal_zmq_rpc");
      inprocEndpoints.put("json.in.dealer", "inproc://deal_json_rpc");
      inprocEndpoints.put("out.cell", "inproc://cell");
      inprocEndpoints.put("out.pub.inproc", "inproc://pub");
      inprocEndpoints.put("offset.pub", "inproc://offsets");
      inprocEndpoints.put("sync.ready", "inproc://sync_ready");
      inprocEndpoints.put("intercepts.reg", "inproc://intercept_reg");
      inprocEndpoints.put("session.svc", "inproc://session");
    }
  }

  /**
   * Initializes and configures the logging system using Logback.
   *
   * <p>If a system property "peer.logging" is set and points to an existing file, that
   * configuration is used. Otherwise, the default logging configuration resource is loaded.
   */
  private void initLogging() {
    // configure logging
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    context.reset();

    // look for a property named peer.logging in the System properties
    final String palLogging = System.getProperty("peer.logging");
    if (palLogging != null && !palLogging.trim().isEmpty()) {
      boolean givenFileExists = false;
      try {
        if (Files.exists(Paths.get(palLogging))) {
          givenFileExists = true;
        }
      } catch (InvalidPathException | SecurityException ex) {
        ex.printStackTrace(System.err);
      }
      if (givenFileExists) {
        try {
          configurator.doConfigure(palLogging);
        } catch (JoranException ex) {
          System.err.printf("Error loading logging configuration from %s%n", palLogging);
          // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
          //noinspection CallToPrintStackTrace
          ex.printStackTrace();
        }
        return;
      }
    }

    // fall back to our default logging configuration
    try (final InputStream stream = Main.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (JoranException | IOException ex) {
      System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
      // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
      //noinspection CallToPrintStackTrace
      ex.printStackTrace();
    }
  }

  /** Property keys that must not be overridden — PAL's internal serdes classes. */
  private static final Set<String> LOCKED_PROPERTIES =
      Set.of("key.deserializer", "value.deserializer");

  /**
   * Loads application properties with the following precedence (highest wins):
   *
   * <ol>
   *   <li>{@code -D} system properties (via {@code PAL_JAVA_OPTS} or {@code pal.vmoptions})
   *   <li>External properties file ({@code --properties /path})
   *   <li>Built-in {@code pal.properties} from classpath
   * </ol>
   *
   * <p>If the built-in properties cannot be loaded, the application terminates with a fatal error.
   * Properties in {@link #LOCKED_PROPERTIES} are ignored if set by external sources.
   */
  private void loadProps() {
    // 1. load built-in pal.properties from classpath
    try (final InputStream stream = Main.class.getResourceAsStream(PROPERTIES_FILE)) {
      properties.load(stream);
    } catch (IOException ex) {
      fatalExit(
          ex,
          PeerException.FatalCode.ERROR_LOADING_PROPERTIES,
          format("Make sure to have `%s` in the classpath", PROPERTIES_FILE));
    }
    logger.info("Loaded built-in properties from `{}`", PROPERTIES_FILE);

    // 2. overlay external properties file if provided
    String externalFile = propertiesFile;
    if (externalFile == null || externalFile.isBlank()) {
      externalFile = System.getenv("PAL_PROPERTIES");
    }
    if (externalFile != null && !externalFile.isBlank()) {
      Path externalPath = Paths.get(externalFile.trim());
      if (!Files.exists(externalPath)) {
        fatalExit(
            new IOException("Properties file not found: " + externalPath),
            PeerException.FatalCode.ERROR_LOADING_PROPERTIES,
            format("External properties file `%s` does not exist", externalPath));
      }
      try (InputStream stream = Files.newInputStream(externalPath)) {
        properties.load(stream);
        logger.info("Loaded external properties from `{}`", externalPath);
      } catch (IOException ex) {
        fatalExit(
            ex,
            PeerException.FatalCode.ERROR_LOADING_PROPERTIES,
            format("Error reading external properties file `%s`", externalPath));
      }
    }

    // 3. overlay -D system properties for any key present in the loaded properties
    for (String key : properties.stringPropertyNames()) {
      String sysProp = System.getProperty(key);
      if (sysProp != null) {
        properties.setProperty(key, sysProp);
        logger.debug("Property `{}` overridden by -D system property", key);
      }
    }

    // 4. enforce locked properties — warn and restore if overridden
    for (String lockedKey : LOCKED_PROPERTIES) {
      String value = properties.getProperty(lockedKey);
      if (value != null) {
        logger.warn(
            "Property `{}` is locked (PAL internal serdes) and cannot be overridden — ignoring",
            lockedKey);
        properties.remove(lockedKey);
      }
    }
  }

  /**
   * Validates the loaded properties, converting and overriding types where necessary.
   *
   * <p>If any property value is invalid, the application terminates with a fatal error.
   */
  private void validateProperties() {

    try {
      // ------------- validate WAL Queue params --------------
      int walInitial = readPowerOfTwo(properties, "wal.queue.initial", MPSC_INITIAL_DEFAULT);
      properties.setProperty("wal.queue.initial", String.valueOf(walInitial));

      int walChunk = readPowerOfTwo(properties, "wal.queue.chunk", MPSC_CHUNK_DEFAULT);
      properties.setProperty("wal.queue.chunk", String.valueOf(walChunk));

      int walMax = readPowerOfTwo(properties, "wal.queue.max", MPSC_MAX_DEFAULT);
      properties.setProperty("wal.queue.max", String.valueOf(walMax));

      // ------------- validate PUB Queue params --------------
      int pubInitial = readPowerOfTwo(properties, "pub.queue.initial", MPSC_INITIAL_DEFAULT);
      properties.setProperty("pub.queue.initial", String.valueOf(pubInitial));

      int pubChunk = readPowerOfTwo(properties, "pub.queue.chunk", MPSC_CHUNK_DEFAULT);
      properties.setProperty("pub.queue.chunk", String.valueOf(pubChunk));

      int pubMax = readPowerOfTwo(properties, "pub.queue.max", MPSC_MAX_DEFAULT);
      properties.setProperty("pub.queue.max", String.valueOf(pubMax));

      // ------------- validate MessagePublisher internal Queue params --------------
      int pubSpsc = readPowerOfTwo(properties, "pub.spsc_size", SPSC_SIZE_DEFAULT);
      properties.setProperty("pub.spsc_size", String.valueOf(pubSpsc));

    } catch (IllegalArgumentException e) {
      fatalExit(e, PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES);
    }
  }

  /**
   * Return the property’s value (or the supplied default) or throw IllegalArgumentException if the
   * value exists but is not a power of 2.
   *
   * @param props loaded properties
   * @param key the property name
   * @param defaultVal the default
   * @return the property value, if is a power of 2
   * @throws IllegalArgumentException if the value exists but is not a power of 2
   */
  private static int readPowerOfTwo(Properties props, String key, int defaultVal)
      throws IllegalArgumentException {
    String raw = props.getProperty(key); // null = not supplied
    int value = (raw == null) ? defaultVal : Integer.parseInt(raw.trim());

    if (raw != null && !isPowerOfTwo(value)) { // only validate when user overrode
      throw new IllegalArgumentException(key + " must be a power of two (was " + value + ')');
    }
    return value;
  }

  /**
   * True if n is a positive power of 2.
   *
   * @param n the integer to check
   * @return true if n is a positive power of 2
   */
  private static boolean isPowerOfTwo(int n) {
    return n > 0 && (n & (n - 1)) == 0;
  }

  /**
   * Initializes and configures the ZeroMQ context.
   *
   * <p>The configuration values for linger, receive high watermark, and send high watermark are
   * read from the application properties. Additionally, a synchronization socket is created and
   * bound to a predefined endpoint.
   */
  private void initZmqContext() {
    zmqContext = new ZContext();
    zmqContext.setLinger(
        Integer.parseInt(properties.getProperty("ZMQ_LINGER", ZmqProperties.ZMQ_LINGER_DEFAULT)));
    zmqContext.setRcvHWM(
        Integer.parseInt(properties.getProperty("ZMQ_RCVHWM", ZmqProperties.ZMQ_RCVHWM_DEFAULT)));
    zmqContext.setSndHWM(
        Integer.parseInt(properties.getProperty("ZMQ_SNDHWM", ZmqProperties.ZMQ_SNDHWM_DEFAULT)));
    logger.info("Created and configured zmq context");

    // start ready socket
    syncSocket = zmqContext.createSocket(SocketType.PULL);
    syncSocket.bind(ZmqProperties.inprocEndpoints.getProperty("sync.ready"));
  }

  /** Closes the ZeroMQ context and associated resources. */
  private void closeZmqContext() {
    if (logger.isDebugEnabled()) {
      logger.debug("Closing zmq context");
    }
    zmqContext.close();
    logger.info("Closed zmq context");
  }

  /**
   * Terminates the application fatally due to a critical PeerException.
   *
   * @param peerException the PeerException triggering the fatal exit
   */
  private void fatalExit(PeerException peerException) {
    fatalExit(peerException, peerException.getFatalCode());
  }

  /**
   * Terminates the application fatally using a Throwable and a fatal code.
   *
   * @param ex the Throwable causing the termination
   * @param fatalCode the fatal error code used for exit
   */
  private void fatalExit(Throwable ex, PeerException.FatalCode fatalCode) {
    fatalExit(ex, fatalCode, null);
  }

  /**
   * Terminates the application fatally, logging the exception, printing error messages, and exiting
   * with the specified code.
   *
   * @param ex the Throwable causing the termination (can be null)
   * @param fatalCode the fatal error code used for exit
   * @param extraMessage an optional extra message to display
   */
  private void fatalExit(Throwable ex, PeerException.FatalCode fatalCode, String extraMessage) {
    if (ex != null) {
      logger.error(fatalCode.getMessage(), ex);
    }
    System.err.println(fatalCode.getMessage());
    if (extraMessage != null) {
      System.err.println(extraMessage);
    }
    System.exit(fatalCode.getCode());
  }

  /**
   * Returns the first non-blank value from: the CLI-provided value, then the environment variable.
   *
   * @param envKey the environment variable key
   * @param paramValue the command-line provided parameter value
   * @return the resolved value, or null if neither source provides one
   */
  private static String getParameter(String envKey, String paramValue) {
    if (paramValue != null && !paramValue.trim().isEmpty()) {
      return paramValue;
    }
    final String envVar = System.getenv(envKey);
    if (envVar != null && !envVar.trim().isEmpty()) {
      return envVar.trim();
    }
    return null;
  }

  /**
   * Returns the environment variable value parsed as an Integer, or null if absent/invalid.
   *
   * @param envKey the environment variable key
   * @return the parsed integer, or null
   */
  private static Integer getIntEnv(String envKey) {
    String val = System.getenv(envKey);
    if (val != null && !val.isBlank()) {
      try {
        return Integer.parseInt(val.trim());
      } catch (NumberFormatException ignored) {
        // keep CLI/default
      }
    }
    return null;
  }

  /**
   * Returns the environment variable value parsed as a Boolean, or null if absent.
   *
   * @param envKey the environment variable key
   * @return the parsed boolean, or null
   */
  @SuppressFBWarnings(
      value = "NP_BOOLEAN_RETURN_NULL",
      justification = "Null signals 'env var not set' vs 'set to false'")
  private static Boolean getBoolEnv(String envKey) {
    String val = System.getenv(envKey);
    if (val != null && !val.isBlank()) {
      return Boolean.parseBoolean(val.trim());
    }
    return null;
  }

  /**
   * Sets various parameters from environment variables if they are not already provided via
   * command-line options. All PAL-specific environment variables use the {@code PAL_} prefix.
   *
   * <p>This method prioritizes command-line inputs and, if missing, retrieves values from
   * corresponding environment variables. The only exception is {@code CLASSPATH}, which is a
   * standard Java convention and does not use the {@code PAL_} prefix.
   */
  private void setEmptyParamsFromEnv() {
    classpath = getParameter("CLASSPATH", classpath);
    kafkaServers = getParameter("PAL_KAFKA_SERVERS", kafkaServers);
    name = getParameter("PAL_PEER_NAME", name);
    String uuidString = getParameter("PAL_PEER_UUID", uuid == null ? null : uuid.toString());
    uuid = uuidString == null ? null : UUID.fromString(uuidString);
    log = getParameter("PAL_LOG", log);
    sourceLog = getParameter("PAL_SOURCE_LOG", sourceLog);
    wal = getParameter("PAL_WAL", wal);
    logPrefix = getParameter("PAL_LOG_PREFIX", logPrefix);
    chronicleBaseDir = getParameter("PAL_CHRONICLE_BASE_DIR", chronicleBaseDir);
    zmqRpc = getParameter("PAL_ZMQ_RPC", zmqRpc);
    jsonRpc = getParameter("PAL_JSON_RPC", jsonRpc);
    tcpPub = getParameter("PAL_TCP_PUB", tcpPub);

    if (kafkaConnectTimeout == null) {
      kafkaConnectTimeout = getIntEnv("PAL_KAFKA_TIMEOUT_MS");
    }
    if (etcdConnectTimeout == null) {
      etcdConnectTimeout = getIntEnv("PAL_ETCD_TIMEOUT_MS");
    }
    if (inFlightTracking == null) {
      inFlightTracking = getBoolEnv("PAL_IN_FLIGHT_TRACKING");
    }
    if (drainTimeoutMs == null) {
      drainTimeoutMs = getIntEnv("PAL_DRAIN_TIMEOUT_MS");
    }
    if (callbackTimeoutMs == null) {
      callbackTimeoutMs = getIntEnv("PAL_CALLBACK_TIMEOUT_MS");
    }
    if (!fxThread) {
      Boolean fxEnv = getBoolEnv("PAL_FX_THREAD");
      if (fxEnv != null && fxEnv) {
        fxThread = true;
      }
    }
    serviceThreadPattern = getParameter("PAL_SERVICE_THREAD", serviceThreadPattern);

    exceptionPolicy = getParameter("PAL_EXCEPTION_POLICY", exceptionPolicy);
    checkedExceptionPolicy = getParameter("PAL_CHECKED_EXCEPTION_POLICY", checkedExceptionPolicy);

    // RPC configuration
    if (rpcThreads == null || rpcThreads == 1) {
      Integer envRpcThreads = getIntEnv("PAL_RPC_THREADS");
      if (envRpcThreads != null) {
        rpcThreads = envRpcThreads;
      }
    }
    rpcDefaultAction = getParameter("PAL_RPC_DEFAULT_ACTION", rpcDefaultAction);
    rpcPolicyPath = getParameter("PAL_RPC_POLICY", rpcPolicyPath);
    rpcPolicyPresets = getParameter("PAL_RPC_POLICY_PRESET", rpcPolicyPresets);
    if (rpcPolicyWatchInterval == null) {
      rpcPolicyWatchInterval = getIntEnv("PAL_RPC_POLICY_WATCH_INTERVAL");
    }

    // WAL incoming flags
    Boolean walIncomingRpcEnv = getBoolEnv("PAL_WAL_INCOMING_RPC");
    if (walIncomingRpcEnv != null) {
      walIncomingRpc = walIncomingRpcEnv;
    }
    Boolean walAllIncomingRpcEnv = getBoolEnv("PAL_WAL_ALL_INCOMING_RPC");
    if (walAllIncomingRpcEnv != null) {
      walAllIncomingRpc = walAllIncomingRpcEnv;
    }
    Boolean walIncomingCliEnv = getBoolEnv("PAL_WAL_INCOMING_CLI");
    if (walIncomingCliEnv != null) {
      walIncomingCli = walIncomingCliEnv;
    }

    // interceptable and source context
    if (!interceptable) {
      Boolean interceptableEnv = getBoolEnv("PAL_INTERCEPTABLE");
      if (interceptableEnv != null && interceptableEnv) {
        interceptable = true;
      }
    }
    if (!includeSourceContext) {
      Boolean srcCtxEnv = getBoolEnv("PAL_WITH_SOURCE_CONTEXT");
      if (srcCtxEnv != null && srcCtxEnv) {
        includeSourceContext = true;
      }
    }

    // Resolve PAL directory: inherited -d option (via parent Pal command) → ENV → null
    if (palDirectoryUrl == null || palDirectoryUrl.trim().isEmpty()) {
      String palDirectoryEnvVar = System.getenv("PAL_DIRECTORY");
      palDirectoryEnvVar = palDirectoryEnvVar != null ? palDirectoryEnvVar.trim() : null;
      if (palCommand != null
          && !Arrays.asList(palDirectoryEnvVar, PalDirectory.NO_URL)
              .contains(palCommand.getPalDirectoryConnectionString())) {
        palDirectoryUrl = palCommand.getPalDirectoryConnectionString();
      } else {
        palDirectoryUrl = palDirectoryEnvVar;
      }
    }
  }

  /**
   * Validates input parameters and command-line arguments, sets derived runtime options, and
   * outputs warnings or errors when configuration inconsistencies are detected.
   */
  private void validateInput() {

    // set argList
    if (jarFile != null) { // if -jar, all positional parameters are considered its args
      argList = cmdArgList;
    } else if (cmdArgList != null) { // else, first is considered the mainClass
      className = cmdArgList.get(0);
      argList = cmdArgList.subList(1, cmdArgList.size());
    }

    // warn if as-service flag does not apply
    if (asService && (className == null && jarFile == null)) {
      System.err.println("NOTE: -s (--as-service) option only relevant with mainClass or -jar.");
    }

    // verify and set run options
    runOptions = EnumSet.noneOf(RunOptions.class);

    if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
      // warn of incompatible options that will be ignored
      if (name != null) {
        System.err.println(
            "WARNING: --name only applicable if registering with a <pal_directory>.");
        logger.warn("--name given, but not <pal_directory>; will be ignored.");
      }
      if (interceptable) {
        System.err.println(
            "WARNING: --interceptable only applicable if registering with a <pal_directory>.");
        logger.warn("--interceptable given, but not <pal_directory>; will be ignored.");
      }
    } else {
      runOptions.add(RunOptions.WITH_PALDIR);
    }

    // Only require Kafka servers if we're actually using Kafka (not Chronicle queues)
    if (kafkaServers == null && (log != null || sourceLog != null || wal != null)) {
      boolean usesKafka =
          (log != null && !isChronicleLog(log))
              || (sourceLog != null && !isChronicleLog(sourceLog))
              || (wal != null && !isChronicleLog(wal));

      if (usesKafka) {
        fatalExit(null, PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN);
      }
    }

    if (log != null) {
      // if logName is given, assign to both source and WAL
      if (sourceLog != null || wal != null) {
        System.err.println(
            "WARNING: with --log (LOG), --source-log (SOURCE_LOG) and"
                + " --wal (WAL) options are ignored.");
      }
      sourceLog = wal = log;
    }

    if (sourceLog != null) {
      runOptions.add(RunOptions.WITH_SOURCE_LOG);
    }

    if (wal != null) {
      runOptions.add(RunOptions.WITH_WAL);
    }

    if (replayWalPath != null) {
      if (wal != null || sourceLog != null || log != null) {
        fatalExit(
            null,
            PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
            "ERROR: --replay-wal is mutually exclusive with --wal, --source-log, and --log.");
      }
      if (!isChronicleLog(replayWalPath) && kafkaServers == null) {
        fatalExit(
            null,
            PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
            "ERROR: --replay-wal with a Kafka topic requires --kafka-servers (-k).");
      }
      runOptions.add(RunOptions.WITH_REPLAY);
      properties.setProperty("replay.wal.path", replayWalPath);
      properties.setProperty("replay.divergence.policy", replayDivergencePolicy);
      properties.setProperty("replay.threading", replayThreading);
      properties.setProperty("replay.delay", replayDelay);
      if (kafkaServers != null) {
        properties.setProperty("replay.kafka.servers", kafkaServers);
      }
      if (replayPolicyPath != null) {
        properties.setProperty("replay.policy.path", replayPolicyPath);
      }
      properties.setProperty("replay.shield.io", String.valueOf(replayShieldIo));
      properties.setProperty("replay.shield.fx", String.valueOf(replayShieldFx));
      if (replayReExecutePatterns != null) {
        properties.setProperty(
            "replay.re-execute.patterns", String.join(",", replayReExecutePatterns));
      }
      if (replayStubPatterns != null) {
        properties.setProperty("replay.stub.patterns", String.join(",", replayStubPatterns));
      }
      properties.setProperty("replay.stub.all.else", String.valueOf(replayStubAllElse));
      properties.setProperty("replay.force.stub", String.valueOf(replayForceStub));
    }

    // Validate Kafka/Chronicle flag consistency
    validateLogBackendConsistency();

    if (tcpPub != null) {
      runOptions.add(RunOptions.WITH_TCP_PUB);
    }

    if (walIncomingRpc || walAllIncomingRpc) {
      if (runOptions.contains(RunOptions.WITH_WAL)
          || runOptions.contains(RunOptions.WITH_TCP_PUB)) {
        runOptions.add(RunOptions.WITH_WAL_INCOMING_RPC);
      }
    }
    if (walAllIncomingRpc) {
      if (runOptions.contains(RunOptions.WITH_WAL)
          || runOptions.contains(RunOptions.WITH_TCP_PUB)) {
        runOptions.add(RunOptions.WITH_WAL_ALL_INCOMING_RPC);
      }
    }
    if (walIncomingCli) {
      if (runOptions.contains(RunOptions.WITH_WAL)
          || runOptions.contains(RunOptions.WITH_TCP_PUB)) {
        runOptions.add(RunOptions.WITH_WAL_INCOMING_CLI);
      }
    }

    // ensure that if offset was given, a log name to read from was also given
    if (startOffset != null && (sourceLog == null || sourceLog.equalsIgnoreCase("auto"))) {
      fatalExit(null, PeerException.FatalCode.ERROR_NO_LOG_GIVEN);
    }

    if (runOptions.contains(RunOptions.WITH_PALDIR) && interceptable) {
      runOptions.add(RunOptions.WITH_INTERCEPTS);
    }

    if (zmqRpc != null) {
      runOptions.add(RunOptions.WITH_ZMQ_RPC);
    }

    if (jsonRpc != null) {
      runOptions.add(RunOptions.WITH_JSON_RPC);
    }

    // enable sessions if any RPC interface is enabled
    if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)
        || runOptions.contains(RunOptions.WITH_JSON_RPC)
        || runOptions.contains(RunOptions.WITH_SOURCE_LOG)) {
      runOptions.add(RunOptions.WITH_SESSIONS);
    }

    // enable in-flight tracking if configured (defaults to true if not explicitly set)
    if (inFlightTracking == null || inFlightTracking) {
      runOptions.add(RunOptions.WITH_IN_FLIGHT_TRACKING);
    }

    logger.info("Running with options: {}", runOptions);
  }

  /**
   * Finds and returns an available TCP port on the host.
   *
   * @return an open TCP port number
   * @throws IOException if an error occurs while attempting to open a socket
   */
  private static int findOpenPort() throws IOException {
    try (ServerSocket tmpSocket = new ServerSocket(0, 0)) {
      return tmpSocket.getLocalPort();
    }
  }

  /**
   * Determines if a log specification refers to a Chronicle queue.
   *
   * @param logSpec the log specification (e.g., "file:/tmp/mylog", "file:mylog", or
   *     "my-kafka-topic")
   * @return true if it's a Chronicle queue (starts with "file:"), false otherwise
   */
  private static boolean isChronicleLog(@Nullable String logSpec) {
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

  /**
   * Validates that Kafka and Chronicle flags are used consistently with the configured log
   * backends.
   *
   * <p>Detects and reports the following inconsistencies:
   *
   * <ul>
   *   <li>{@code --kafka-servers} given but all configured logs use Chronicle (file: paths)
   *   <li>{@code --chronicle-base-dir} given but all configured logs use Kafka
   *   <li>{@code --source-log} and {@code --wal} use different backends (one Kafka, one Chronicle)
   * </ul>
   *
   * <p>For replay mode ({@code --replay-wal}), checks are performed against the replay WAL path
   * instead of the regular source-log and WAL.
   */
  private void validateLogBackendConsistency() {
    if (replayWalPath != null) {
      // Replay mode: check replayWalPath backend vs flags
      if (kafkaServers != null && isChronicleLog(replayWalPath)) {
        fatalExit(
            null,
            PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
            "ERROR: --kafka-servers (-k) given but --replay-wal uses a Chronicle queue"
                + " (file: path). Remove --kafka-servers or use a Kafka topic name for"
                + " --replay-wal.");
      }
      if (chronicleBaseDir != null
          && !chronicleBaseDir.isBlank()
          && !isChronicleLog(replayWalPath)) {
        fatalExit(
            null,
            PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
            "ERROR: --chronicle-base-dir given but --replay-wal uses a Kafka topic."
                + " Remove --chronicle-base-dir or use a file: path for --replay-wal.");
      }
    } else {
      // Non-replay mode: check sourceLog and wal
      boolean anyLog = sourceLog != null || wal != null;
      if (anyLog) {
        boolean anyKafkaLog =
            (sourceLog != null && !isChronicleLog(sourceLog))
                || (wal != null && !isChronicleLog(wal));
        boolean anyChronicleLog =
            (sourceLog != null && isChronicleLog(sourceLog))
                || (wal != null && isChronicleLog(wal));

        if (kafkaServers != null && !anyKafkaLog) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
              "ERROR: --kafka-servers (-k) given but all configured logs use Chronicle"
                  + " (file: paths). Remove --kafka-servers or use Kafka topic names for"
                  + " --source-log/--wal.");
        }

        if (chronicleBaseDir != null && !chronicleBaseDir.isBlank() && !anyChronicleLog) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES,
              "ERROR: --chronicle-base-dir given but all configured logs use Kafka."
                  + " Remove --chronicle-base-dir or use file: paths for --source-log/--wal.");
        }

        if (sourceLog != null && wal != null && isChronicleLog(sourceLog) != isChronicleLog(wal)) {
          System.err.println(
              "WARNING: source-log and WAL use different backends"
                  + " (one is Kafka, the other is Chronicle). Verify this is intentional.");
        }
      }
    }
  }

  /**
   * Adds miscellaneous properties to the application configuration.
   *
   * <p>This method sets the peer's UUID, directory URL, Kafka servers, and messaging addresses (TCP
   * PUB, RPC, JSONRPC) based on the current configuration and environment.
   */
  private void addMiscProperties() {
    // set this peer's UUID if given from param or ENV, otherwise create random UUID
    if (uuid == null) {
      final String envUuid = System.getenv("PAL_PEER_UUID");
      if (envUuid != null) {
        uuid = UUID.fromString(envUuid.trim());
      } else {
        uuid = UUID.randomUUID();
      }
    }
    properties.put("id", uuid.toString());

    // add Directory url to app properties
    properties.setProperty(
        "paldir_url",
        palDirectoryUrl == null || palDirectoryUrl.isEmpty()
            ? PalDirectory.NO_URL
            : palDirectoryUrl);

    // add kafka servers if given
    if (kafkaServers != null) {
      properties.setProperty("kafka.bootstrap.servers", kafkaServers);
    }

    // add kafka connection timeout
    if (kafkaConnectTimeout != null) {
      properties.setProperty("kafka.connect.timeout.ms", String.valueOf(kafkaConnectTimeout));
    }

    // add etcd connect timeout for DirectoryConnectionProvider injection
    if (etcdConnectTimeout != null) {
      properties.setProperty("etcd.connect.timeout.ms", String.valueOf(etcdConnectTimeout));
    }

    // add log (kafka topic) prefix
    if (logPrefix != null && !logPrefix.isBlank()) {
      properties.setProperty("logPrefix", logPrefix);
    }

    // add chronicle base directory (only if explicitly provided via CLI or ENV)
    if (chronicleBaseDir != null && !chronicleBaseDir.isBlank()) {
      properties.setProperty("wal.chronicle.base_dir", chronicleBaseDir);
    }
    // If not set, ChronicleSourceLogReader and ChronicleWalWriter will use CWD for relative paths

    // Determine and set source log type (KAFKA or CHRONICLE)
    if (sourceLog != null) {
      if (isChronicleLog(sourceLog)) {
        properties.setProperty("source_log.type", "CHRONICLE");
        String sourcePath = extractLogName(sourceLog);
        // For Chronicle queues, store the path for use by ChronicleSourceLogReader
        if (!sourcePath.startsWith("/")) {
          // Relative path - use base_dir if configured
          if (!properties.containsKey("wal.chronicle.base_dir")) {
            logger.warn(
                "Chronicle source log uses relative path but wal.chronicle.base_dir not set");
          }
        }
      } else {
        properties.setProperty("source_log.type", "KAFKA");
      }
    }

    // Determine and set WAL type (KAFKA or CHRONICLE)
    if (wal != null) {
      if (isChronicleLog(wal)) {
        properties.setProperty("wal.type", "CHRONICLE");
        String walPath = extractLogName(wal);
        // For absolute paths, extract base directory
        if (walPath.startsWith("/")) {
          Path p = Paths.get(walPath);
          Path parent = p.getParent();
          // Only set base_dir if not already configured and parent exists
          if (parent != null && !properties.containsKey("wal.chronicle.base_dir")) {
            properties.setProperty("wal.chronicle.base_dir", parent.toString());
          }
        }
      } else {
        properties.setProperty("wal.type", "KAFKA");
      }
    }

    // are we publishing via TCP, or just internally
    if (tcpPub != null) {
      int port = 0;
      String hostname = ZmqProperties.DEFAULT_PUB_HOSTNAME;
      if (tcpPub.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for PUB");
        }
      } else if (tcpPub.contains(":")) {
        hostname = Strings.stringBefore(tcpPub, ":");
        port = Integer.parseInt(Strings.stringAfter(tcpPub, ":"));
      } else {
        port = Integer.parseInt(tcpPub);
      }
      properties.setProperty(ZmqProperties.OUT_PUB_CHANNEL, format("tcp://%s:%d", hostname, port));
    } else {
      properties.setProperty(
          ZmqProperties.OUT_PUB_CHANNEL,
          ZmqProperties.inprocEndpoints.getProperty("out.pub.inproc"));
    }

    // are we listening for RPC requests
    if (zmqRpc != null) {
      String hostname = ZmqProperties.DEFAULT_ZMQ_RPC_HOSTNAME;
      int port = 0;
      if (zmqRpc.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for ZMQ-RPC");
        }
      } else {
        final String portStr;
        if (zmqRpc.contains(":")) {
          hostname = Strings.stringBefore(zmqRpc, ":");
          portStr = Strings.stringAfter(zmqRpc, ":");
        } else {
          portStr = zmqRpc;
        }
        try {
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
          fatalExit(e, PeerException.FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER);
        }
      }
      properties.setProperty("in.zmq.rpc", format("tcp://%s:%d", hostname, port));
      properties.setProperty("rpc.threadPoolSize", String.valueOf(rpcThreads));
    }

    // are we listening for JSONRPC requests
    if (jsonRpc != null) {
      String hostname = ZmqProperties.DEFAULT_JSONRPC_HOSTNAME;
      int port = 0;
      if (jsonRpc.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for JSON-RPC");
        }
      } else {
        final String portStr;
        if (jsonRpc.contains(":")) {
          hostname = Strings.stringBefore(jsonRpc, ":");
          portStr = Strings.stringAfter(jsonRpc, ":");
        } else {
          portStr = jsonRpc;
        }
        try {
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
          fatalExit(e, PeerException.FatalCode.ERROR_PARSING_JSON_RPC_PORT_NUMBER);
        }
      }
      properties.setProperty("in.json.rpc", format("ws://%s:%d", hostname, port));
      properties.setProperty("rpc.threadPoolSize", String.valueOf(rpcThreads));
    }

    // message content options
    properties.setProperty("messages.with_src_context", String.valueOf(includeSourceContext));

    // rpc policy options
    if (rpcPolicyPath != null) {
      properties.setProperty("rpc.policy.path", rpcPolicyPath);
    }
    if (rpcPolicyPresets != null) {
      properties.setProperty("rpc.policy.presets", rpcPolicyPresets);
    }
    if (rpcDefaultAction != null) {
      properties.setProperty("rpc.default_action", rpcDefaultAction);
    }
    if (rpcPolicyWatchInterval != null) {
      properties.setProperty(
          "rpc.policy.watch.interval.ms", String.valueOf(rpcPolicyWatchInterval));
    }
    // in-flight tracking options
    if (drainTimeoutMs != null) {
      properties.setProperty("intercept.drain.timeout.ms", String.valueOf(drainTimeoutMs));
    }
    if (callbackTimeoutMs != null) {
      properties.setProperty("intercept.callback.timeout.ms", String.valueOf(callbackTimeoutMs));
    }

    // fx thread execution
    properties.setProperty("execution.fx.thread.enabled", String.valueOf(fxThread));

    // service thread affinity (CDI request context wrapping)
    if (serviceThreadPattern != null) {
      properties.setProperty("execution.service.thread.pattern", serviceThreadPattern);
    }

    // source-log and WAL same-log determination (circularity guard for incoming WAL writes)
    properties.setProperty(
        "log.sourceAndWalAreSameLog", String.valueOf(Objects.equals(sourceLog, wal)));

    // exception policy configuration
    if (exceptionPolicy != null && !exceptionPolicy.isBlank()) {
      properties.setProperty("pal.intercept.exception-policy.default", exceptionPolicy.trim());
    }
    if (checkedExceptionPolicy != null && !checkedExceptionPolicy.isBlank()) {
      properties.setProperty(
          "pal.intercept.checked-exception-policy.default", checkedExceptionPolicy.trim());
    }

    // recording scope options
    if (scopePatterns != null) {
      properties.setProperty("scope.patterns", String.join(",", scopePatterns));
    }
    if (scopeExcludePatterns != null) {
      properties.setProperty("scope.exclude.patterns", String.join(",", scopeExcludePatterns));
    }
    if (scopeIo) {
      properties.setProperty("scope.io", "true");
    }
    if (scopePolicyPath != null) {
      properties.setProperty("scope.policy.path", scopePolicyPath);
    }
    if (scopeDefaultAction != null) {
      properties.setProperty("scope.default.action", scopeDefaultAction);
    }
  }

  /**
   * Returns the JMX address in "host:port" form, or {@code null} if JMX is disabled /
   * mis-configured.
   */
  private @Nullable String getJmxAddress() {
    // Quick exit if explicitly disabled
    if ("false".equalsIgnoreCase(System.getProperty("com.sun.management.jmxremote"))) {
      return null;
    }

    // Resolve PORT  – property → PAL_ env
    Integer port = null;
    String portStr = System.getProperty("com.sun.management.jmxremote.port");
    if (portStr == null || portStr.isEmpty()) {
      portStr = System.getenv("PAL_JMX_PORT");
    }
    if (portStr != null && !portStr.isEmpty()) {
      try {
        port = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        logger.warn("Invalid JMX port - JMX not configured", e);
      }
    }

    // Resolve HOST – property → PAL_ env → local-only default
    String host = System.getProperty("java.rmi.server.hostname");
    if (host == null || host.isEmpty()) {
      host = System.getenv("PAL_JMX_HOST");
      if (host == null || host.isEmpty()) {
        // local-only defaults to TRUE when the prop is absent
        String localOnly = System.getProperty("com.sun.management.jmxremote.local.only");
        boolean local = !"false".equalsIgnoreCase(localOnly);
        if (local) {
          host = "localhost";
        }
      }
    }

    // Assemble
    return (host != null && port != null) ? host + ':' + port : null;
  }

  /**
   * Creates and configures a custom classloader using the provided classpath entries and JAR file.
   *
   * <p>The custom classloader is used for dynamic class loading during peer execution.
   */
  private void createCustomClassloader() {
    List<URL> urls = new ArrayList<>();
    if (classpath != null) {
      // colon-separated list of classpath entries where each is either a folder or a JAR file
      Arrays.stream(classpath.split(":"))
          .map(File::new)
          .forEach(
              f -> {
                try {
                  urls.add(f.toURI().toURL());
                } catch (MalformedURLException ex) {
                  logger.error("Error adding classpath entry as URL for custom classloader", ex);
                }
              });
    }
    if (jarFile != null) {
      try {
        urls.add(new File(jarFile).toURI().toURL());
      } catch (MalformedURLException ex) {
        logger.error("Error adding JAR file as URL for custom classloader", ex);
      }
    }

    this.customClassloader =
        new CustomClassloader(
            urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
  }

  /**
   * Registers the source and write-ahead Log configurations to be used by this peer with the Pal
   * directory.
   *
   * @param injector the Guice injector used to obtain the DirectoryConnectionProvider
   * @param self the PeerInfo instance representing this peer
   * @param sourceLog the source log configuration (can be null)
   * @param writeAheadLog the write-ahead log configuration (can be null)
   */
  private void registerLogsToUse(
      Injector injector, PeerInfo self, LogInfo sourceLog, LogInfo writeAheadLog) {
    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    try {
      if (sourceLog != null) {
        palDirectory.setSourceLog(self, sourceLog, peerLease);
      }
      if (writeAheadLog != null) {
        palDirectory.setWalLog(self, writeAheadLog, peerLease);
      }
    } catch (EtcdUnavailableException ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_UNREACHABLE_ETCD);
    } catch (Exception ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_SELF_LOGS);
    }
  }

  /**
   * Registers this peer with the PAL directory.
   *
   * <p>A new PeerInfo instance is created, configured with available network interfaces and
   * addresses, and then registered with the PAL directory.
   *
   * @param injector the Guice injector used for obtaining necessary dependencies
   * @return the registered PeerInfo instance representing this peer
   */
  private PeerInfo registerSelfAsPeer(Injector injector) {
    PeerInfo self = null;

    // register self as new peer
    try {
      final PalDirectory palDirectory =
          injector
              .getInstance(DirectoryConnectionProvider.class)
              .get()
              .orElseThrow(RuntimeException::new);
      self = new PeerInfo(uuid);
      // public listening interfaces
      if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)) {
        self.setZmqRpcAddress(properties.getProperty("in.zmq.rpc"));
      }
      if (runOptions.contains(RunOptions.WITH_JSON_RPC)) {
        self.setJsonrpcAddress(properties.getProperty("in.json.rpc"));
      }
      if (properties
          .getProperty(ZmqProperties.OUT_PUB_CHANNEL)
          .startsWith("tcp://")) { // only register PUB address if over TCP
        self.setPubAddress(properties.getProperty(ZmqProperties.OUT_PUB_CHANNEL));
      }
      String jmxAddress = getJmxAddress();
      if (jmxAddress != null) {
        self.setJmxAddress(jmxAddress);
      }
      // other info
      if (name != null) {
        self.setName(name);
      }
      palDirectory.createPeer(self);
      long keepAliveSecs =
          Long.parseLong(
              properties.getProperty(
                  "peer.keepalive.seconds", String.valueOf(PEER_KA_SECS_DEFAULT)));
      peerLease = palDirectory.createPeerLease(self.getUuid(), keepAliveSecs);
    } catch (EtcdUnavailableException ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_UNREACHABLE_ETCD);
    } catch (Exception ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_SELF);
    }
    logger.info("Registered self in directory with new PeerInfo: {}", self);
    return self;
  }

  /**
   * Creates a set of managed services based on the enabled runtime options.
   *
   * <p>Services may include Log reader/writer, message publisher, BIN-RPC and JSON-RPC dispatchers,
   * intercept matcher, and a session service if required.
   *
   * @param injector the Guice injector used to obtain service instances
   * @return a Set of Service instances to be managed
   */
  private Set<Service> createManagedServices(Injector injector) {
    final Set<Service> services = new HashSet<>();
    boolean sessionsRequired = false;

    if (runOptions.contains(RunOptions.WITH_SOURCE_LOG)) {
      services.add(injector.getInstance(SourceLogReader.class));
      sessionsRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_WAL)) {
      services.add(injector.getInstance(WalWriter.class));
    }
    if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
      services.add(injector.getInstance(MessagePublisher.class));
    }
    if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)) {
      services.add(injector.getInstance(ZmqRpcServer.class));
      sessionsRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_JSON_RPC)) {
      services.add(injector.getInstance(JsonRpcRequestServer.class));
      sessionsRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
      services.add(injector.getInstance(InterceptMatcher.class));
    }
    if (sessionsRequired) {
      services.add(injector.getInstance(SessionService.class));
    }
    return services;
  }

  /**
   * Creates and configures a ServiceManager to manage the given services.
   *
   * <p>The manager listens for state changes, logs service startup times, and triggers fatal exit
   * on failure.
   *
   * @param services an iterable collection of Service instances to be managed
   * @return the configured ServiceManager instance
   */
  private ServiceManager createServiceManager(Iterable<Service> services) {
    final ServiceManager manager = new ServiceManager(services);
    manager.addListener(
        new ServiceManager.Listener() {
          @Override
          public void stopped() {
            logger.info("Service manager stopped.");
          }

          @Override
          public void healthy() {
            logger.info("Managed services ready");
            manager
                .startupTimes()
                .forEach((key, value) -> logger.info("Service '{}' started in {} ms", key, value));
          }

          @Override
          public void failure(@Nonnull Service service) {
            fatalExit(service.failureCause(), PeerException.FatalCode.ERROR_SERVICE_MANAGER_FAILED);
          }
        },
        MoreExecutors.directExecutor());
    return manager;
  }

  /**
   * Shuts down managed services, executors, and associated resources.
   *
   * <p>This includes stopping messaging executors, unregistering the peer from the directory,
   * closing custom classloader executors, and shutting down the ZeroMQ context.
   *
   * @param manager the ServiceManager instance managing the services (can be null)
   * @param injector the Guice injector used to obtain service instances for shutdown procedures
   */
  @SuppressWarnings("UnsafeWildcard")
  private void shutdown(ServiceManager manager, Injector injector) {
    if (logger.isInfoEnabled()) {
      logger.info("Shutting down...");
    }
    // stop RPC policy file watcher (if running)
    if (rpcPolicyFileWatcher != null) {
      rpcPolicyFileWatcher.stop();
    }

    ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
    try {
      // stop services
      if (manager != null) {
        manager.stopAsync();
      }

      // stop peer executor (interrupts all peer exec threads)
      if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)
          || runOptions.contains(RunOptions.WITH_JSON_RPC)) {
        final ThreadPool rpcMessageExecutor = injector.getInstance(SocketRpcExecutor.class);
        rpcMessageExecutor.shutdown();
        logger.info("Done shutting down peer threads");
      }

      // stop log executor (interrupts all log exec threads)
      if (runOptions.contains(RunOptions.WITH_SOURCE_LOG)) {
        final ThreadPool logMessageExecutor = injector.getInstance(LogRpcExecutor.class);
        logMessageExecutor.shutdown();
        logger.info("Done shutting down log threads");
      }

      // asynchronously shutdown custom classloader notifications executor
      singleExecutor.execute(() -> customClassloader.shutdown());

      // unregister self and close connection to paldir
      if (runOptions.contains(RunOptions.WITH_PALDIR)) {
        final Optional<PalDirectory> palDirectory =
            injector.getInstance(DirectoryConnectionProvider.class).get();
        palDirectory.ifPresent(
            dir -> {
              try {
                peerLease.close(); // revoke + stop keep-alive
                dir.deletePeer(this.uuid);
              } catch (Exception e) {
                logger.warn("Error unregistering self from PAL directory.", e);
              }
              dir.close();
            });
      }

      // close sockets that aren't automatically closed
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
        final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
        interceptInformer.closeThreadLocalSocket();
      }

      // close zmq context asynchronously
      singleExecutor.execute(this::closeZmqContext);
      singleExecutor.shutdown();

      // wait a bit for services to stop
      if (manager != null) {
        manager.awaitStopped(SERVICE_MANAGER_AWAIT_TERM);
      }

      // wait a bit for exec service to finish closing zmq context
      boolean terminated =
          singleExecutor.awaitTermination(EXECUTOR_AWAIT_TERM.toMillis(), TimeUnit.MILLISECONDS);
      if (!terminated && logger.isDebugEnabled()) {
        logger.debug("Executor service did not terminate gracefully.");
      }

      // clear queues - just to be nice
      if (runOptions.contains(RunOptions.WITH_WAL)) {
        // NOTE: explicit type required for errorprone (do not replace by <>)
        Key<HwmMessageQueue<OutboundMsg>> walKey =
            Key.get(new TypeLiteral<HwmMessageQueue<OutboundMsg>>() {}, Names.named("wal_queue"));
        HwmMessageQueue<OutboundMsg> walQueue = injector.getInstance(walKey);
        walQueue.clear();
        if (logger.isDebugEnabled()) {
          logger.debug("Cleared internal WAL queue");
        }
      }
      if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
        // NOTE: explicit type required for errorprone (do not replace by <>)
        Key<HwmMessageQueue<OutboundMsg>> pubKey =
            Key.get(new TypeLiteral<HwmMessageQueue<OutboundMsg>>() {}, Names.named("pub_queue"));
        HwmMessageQueue<OutboundMsg> pubQueue = injector.getInstance(pubKey);
        pubQueue.clear();
        if (logger.isDebugEnabled()) {
          logger.debug("Cleared internal PUB queue");
        }
      }

      // print aggregated stats
      OutboundMessageGateway outboundMessageGateway =
          injector.getInstance(OutboundMessageGateway.class);
      outboundMessageGateway.printAggregateStats();

      // in case we're running asService and manager == null
      if (manager == null) {
        runAsServiceLatch.countDown();
      }
    } catch (TimeoutException ie) {
      logger.warn("Timeout exception in shutdown hook", ie);
    } catch (InterruptedException e) {
      logger.warn("Interrupted while shutting down", e);
      Thread.currentThread().interrupt();
    } finally {
      logger.info("This peer is done! bye");
    }
  }

  /**
   * Waits for a specified number of "go!" signals on the synchronization socket.
   *
   * <p>Only messages equal to "go!" (case-insensitive) are counted; any unexpected messages are
   * logged and ignored.
   *
   * @param numberOfSignals the number of "go!" signals to wait for
   */
  private void collectGoSignals(int numberOfSignals) {
    CountDownLatch latch = new CountDownLatch(numberOfSignals);
    while (latch.getCount() > 0) {
      String received = syncSocket.recvStr();
      if (received.equalsIgnoreCase("go!")) {
        latch.countDown();
      } else {
        logger.warn("ignoring unexpected msg: '{}'", received);
      }
    }
    syncSocket.close();
  }

  /**
   * Main entry point of the peer application.
   *
   * <p>Parses command-line arguments using Picocli and executes the Main callable. The application
   * exits with the return code from the executed main class or JAR.
   *
   * @param args command-line arguments
   */
  public static void main(final String[] args) {
    CommandLine commandLine = new CommandLine(new Main());
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }

  /**
   * Executes the peer startup sequence.
   *
   * <p>This method performs logging setup, property loading, ZeroMQ context initialization,
   * dependency injection, service registration, and optionally self-executes a main class or JAR
   * file. It returns the exit code resulting from the main class or JAR execution.
   *
   * @return the exit code from executing the main class or JAR file
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  @Override
  public Integer call() throws InterruptedException {

    initLogging();
    if (logger.isDebugEnabled()) {
      var parseResult = spec.commandLine().getParseResult();
      if (parseResult != null) {
        List<String> rawArgs = parseResult.originalArgs();
        // print all args excluding the 'run' subcommand
        logger.debug("Starting peer with args: {}", rawArgs.subList(1, rawArgs.size()));
      }
    }

    // for async calls
    final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();

    // validate CLI args and ENV vars
    setEmptyParamsFromEnv();
    validateInput();

    // load and validate properties
    loadProps();
    validateProperties();

    // initialize ZMQ and local sockets
    initZmqContext();

    // add zmq channel names to properties
    properties.putAll(ZmqProperties.inprocEndpoints);

    // add misc variables to app props
    addMiscProperties();

    // init custom classloader
    createCustomClassloader();

    // tell our resource bundle control provider to use our custom classloader
    DynamicResourceBundleControlProvider.setClassLoaderResolver(
        (baseName, locale) -> customClassloader);

    // inject dependencies
    final Injector injector =
        Guice.createInjector(new PeerWiring(properties, runOptions, zmqContext, customClassloader));

    // preflight PAL directory connectivity (if configured) BEFORE Kafka/log initialization
    if (runOptions.contains(RunOptions.WITH_PALDIR)) {
      try {
        @SuppressWarnings("unused")
        var unused =
            injector
                .getInstance(DirectoryConnectionProvider.class)
                .get()
                .orElseThrow(() -> new RuntimeException("Failed to get PAL directory connection"));
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_UNREACHABLE_ETCD);
      }
    }

    // init logs IO
    LogConfigurator logConfigurator = null;
    if (runOptions.contains(RunOptions.WITH_SOURCE_LOG)
        || runOptions.contains(RunOptions.WITH_WAL)) {
      try {
        logConfigurator =
            new LogConfigurator(sourceLog, startOffset, wal, properties, injector, true);
        logConfigurator.init();
      } catch (EtcdUnavailableException ex) {
        // If etcd is unreachable during log registration (e.g., when using --dir),
        // report the dedicated etcd error code instead of a generic logs init error.
        fatalExit(ex, PeerException.FatalCode.ERROR_UNREACHABLE_ETCD);
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS, ex.getMessage());
      }
    }

    // register self in directory
    final CountDownLatch selfRegistrationLatch = new CountDownLatch(1);
    if (runOptions.contains(RunOptions.WITH_PALDIR)) {
      final LogInfo peerSourceLog =
          logConfigurator != null ? logConfigurator.getSourceLog().orElse(null) : null;
      final LogInfo peerWriteAheadLog =
          logConfigurator != null ? logConfigurator.getWriteAheadLog().orElse(null) : null;
      singleExecutor.execute(
          () -> {
            PeerInfo self = registerSelfAsPeer(injector);
            registerLogsToUse(injector, self, peerSourceLog, peerWriteAheadLog);
            selfRegistrationLatch.countDown();
          });
    }

    // set up managed services
    final Set<Service> services = createManagedServices(injector);
    final ServiceManager manager = !services.isEmpty() ? createServiceManager(services) : null;

    // add shutdown hook
    //
    // The hook also prints the replay divergence report: when a self-called application blocks the
    // main thread (e.g. a Quarkus server) and the peer is stopped via SIGTERM, the main-thread
    // post-replay path never runs, so the report must be emitted from here. An AtomicBoolean makes
    // the print idempotent across the normal-exit path and the SIGTERM path.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  shutdown(manager, injector);
                  if (runOptions.contains(RunOptions.WITH_REPLAY)) {
                    printReplayDivergenceReportOnce(injector);
                  }
                }));

    // start services
    if (manager != null) { // manager = null if there are no services
      manager.startAsync();
    }

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

    // wait for all services up
    if (manager != null) {
      manager.awaitHealthy();
    }

    // block until we're registered in Directory
    if (runOptions.contains(RunOptions.WITH_PALDIR)) {
      try {
        selfRegistrationLatch.await();
      } finally {
        singleExecutor.shutdownNow();
      }
    }

    logger.info("Peer {} up and running", uuid);

    // start RPC policy file watcher (if configured)
    rpcPolicyFileWatcher = injector.getInstance(Key.get(RpcPolicyFileWatcher.class));
    if (rpcPolicyFileWatcher != null) {
      rpcPolicyFileWatcher.start();
    }

    // start listening to intercept requests
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
      final PalDirectory palDirectory =
          injector
              .getInstance(DirectoryConnectionProvider.class)
              .get()
              .orElseThrow(RuntimeException::new);
      final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
      // add as listener for future requests
      palDirectory.addInterceptListener(interceptInformer);
      // register all current intercepts in directory
      interceptInformer.registerAllInterceptsInDirectory();
    }

    // start accepting Log requests
    if (runOptions.contains(RunOptions.WITH_SOURCE_LOG)) {
      SourceLogReader logMessageReader = injector.getInstance(SourceLogReader.class);
      logMessageReader.acceptRequests(true);
      injector.getInstance(LogRpcExecutor.class).startAllThreads();
    }

    // pre-start threads to create the REP sockets; this must be done after DEALER
    if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)
        || runOptions.contains(RunOptions.WITH_JSON_RPC)) {
      injector.getInstance(SocketRpcExecutor.class).startAllThreads();
    }

    // Set up replay input injectors if replay mode has input threads
    List<Thread> replayInjectorThreads = new ArrayList<>();
    if (runOptions.contains(RunOptions.WITH_REPLAY)) {
      ReplayContext replayContext = injector.getInstance(ReplayContext.class);
      if (replayContext != null) {
        replayInjectorThreads =
            startReplayInputInjectors(replayContext, injector, customClassloader);
      }
    }

    // now call target (main class or JAR file), if given
    boolean mainCalled = false;
    int returnValue = 0;
    if (className != null) {
      // Proactively check class presence to mimic `java` behavior for CNFE
      try {
        Class.forName(className, /* initialize= */ false, customClassloader);
      } catch (ClassNotFoundException e) {
        // Match the standard java launcher error format
        logger.error("Could not find or load main class {}", className, e);
        System.err.printf("Error: Could not find or load main class %s%n", className);
        return EXIT_CLASS_NOT_FOUND;
      }
      // self-call className.main() if given, and then we're done
      try {
        returnValue = injector.getInstance(SelfBootstrapInvoker.class).callMain(className, argList);
      } catch (PeerException e) {
        fatalExit(e);
      }
      mainCalled = true;
    } else if (jarFile != null) { // NOTE: jarFile was previously added to classpath
      // self-call Main-Class found in manifest
      try {
        returnValue = injector.getInstance(SelfBootstrapInvoker.class).callJar(jarFile, argList);
      } catch (PeerException e) {
        fatalExit(e);
      }
      mainCalled = true;
    }

    // Join replay injector threads after self-caller completes
    joinReplayInjectorThreads(replayInjectorThreads);

    // After replay, print divergence report and adjust exit code
    if (mainCalled && runOptions.contains(RunOptions.WITH_REPLAY)) {
      if (printReplayDivergenceReportOnce(injector) && returnValue == EXIT_SUCCESS) {
        returnValue = EXIT_REPLAY_DIVERGENCES;
      }
    }

    if (!mainCalled || asService) {
      if (manager != null) {
        manager.awaitStopped();
      } else {
        runAsServiceLatch.await();
      }
    }
    return returnValue;
  }

  /** Timeout in seconds for joining replay injector threads. */
  private static final long REPLAY_INJECTOR_JOIN_TIMEOUT_SECONDS = 60;

  /**
   * The name of the JavaFX Application Thread as created by the JavaFX runtime. Entry points
   * recorded on this thread require special handling during replay - they must be routed through
   * the real JavaFX thread via {@code Platform.runLater()}, not injected on a fake thread.
   */
  private static final String JAVAFX_APPLICATION_THREAD = "JavaFX Application Thread";

  /**
   * Thread name for the replay injector that handles JavaFX entry points. Uses a distinct name to
   * avoid conflicting with the real JavaFX Application Thread.
   */
  private static final String FX_REPLAY_INJECTOR_THREAD = "pal-fx-replay-injector";

  /**
   * Creates and starts {@link ReplayInputInjector} threads for each non-self-caller thread with
   * entry-point operations in the WAL index.
   *
   * <p>Each injector thread is named to match the WAL thread name (so that {@link
   * io.quasient.pal.core.replay.ReplayCursor} lookup works correctly) and set as a daemon thread. A
   * shared {@link CountDownLatch} is counted down before returning, signaling to all injectors that
   * peer initialization is complete and they can begin injecting entry-point messages.
   *
   * @param replayContext the replay context containing the WAL index and replay gate
   * @param injector the Guice injector for obtaining the {@link IncomingMessageDispatcher}
   * @param classloader the custom classloader to set on injector threads
   * @return the list of started injector threads (empty if no input threads exist)
   */
  private List<Thread> startReplayInputInjectors(
      ReplayContext replayContext, Injector injector, CustomClassloader classloader) {
    WalIndex walIndex = replayContext.getWalIndex();
    Set<String> inputThreadNames = walIndex.getInputThreadNames();
    List<Thread> threads = new ArrayList<>();
    if (inputThreadNames.isEmpty()) {
      return threads;
    }

    IncomingMessageDispatcher dispatcher = injector.getInstance(IncomingMessageDispatcher.class);
    CountDownLatch readyLatch = new CountDownLatch(1);

    for (String threadName : inputThreadNames) {
      // The self-caller thread's entry point (main()) is already invoked by
      // SelfBootstrapInvoker — do not create a duplicate injector for it.
      if ("self-caller".equals(threadName)) {
        continue;
      }

      List<WalEntry> entryPoints = walIndex.getEntryPointsForThread(threadName);
      if (entryPoints.isEmpty()) {
        continue;
      }

      // For JavaFX Application Thread entry points, use a different thread name to avoid
      // conflicting with the real JavaFX thread. The actual execution will be routed to the
      // real FX thread via ThreadAffinityDispatcher (when entry points have threadAffinity
      // = "fx-thread") or via Platform.runLater() through JavaFxInvocationExecutor.
      String injectorThreadName =
          JAVAFX_APPLICATION_THREAD.equals(threadName) ? FX_REPLAY_INJECTOR_THREAD : threadName;

      replayContext.registerInjectorThread(threadName);

      ReplayInputInjector injectorRunnable =
          new ReplayInputInjector(
              threadName,
              entryPoints,
              dispatcher,
              replayContext.getReplayGate(),
              replayContext,
              readyLatch,
              replayContext.getOperationDelayMs(),
              walIndex);

      Thread thread = new Thread(injectorRunnable, injectorThreadName);
      thread.setDaemon(true);
      thread.setContextClassLoader(classloader);
      threads.add(thread);
    }

    // Start all injector threads (they will block on readyLatch)
    for (Thread thread : threads) {
      thread.start();
    }

    logger.info("Started {} replay input injector thread(s)", threads.size());

    // Store the ready latch in ReplayContext. It will be counted down from
    // dispatchIncoming() after the self-caller thread loads the target class,
    // ensuring static initialization runs on the correct thread (self-caller)
    // before any injector thread can trigger class loading.
    replayContext.setInjectorReadyLatch(readyLatch);

    return threads;
  }

  /**
   * Joins replay injector threads with a timeout, logging warnings for threads that do not complete
   * in time or that throw exceptions.
   *
   * @param threads the injector threads to join
   */
  private void joinReplayInjectorThreads(List<Thread> threads) {
    if (threads.isEmpty()) {
      return;
    }

    for (Thread thread : threads) {
      try {
        thread.join(TimeUnit.SECONDS.toMillis(REPLAY_INJECTOR_JOIN_TIMEOUT_SECONDS));
        if (thread.isAlive()) {
          logger.warn(
              "Replay input injector thread '{}' did not complete within {}s timeout",
              thread.getName(),
              REPLAY_INJECTOR_JOIN_TIMEOUT_SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn(
            "Interrupted while joining replay input injector thread '{}'", thread.getName());
        break;
      }
    }
  }

  /**
   * Prints the replay divergence report to stderr, at most once per peer run.
   *
   * <p>Called from two places: the main thread after the self-called application returns, and the
   * JVM shutdown hook. The {@link AtomicBoolean} guard ensures exactly-once emission so neither
   * call-site is missed (SIGTERM, which bypasses the main-thread path) nor duplicated (normal exit,
   * which runs both paths).
   *
   * @param injector the Guice injector used to obtain the {@link ReplayContext}
   * @return {@code true} iff this call printed a non-empty report; callers may use the result to
   *     bump the exit code to {@link #EXIT_REPLAY_DIVERGENCES}
   */
  private boolean printReplayDivergenceReportOnce(Injector injector) {
    if (!replayDivergenceReportPrinted.compareAndSet(false, true)) {
      return false;
    }
    ReplayContext replayContext = injector.getInstance(ReplayContext.class);
    if (replayContext == null) {
      return false;
    }
    DivergenceReport report = replayContext.getDivergenceDetector().getReport();
    if (report.isEmpty()) {
      return false;
    }
    System.err.print(report.formatAsText());
    // Explicit flush is load-bearing on the shutdown-hook path: the JVM halts as soon as all
    // hooks return, before stderr's own buffer is guaranteed to drain, so the report would be
    // silently dropped on SIGTERM without this.
    System.err.flush();
    return true;
  }
}
