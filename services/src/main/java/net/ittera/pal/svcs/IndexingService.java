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

package net.ittera.pal.svcs;

import static picocli.CommandLine.Option;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import kong.unirest.Unirest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.ContextFillingTransformSupplier;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.serdes.kafka.KafkaMessageSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "indexer")
public class IndexingService implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  @Option(
      names = {"--es-url"},
      required = true,
      paramLabel = "ELASTIC_URL",
      description = "elasticsearch server URL")
  private String elasticSearchAddress;

  @Option(
      names = {"-l", "--log"},
      required = true,
      paramLabel = "LOGNAME",
      description = "index given log")
  private String logName;

  @Option(
      names = {"--pal-directory"},
      defaultValue = "localhost:2379",
      required = true,
      paramLabel = "DIRECTORY_ADDRESS",
      description = "get log info from directory")
  private String palDirAddress;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  private Instant lastIndexed;
  private Instant lastTimeReceived;
  private static final int BATCH_SIZE = 100;
  private static final int SECS_TO_SUBMIT_BATCH = 3;

  private final List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);

  private MessageIndexer messageIndexer;

  public IndexingService(String elasticSearchUrl, String palDirUrl, String logName) {
    this.elasticSearchAddress = elasticSearchUrl;
    this.palDirAddress = palDirUrl;
    this.logName = logName;
  }

  public IndexingService() {}

  private void setTimeLastReceived() {
    lastTimeReceived = Instant.now();
  }

  private void setTimeLastIndexed() {
    lastIndexed = Instant.now();
  }

  private void submitBatch() {
    synchronized (batch) {
      if (!batch.isEmpty()) {
        messageIndexer.bulkIndex(logName, batch);
        setTimeLastIndexed();
        batch.clear();
      }
    }
  }

  private synchronized void addToBatch(Map<String, Object> messageWithCtx) {
    synchronized (batch) {
      batch.add(messageWithCtx);
    }
  }

  @Override
  public Integer call() throws Exception {

    logger.info("Started indexing log `{}`", logName);
    PalDirectory palDirectory = new PalDirectory(palDirAddress);
    LogInfo logInfo = palDirectory.getLogInfo(logName);

    messageIndexer = new MessageIndexer(elasticSearchAddress);

    /*
    1. CONFIGURE STREAMS API
    */
    Properties props = new Properties();
    String consumerId = "indexer-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s\n",
          logName, logInfo.getBootstrapServers(), consumerId);
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Kafka config: topic={} bootstrap_servers={} app_id={}",
          logName,
          logInfo.getBootstrapServers(),
          consumerId);
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, logInfo.getBootstrapServers());
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();
    KStream<String, LogMessage<?>> stream = builder.stream(logName);

    KStream<String, Map<String, Object>> streamWithCtxt =
        stream.transform(ContextFillingTransformSupplier::new);

    streamWithCtxt.foreach(
        (k, m) -> {
          addToBatch(m);
          setTimeLastReceived();
          if (batch.size() == BATCH_SIZE) {
            submitBatch();
            logger.debug("submitted new batch of {} docs", BATCH_SIZE);
          }
        });

    /*
      3. PREPARE AND START PROCESSING
    */

    final Topology topology = builder.build();
    if (verbose) {
      System.out.println(topology.describe());
    }

    final KafkaStreams streams = new KafkaStreams(topology, props);
    // attach shutdown handler to catch control-c
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                shutdownLatch.countDown();
              }
            });

    startStreams(streams);
    logger.debug("started kafka streams");

    // send incomplete batches after some time elapsed without new messages
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    @SuppressWarnings("unused")
    var unused =
        executorService.scheduleAtFixedRate(
            () -> {
              if (!batch.isEmpty()) {
                submitBatch();
              }
            },
            0,
            SECS_TO_SUBMIT_BATCH,
            TimeUnit.SECONDS);

    // wait for the shutdown signal
    shutdownLatch.await();

    // shutdown the executor service
    executorService.shutdown();

    // close resources
    streams.close();
    Unirest.shutDown();
    palDirectory.close();
    logger.debug("indexing service for log '{}' has shut down", logName);
    return 0;
  }

  private void startStreams(KafkaStreams streams) {
    CompletableFuture<KafkaStreams.State> stateFuture = new CompletableFuture<>();
    // set state listener
    streams.setStateListener(
        (newState, oldState) -> {
          if (stateFuture.isDone()) {
            return;
          }

          if (newState == KafkaStreams.State.RUNNING || newState == KafkaStreams.State.ERROR) {
            stateFuture.complete(newState);
          }
        });

    // catch unhandled exceptions
    streams.setUncaughtExceptionHandler(
        throwable -> {
          logger.error("Uncaught exception in stream. Will shutdown client.", throwable);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

    // start consuming the stream
    streams.start();
    try {
      KafkaStreams.State finalState = stateFuture.get();
      if (finalState == KafkaStreams.State.RUNNING) {
        logger.info("Stream started successfully");
        // TODO start consuming thread here
      }
    } catch (InterruptedException | ExecutionException ex) {
      logger.error("Error starting stream", ex);
    }
  }

  public Instant getLastIndexed() {
    return lastIndexed;
  }

  public Instant getLastTimeReceived() {
    return lastTimeReceived;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new IndexingService()).execute(args);
    System.exit(exitCode);
  }
}
