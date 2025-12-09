/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.chronicle;

import com.quasient.pal.messages.OutboundMsg;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for Chronicle queue operations in CLI and runtime contexts.
 *
 * <p>This class provides methods to read, query, and manage Chronicle queues, enabling CLI commands
 * and integration tests to work with Chronicle-based logs. It supports operations such as counting
 * messages, reading message streams, checking queue existence, calculating queue sizes, and
 * deleting queues.
 *
 * <p>All methods are designed to be safe and handle non-existent queues gracefully, returning null
 * or empty results rather than throwing exceptions in most cases.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Check if queue exists
 * if (ChronicleLogUtil.queueExists(queuePath)) {
 *   // Get queue metadata
 *   QueueIndexInfo info = ChronicleLogUtil.getQueueIndexInfo(queuePath);
 *   long messageCount = info.getMessageCount();
 *   long sizeInBytes = ChronicleLogUtil.getQueueSizeInBytes(queuePath);
 * }
 * }</pre>
 */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Utility class - private constructor throws to prevent instantiation")
public class ChronicleLogUtil {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ChronicleLogUtil.class);

  /** Private constructor to prevent instantiation of utility class. */
  private ChronicleLogUtil() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Checks if a Chronicle queue exists at the specified path.
   *
   * <p>A queue is considered to exist if the directory exists and contains Chronicle queue files
   * (*.cq4 files).
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return {@code true} if the queue exists and contains data files, {@code false} otherwise
   */
  public static boolean queueExists(Path queuePath) {
    if (queuePath == null || !Files.exists(queuePath)) {
      return false;
    }

    if (!Files.isDirectory(queuePath)) {
      return false;
    }

    // Check if directory contains Chronicle queue files (*.cq4*)
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(queuePath, "*.cq4*")) {
      return stream.iterator().hasNext();
    } catch (IOException e) {
      logger.debug("Error checking queue existence at {}", queuePath, e);
      return false;
    }
  }

  /**
   * Counts the number of messages in the specified Chronicle queue.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return the number of messages in the queue, or 0 if the queue is empty or doesn't exist
   */
  public static int countMessages(Path queuePath) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      int count = 0;
      while (OutboundMsg.readNext(tailer) != null) {
        count++;
      }
      return count;
    } catch (Exception e) {
      logger.debug("Error counting messages in queue at {}", queuePath, e);
      // If queue doesn't exist or can't be read, return 0
      return 0;
    }
  }

  /**
   * Checks if the specified Chronicle queue is empty.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return {@code true} if the queue is empty or doesn't exist, {@code false} otherwise
   */
  public static boolean isQueueEmpty(Path queuePath) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();
      return OutboundMsg.readNext(tailer) == null;
    } catch (Exception e) {
      logger.debug("Error checking if queue is empty at {}", queuePath, e);
      // If queue doesn't exist or can't be read, consider it empty
      return true;
    }
  }

  /**
   * Gets the first and last logical offset of the Chronicle queue.
   *
   * <p>Unlike Chronicle's internal indices (which encode cycle and sequence), this method returns
   * logical offsets similar to Kafka: starting at 0 for the first message and incrementing
   * sequentially. This makes Chronicle queue offsets comparable to Kafka offsets in CLI output.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a {@link QueueIndexInfo} containing logical first (0) and last (count-1) offsets, or
   *     null if queue doesn't exist
   */
  @Nullable
  public static QueueIndexInfo getQueueIndexInfo(Path queuePath) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      // Count messages to get logical offsets
      long messageCount = 0;
      while (OutboundMsg.readNext(tailer) != null) {
        messageCount++;
      }

      if (messageCount == 0) {
        // Empty queue - return -1 for both indices to indicate no messages
        return new QueueIndexInfo(-1, -1);
      }

      // Return logical offsets: 0-based sequential numbering like Kafka
      long firstIndex = 0;
      long lastIndex = messageCount - 1;
      return new QueueIndexInfo(firstIndex, lastIndex);
    } catch (Exception e) {
      logger.debug("Error getting queue index info at {}", queuePath, e);
      return null;
    }
  }

  /**
   * Calculates the total logical data size of a Chronicle queue in bytes.
   *
   * <p>This method attempts to use Chronicle Queue's internal API to get actual write positions
   * when possible. If internal APIs are not accessible, it falls back to sampling messages to
   * estimate size.
   *
   * <p>Chronicle Queue pre-allocates .cq4 files (often 64MB+ per cycle), so file system size
   * queries report allocated size, not actual data size. The `writePosition` from Chronicle's
   * internal stores provides accurate byte positions (within ~4KB accuracy).
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return the estimated logical data size in bytes, or 0 if the queue doesn't exist or an error
   *     occurs
   */
  public static long getQueueSizeInBytes(Path queuePath) {
    if (queuePath == null || !Files.exists(queuePath)) {
      return 0L;
    }

    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      // Try to use Chronicle's internal API to get actual write positions
      if (queue instanceof SingleChronicleQueue singleQueue) {
        try {
          // Attempt to calculate size using internal store positions
          // This accesses the actual write position in each cycle file
          long totalSize = 0;

          // Get the first and last cycles
          long firstCycle = singleQueue.firstCycle();
          long lastCycle = singleQueue.lastCycle();

          if (firstCycle == -1 || lastCycle == -1) {
            return 0L; // Empty queue
          }

          // Sum write positions across all cycle files
          for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
            try {
              // Try to get the store for this cycle
              var store = singleQueue.storeForCycle((int) cycle, 0, false, null);
              if (store != null) {
                try {
                  long writePos = store.writePosition();
                  totalSize += writePos;
                } finally {
                  // Release the store reference if needed
                  store.close();
                }
              }
            } catch (Exception storeEx) {
              logger.debug("Could not get store for cycle {}: {}", cycle, storeEx.getMessage());
            }
          }

          if (totalSize > 0) {
            logger.debug("Chronicle queue size from write positions: {} bytes", totalSize);
            return totalSize;
          }
        } catch (Exception internalApiEx) {
          logger.debug(
              "Could not use Chronicle internal API for size calculation: {}",
              internalApiEx.getMessage());
          // Fall through to sampling approach
        }
      }

      // Fallback: Sample messages to estimate size
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      long messageCount = 0;
      long totalBytesRead = 0;
      int sampleCount = 0;
      final int SAMPLE_SIZE = 100; // Sample first 100 messages for average size

      // Count all messages and sample some to estimate average size
      OutboundMsg msg;
      while ((msg = OutboundMsg.readNext(tailer)) != null) {
        messageCount++;
        if (sampleCount < SAMPLE_SIZE) {
          // Estimate message size based on its content
          // This is a rough estimate: header + payload + metadata
          long estimatedMsgSize = 50; // Base overhead for type, headers, IDs, etc.
          if (msg.getBody() != null) {
            estimatedMsgSize += msg.getBody().length;
          }
          totalBytesRead += estimatedMsgSize;
          sampleCount++;
        }
      }

      if (messageCount == 0) {
        return 0L;
      }

      // Calculate average and extrapolate
      if (sampleCount > 0) {
        long averageMessageSize = totalBytesRead / sampleCount;
        long estimatedSize = averageMessageSize * messageCount;
        logger.debug("Chronicle queue size estimated from sampling: {} bytes", estimatedSize);
        return estimatedSize;
      }

      return 0L;
    } catch (Exception e) {
      logger.debug("Error calculating queue size at {}", queuePath, e);
      return 0L;
    }
  }

  /**
   * Deletes a Chronicle queue by removing all files in the queue directory.
   *
   * <p>This method recursively deletes all files and subdirectories within the queue directory,
   * then deletes the directory itself.
   *
   * @param queuePath the path to the Chronicle queue directory to delete
   * @return {@code true} if the queue was successfully deleted, {@code false} otherwise
   */
  public static boolean deleteQueue(Path queuePath) {
    if (queuePath == null || !Files.exists(queuePath)) {
      logger.debug("Queue path does not exist: {}", queuePath);
      return false;
    }

    try {
      // Walk the file tree in reverse order (depth-first) to delete files before directories
      try (Stream<Path> paths = Files.walk(queuePath)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                    logger.debug("Deleted: {}", path);
                  } catch (IOException e) {
                    logger.error("Failed to delete: {}", path, e);
                  }
                });
      }
      return !Files.exists(queuePath);
    } catch (IOException e) {
      logger.error("Error deleting queue at {}", queuePath, e);
      return false;
    }
  }

  /**
   * Creates a read-only Chronicle queue instance.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a ChronicleQueue instance opened in read-only mode
   */
  private static ChronicleQueue createReadOnlyQueue(Path queuePath) {
    return SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build();
  }

  /**
   * Container class for Chronicle queue index information.
   *
   * <p>Holds the first and last indices of a Chronicle queue, useful for verifying queue state and
   * calculating message counts.
   */
  public static class QueueIndexInfo {
    /** The first index in the queue. */
    private final long firstIndex;

    /** The last index in the queue. */
    private final long lastIndex;

    /**
     * Constructs a QueueIndexInfo with the specified indices.
     *
     * @param firstIndex the first index in the queue
     * @param lastIndex the last index in the queue
     */
    public QueueIndexInfo(long firstIndex, long lastIndex) {
      this.firstIndex = firstIndex;
      this.lastIndex = lastIndex;
    }

    /**
     * Returns the first index in the queue.
     *
     * @return the first index
     */
    public long getFirstIndex() {
      return firstIndex;
    }

    /**
     * Returns the last index in the queue.
     *
     * @return the last index
     */
    public long getLastIndex() {
      return lastIndex;
    }

    /**
     * Returns the number of messages in the queue based on the index range.
     *
     * <p>Note: This is an approximation based on index arithmetic and may not reflect the actual
     * message count if there are gaps in the indices.
     *
     * @return the approximate number of messages
     */
    public long getMessageCount() {
      if (lastIndex < 0 || firstIndex < 0) {
        return 0;
      }
      // Chronicle indices are sequential, so count is last - first + 1
      // However, if the queue is empty, both indices might be at default values
      return Math.max(0, lastIndex - firstIndex + 1);
    }

    @Override
    public String toString() {
      return String.format(
          "QueueIndexInfo{firstIndex=%d, lastIndex=%d, messageCount=%d}",
          firstIndex, lastIndex, getMessageCount());
    }
  }
}
