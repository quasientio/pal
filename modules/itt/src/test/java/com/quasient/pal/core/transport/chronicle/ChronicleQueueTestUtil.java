/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.chronicle;

import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.types.MessageType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

/**
 * Test utility class for verifying Chronicle queue content in integration tests.
 *
 * <p>This class provides methods to read and verify messages written to Chronicle queues, enabling
 * tests to confirm that messages were actually persisted and can be read back.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Verify queue has messages
 * List<OutboundMsg> messages = ChronicleQueueTestUtil.readAllMessages(queuePath);
 * assertThat("Queue should contain messages", messages.isEmpty(), is(false));
 *
 * // Verify specific message count
 * int count = ChronicleQueueTestUtil.countMessages(queuePath);
 * assertThat("Queue should contain 5 messages", count, is(5));
 *
 * // Verify queue is not empty
 * assertThat("Queue should not be empty",
 *     ChronicleQueueTestUtil.isQueueEmpty(queuePath), is(false));
 * }</pre>
 */
public class ChronicleQueueTestUtil {

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
   * <p>Holds the first and last indices of a Chronicle queue, useful for verifying queue state.
   */
  public static class QueueIndexInfo {
    private final long firstIndex;
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
