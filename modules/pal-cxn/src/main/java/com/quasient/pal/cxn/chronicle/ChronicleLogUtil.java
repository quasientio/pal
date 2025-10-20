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
import com.quasient.pal.messages.types.MessageType;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
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
 *
 *   // Read messages
 *   List<OutboundMsg> messages = ChronicleLogUtil.readMessagesFrom(queuePath, 0, 100);
 * }
 * }</pre>
 */
public class ChronicleLogUtil {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ChronicleLogUtil.class);

  /** Private constructor to prevent instantiation of utility class. */
  private ChronicleLogUtil() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Resolves a Chronicle queue name to an absolute path.
   *
   * <p>If the queue name is an absolute path, it is returned as-is. Otherwise, it is resolved
   * against the provided base directory.
   *
   * @param queueName the queue name or path (can be relative or absolute)
   * @param baseDir the base directory for resolving relative paths
   * @return the absolute path to the Chronicle queue
   */
  public static Path resolveQueuePath(String queueName, Path baseDir) {
    Path queueNamePath = Path.of(queueName);
    return queueNamePath.isAbsolute() ? queueNamePath : baseDir.resolve(queueName);
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

    // Check if directory contains Chronicle queue files (*.cq4)
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(queuePath, "*.cq4")) {
      return stream.iterator().hasNext();
    } catch (IOException e) {
      logger.debug("Error checking queue existence at {}", queuePath, e);
      return false;
    }
  }

  /**
   * Reads all messages from the specified Chronicle queue.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a list of all messages in the queue, or an empty list if the queue is empty or doesn't
   *     exist
   */
  public static List<OutboundMsg> readAllMessages(Path queuePath) {
    List<OutboundMsg> messages = new ArrayList<>();

    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      OutboundMsg msg;
      while ((msg = OutboundMsg.readNext(tailer)) != null) {
        messages.add(msg);
      }
    } catch (Exception e) {
      logger.debug("Error reading all messages from queue at {}", queuePath, e);
      // If queue doesn't exist or can't be read, return empty list
      return messages;
    }

    return messages;
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
   * Gets the first and last index of the Chronicle queue.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a {@link QueueIndexInfo} containing first and last indices, or null if queue doesn't
   *     exist
   */
  @Nullable
  public static QueueIndexInfo getQueueIndexInfo(Path queuePath) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      long firstIndex = queue.firstIndex();
      long lastIndex = queue.lastIndex();
      return new QueueIndexInfo(firstIndex, lastIndex);
    } catch (Exception e) {
      logger.debug("Error getting queue index info at {}", queuePath, e);
      return null;
    }
  }

  /**
   * Reads a specific message at the given index from the Chronicle queue.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @param index the Chronicle index to read from
   * @return the message at the specified index, or {@code null} if not found
   */
  @Nullable
  public static OutboundMsg readMessageAtIndex(Path queuePath, long index) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      if (tailer.moveToIndex(index)) {
        return OutboundMsg.readNext(tailer);
      }
      return null;
    } catch (Exception e) {
      logger.debug("Error reading message at index {} from queue at {}", index, queuePath, e);
      return null;
    }
  }

  /**
   * Reads messages starting from a specific offset up to a maximum count.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @param startOffset the Chronicle index to start reading from
   * @param maxMessages maximum number of messages to read
   * @return a list of messages, up to maxMessages count
   */
  public static List<OutboundMsg> readMessagesFrom(
      Path queuePath, long startOffset, int maxMessages) {
    List<OutboundMsg> messages = new ArrayList<>();

    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      if (!tailer.moveToIndex(startOffset)) {
        return messages;
      }

      OutboundMsg msg;
      int count = 0;
      while (count < maxMessages && (msg = OutboundMsg.readNext(tailer)) != null) {
        messages.add(msg);
        count++;
      }
    } catch (Exception e) {
      logger.debug("Error reading messages from offset {} at queue {}", startOffset, queuePath, e);
      // Return whatever we read so far
      return messages;
    }

    return messages;
  }

  /**
   * Verifies that all messages in the queue have the expected message type.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @param expectedType the expected message type
   * @return {@code true} if all messages have the expected type (or queue is empty), {@code false}
   *     otherwise
   */
  public static boolean allMessagesHaveType(Path queuePath, MessageType expectedType) {
    try (ChronicleQueue queue = createReadOnlyQueue(queuePath)) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      OutboundMsg msg;
      while ((msg = OutboundMsg.readNext(tailer)) != null) {
        if (msg.getMessageType() != expectedType) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      logger.debug("Error verifying message types in queue at {}", queuePath, e);
      return false;
    }
  }

  /**
   * Calculates the total size of a Chronicle queue on disk in bytes.
   *
   * <p>This method walks through all files in the queue directory and sums their sizes.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return the total size in bytes, or 0 if the queue doesn't exist or an error occurs
   */
  public static long getQueueSizeInBytes(Path queuePath) {
    if (queuePath == null || !Files.exists(queuePath)) {
      return 0L;
    }

    try (Stream<Path> files = Files.walk(queuePath)) {
      return files
          .filter(Files::isRegularFile)
          .mapToLong(
              path -> {
                try {
                  return Files.size(path);
                } catch (IOException e) {
                  logger.debug("Error getting size of file {}", path, e);
                  return 0L;
                }
              })
          .sum();
    } catch (IOException e) {
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
