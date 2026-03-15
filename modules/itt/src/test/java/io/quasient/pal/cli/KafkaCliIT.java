/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for Kafka-related CLI functionality using the new entity-operation command
 * structure.
 *
 * <p>Tests for Kafka log operations in {@code pal log print}, {@code pal log ls}, {@code pal log
 * rm}, and {@code pal log call} commands, including:
 *
 * <ul>
 *   <li>Printing Kafka logs with and without PAL_DIRECTORY
 *   <li>Kafka end offset display (last offset, not last+1)
 *   <li>Removing Kafka logs with and without PAL_DIRECTORY
 *   <li>Calling methods that write to Kafka logs
 * </ul>
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class KafkaCliIT extends AbstractCliIT {

  // ==========================================================================
  // Kafka log print tests: pal log print
  // Old command: pal print -l <log>
  // New command: pal log print <log>
  // ==========================================================================

  /**
   * Tests that {@code pal log print} can print Kafka logs without PAL_DIRECTORY when -k is given.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogPrint_kafkaLog_withoutPalDirectory_withKafkaServers() throws Exception {
    // Given: A Kafka log created by launching a peer
    // When: `pal log print -k <kafkaServers> <walName> --full` is executed via runLogPrint()
    //       without -d flag (no PAL_DIRECTORY)
    // Then: Exit code is 0, stdout is non-empty

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal log print} can print Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogPrint_kafkaLog_withPalDirectory() throws Exception {
    // Given: A Kafka log created by launching a peer
    // When: `pal log print -d <palDirectory> <walName> --full` is executed via runLogPrint()
    // Then: Exit code is 0, stdout is non-empty

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Kafka log list tests: pal log ls
  // Old command: pal ls -L
  // New command: pal log ls
  // ==========================================================================

  /**
   * Tests that {@code pal log ls} shows Kafka end offset as the last message offset (not last+1).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogLs_kafkaLog_endOffsetIsLastMessageOffset() throws Exception {
    // Given: A Kafka log created by launching a peer that writes messages
    // When: `pal log ls -d <palDirectory> -l --no-trim` is executed via runLogLs()
    // Then: Exit code is 0, the displayed end offset equals messageCount - 1
    //       (last message index, not Kafka's internal last+1)

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Kafka log remove tests: pal log rm
  // Old command: pal rm -L <name>
  // New command: pal log rm <name>
  // ==========================================================================

  /**
   * Tests that {@code pal log rm} can remove Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogRm_kafkaLog_withPalDirectory() throws Exception {
    // Given: A Kafka log created by launching a peer
    // When: `pal log rm -d <palDirectory> <walName> --force` is executed via runLogRm()
    // Then: Exit code is 0, log no longer appears in `pal log ls` output

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal log rm} can remove Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogRm_kafkaLog_withoutPalDirectory() throws Exception {
    // Given: A Kafka log created by launching a peer
    // When: `pal log rm -k <kafkaServers> <walName> --force` is executed via runLogRm()
    //       without -d flag (no PAL_DIRECTORY)
    // Then: Exit code is 0

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Kafka log call tests: pal log call
  // Old command: pal call --output-log <source> --input-log <wal>
  // New command: pal log call --output-log <source> --input-log <wal>
  // ==========================================================================

  /**
   * Tests that {@code pal log call} can write to Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_kafkaLog_withPalDirectory() throws Exception {
    // Given: A peer launched with split Kafka logs (source + WAL) and --wal-all-incoming-rpc
    // When: `pal log call -d <palDirectory> -k <kafkaServers> --output-log <source>
    //       --input-log <wal> io.quasient.foobar.apps.quantized.rpc.Methods
    //       -m staticStringWithStringArgs test-call-kafka-registry` is executed via runLogCall()
    // Then: Exit code is 0, stdout contains "RESULT: test-call-kafka-registry"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal log call} can write to Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_kafkaLog_withoutPalDirectory() throws Exception {
    // Given: A peer launched with split Kafka logs (source + WAL) and --wal-all-incoming-rpc
    // When: `pal log call -k <kafkaServers> --output-log <source> --input-log <wal>
    //       io.quasient.foobar.apps.quantized.rpc.Methods -m staticStringWithStringArgs
    //       test-call-kafka-direct` is executed via runLogCall() without -d flag
    // Then: Exit code is 0, stdout contains "RESULT: test-call-kafka-direct"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }
}
