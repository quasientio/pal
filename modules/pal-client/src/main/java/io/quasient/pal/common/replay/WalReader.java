/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageFamily;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads all {@link ExecMessage} entries from a Chronicle queue and returns them as a list of {@link
 * WalEntry} instances.
 *
 * <p>This utility is the I/O bridge between the persisted Chronicle WAL and the in-memory {@link
 * WalIndex}. It reuses the mature {@link OutboundMsg#readNext(ExcerptTailer)} deserialization
 * pipeline already used by {@code pal print} and {@code ChronicleSourceLogReader}, ensuring
 * compatibility with the existing WAL format.
 *
 * <p>The deserialization pipeline is:
 *
 * <ol>
 *   <li>{@link ChronicleQueue} &rarr; {@link ExcerptTailer}
 *   <li>{@link OutboundMsg#readNext(ExcerptTailer)} reads binary format (type byte + bodyLen int +
 *       body bytes)
 *   <li>{@link Message#unmarshal(byte[], int)} &rarr; {@link ExecMessage}
 *   <li>{@link WalEntry#fromExecMessage(long, ExecMessage)}
 * </ol>
 *
 * <p>Only messages belonging to the {@link MessageFamily#EXEC} family are included; all other
 * message types (CONTROL, META, INTERCEPT) are filtered out.
 */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Utility class - private constructor throws to prevent instantiation")
public final class WalReader {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(WalReader.class);

  /** Private constructor to prevent instantiation of utility class. */
  private WalReader() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Reads all EXEC-family messages from a Chronicle queue and returns them as {@link WalEntry}
   * instances.
   *
   * <p>The queue is opened in read-only mode. An {@link ExcerptTailer} is created and positioned at
   * the start. Each message is read via {@link OutboundMsg#readNext(ExcerptTailer)}, filtered to
   * the {@link MessageFamily#EXEC} family, deserialized into an {@link ExecMessage}, and wrapped in
   * a {@link WalEntry} with the Chronicle queue index as its offset.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a list of {@link WalEntry} instances in offset order, or an empty list if the queue
   *     contains no EXEC messages
   */
  public static List<WalEntry> readChronicleWal(Path queuePath) {
    List<WalEntry> entries = new ArrayList<>();

    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      OutboundMsg outboundMsg;
      while (true) {
        long index = tailer.index();
        outboundMsg = OutboundMsg.readNext(tailer);
        if (outboundMsg == null) {
          break;
        }

        if (outboundMsg.getMessageType().getFamily() != MessageFamily.EXEC) {
          logger.debug(
              "Skipping non-EXEC message at index {}: {}", index, outboundMsg.getMessageType());
          continue;
        }

        Message message = new Message();
        message.unmarshal(outboundMsg.getBody(), 0);
        ExecMessage execMessage = message.getExecMessage();

        entries.add(WalEntry.fromExecMessage(index, execMessage));
      }
    }

    filterBootstrapMainCompletion(entries);

    return entries;
  }

  /**
   * Filters out the trailing bootstrap {@code main()} completion if present.
   *
   * <p>{@code SelfBootstrapInvoker} invokes {@code main()} directly via reflection, so the
   * operation (BEFORE message) is never written to the WAL. However, the completion (AFTER message
   * / EXEC_RETURN_VALUE) <em>is</em> written because the dispatch path's {@code
   * createAfterExecMessage} fires after execution. This leaves an orphaned completion at the end of
   * every peer-recorded WAL.
   *
   * <p>This method removes that trailing orphaned completion when:
   *
   * <ol>
   *   <li>The last entry is a {@link WalEntryKind#COMPLETION}
   *   <li>Its executable name is {@code "main"}
   *   <li>No {@link WalEntryKind#OPERATION} entry for {@code "main"} exists in the WAL
   * </ol>
   *
   * @param entries the mutable list of entries to filter in place
   */
  static void filterBootstrapMainCompletion(List<WalEntry> entries) {
    if (entries.isEmpty()) {
      return;
    }

    WalEntry lastEntry = entries.get(entries.size() - 1);
    if (lastEntry.getKind() != WalEntryKind.COMPLETION
        || !"main".equals(lastEntry.getExecutableName())) {
      return;
    }

    boolean hasMainOperation =
        entries.stream()
            .anyMatch(
                e -> e.getKind() == WalEntryKind.OPERATION && "main".equals(e.getExecutableName()));

    if (!hasMainOperation) {
      logger.debug(
          "Filtering bootstrap main() completion at offset {} (no matching OPERATION)",
          lastEntry.getOffset());
      entries.remove(entries.size() - 1);
    }
  }

  /**
   * Reads all EXEC-family messages from a Chronicle queue and builds a {@link WalIndex}.
   *
   * <p>This is a convenience method that calls {@link #readChronicleWal(Path)} and then passes the
   * resulting entries to {@link WalIndex#build(List)}.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a fully indexed {@link WalIndex} built from the Chronicle queue entries
   */
  public static WalIndex readAndIndexChronicleWal(Path queuePath) {
    return WalIndex.build(readChronicleWal(queuePath));
  }
}
