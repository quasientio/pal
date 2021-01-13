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

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
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
import kong.unirest.Unirest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.ContextFillingTransformSupplier;
import net.ittera.pal.messages.protobuf.KafkaExecMessageSerde;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "indexer")
public class IndexingService implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);
  private CountDownLatch shutdownLatch = new CountDownLatch(1);

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
      defaultValue = "localhost:2181",
      required = true,
      paramLabel = "DIRECTORY_ADDRESS",
      description = "get log info from directory")
  private String palDirAddress;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  private Instant lastIndexed;
  private static final int BATCH_SIZE = 200;
  private static final int SECS_TO_SUBMIT_BATCH = 5;

  private final List<Map> batch = new ArrayList<>(BATCH_SIZE);

  private MessageIndexer messageIndexer;

  public IndexingService(String esURL, String palDirURL, String logName) {
    this.elasticSearchAddress = esURL;
    this.palDirAddress = palDirURL;
    this.logName = logName;
  }

  public IndexingService() {}

  private void setTimeLastReceived() {
    lastIndexed = Instant.now();
  }

  private void submitBatch() {
    synchronized (batch) {
      if (batch.size() > 0) {
        messageIndexer.bulkIndex(logName, batch);
        batch.clear();
      }
    }
  }

  private synchronized void addToBatch(Map messageWithCtx) {
    synchronized (batch) {
      batch.add(messageWithCtx);
    }
  }

  @Override
  public Integer call() throws Exception {

    logger.info("Started indexing log `{}`", logName);
    PALDirectory palDirectory = new PALDirectory(palDirAddress);
    LogInfo logInfo = palDirectory.getLogInfo(logName);

    messageIndexer = new MessageIndexer(elasticSearchAddress);

    /*
    1. CONFIGURE STREAMS API
    */
    Properties props = new Properties();
    String consumerId = "indexer-" + UUID.randomUUID().toString();
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
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaExecMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();
    //    KStream<String, ExecMessage> stream = builder.stream(logName);
    // stream: deserialize value
    KStream<String, Message> stream =
        builder.<String, byte[]>stream(logName)
            .map(
                (k, v) -> {
                  try {
                    return new KeyValue<>(k, Message.parseFrom(v));
                  } catch (InvalidProtocolBufferException e) {
                    logger.error("Error parsing message", e);
                    return new KeyValue<>(k, null);
                  }
                });

    KStream<String, Map> streamWithCtxt = stream.transform(ContextFillingTransformSupplier::new);

    streamWithCtxt.foreach(
        (k, m) -> {
          addToBatch(m);
          if (batch.size() == BATCH_SIZE) {
            submitBatch();
            logger.debug("submitted new batch of {} docs", BATCH_SIZE);
          }
          setTimeLastReceived();
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

    // have this waiting thread send incomplete batches after some time elapsed without new messages
    while (shutdownLatch.getCount() > 0) {
      if (batch.size() > 0) {
        Duration elapsedSinceLast = Duration.between(lastIndexed, Instant.now());
        if (elapsedSinceLast.compareTo(Duration.ofSeconds(SECS_TO_SUBMIT_BATCH)) > 0) {
          submitBatch();
        }
      }
      Thread.sleep(300);
    }

    // close resources
    streams.close();
    Unirest.shutDown();
    palDirectory.close();
    logger.debug("indexing service for log '{}' has shut down", logName);
    return 0;
  }

  public void shutdown() {
    shutdownLatch.countDown();
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
        (Thread thread, Throwable throwable) -> {
          logger.error("Uncaught throwable", throwable);
        });

    // start consuming the stream
    streams.start();
    try {
      KafkaStreams.State finalState = stateFuture.get();
      if (finalState == KafkaStreams.State.RUNNING) {
        // TODO start consuming thread here
      }
    } catch (InterruptedException | ExecutionException ex) {
      logger.error("Error starting stream", ex);
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new IndexingService()).execute(args);
    System.exit(exitCode);
  }
}
