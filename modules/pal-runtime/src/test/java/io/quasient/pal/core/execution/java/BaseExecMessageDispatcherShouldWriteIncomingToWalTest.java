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
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for the {@code shouldWriteIncomingToWal(MessageChannelType)} helper method in {@link
 * BaseExecMessageDispatcher}.
 *
 * <p>These tests verify the decision logic that determines whether incoming messages should be
 * written to WAL/PUB. The helper considers three factors:
 *
 * <ol>
 *   <li>{@link RunOptions} flags ({@code WITH_WAL}, {@code WITH_TCP_PUB}, {@code
 *       WITH_WAL_INCOMING_RPC}, {@code WITH_WAL_ALL_INCOMING_RPC})
 *   <li>{@link MessageChannelType} of the incoming message
 *   <li>The {@code sourceAndWalAreSameLog} circularity guard
 * </ol>
 *
 * <p>Uses the {@code MinimalOk} pattern from {@link BaseExecMessageDispatcherDispatchTest} to
 * create a concrete subclass of {@link BaseExecMessageDispatcher}, then uses reflection to set
 * {@code runOptions} and the {@code sourceAndWalAreSameLog} field, and invokes the helper method.
 *
 * @see BaseExecMessageDispatcher
 * @see RunOptions#WITH_WAL_INCOMING_RPC
 * @see RunOptions#WITH_WAL_ALL_INCOMING_RPC
 */
public class BaseExecMessageDispatcherShouldWriteIncomingToWalTest {

  /**
   * Reflection helper to set {@code runOptions} on the dispatcher under test.
   *
   * @param d the dispatcher instance
   * @param ro the set of run options to inject
   * @throws Exception if reflection fails
   */
  private static void setRunOptions(AbstractDispatcher d, Set<RunOptions> ro) throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("runOptions");
    f.setAccessible(true);
    f.set(d, ro);
  }

  /**
   * Reflection helper to set {@code sourceAndWalAreSameLog} on the dispatcher under test.
   *
   * @param d the dispatcher instance
   * @param sameLog whether source log and WAL are the same
   * @throws Exception if reflection fails
   */
  private static void setSourceAndWalAreSameLog(AbstractDispatcher d, boolean sameLog)
      throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("sourceAndWalAreSameLog");
    f.setAccessible(true);
    f.setBoolean(d, sameLog);
  }

  /**
   * Reflection helper to invoke the private {@code shouldWriteIncomingToWal} method.
   *
   * @param d the dispatcher instance
   * @param channel the message channel type
   * @return the boolean result of the method
   * @throws Exception if reflection fails
   */
  private static boolean invokeShouldWriteIncomingToWal(
      BaseExecMessageDispatcher d, MessageChannelType channel) throws Exception {
    Method m =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "shouldWriteIncomingToWal", MessageChannelType.class);
    m.setAccessible(true);
    return (boolean) m.invoke(d, channel);
  }

  /** Minimal concrete subclass of {@link BaseExecMessageDispatcher} for testing. */
  static class MinimalOk extends BaseExecMessageDispatcher {

    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt,
        Object sender,
        Object target,
        Object[] args,
        boolean includeDeclaredExceptions) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      return new ExecMessage();
    }

    @Override
    protected Object invokeIncoming(
        AccessibleObject accessibleObject,
        Object target,
        List<MessageArgument> args,
        Object value) {
      return null;
    }

    @Override
    protected boolean returnsVoid(AccessibleObject accessibleObject) {
      return false;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return false;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Obj> getArgsList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }

  // ---------------------------------------------------------------
  // Test 1: No incoming WAL without explicit opt-in
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withoutWalIncomingRpc_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_WAL} (no WITH_WAL_INCOMING_RPC)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 2: ZMQ RPC writes to WAL when opted in
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingRpc_zmqSocket_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 3: Websocket RPC writes to WAL when opted in
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingRpc_websocket_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 4: CLI RPC no longer controlled by WITH_WAL_INCOMING_RPC
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingRpc_cliRpc_returnsFalse() throws Exception {
    // Given: RunOptions {WITH_WAL, WITH_WAL_INCOMING_RPC} (no WITH_WAL_INCOMING_CLI)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.CLI_RPC);

    // Then: returns false (CLI_RPC now requires its own WITH_WAL_INCOMING_CLI flag)
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 4a: CLI RPC writes to WAL when WITH_WAL_INCOMING_CLI is set
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingCli_cliRpc_returnsTrue() throws Exception {
    // Given: RunOptions {WITH_WAL, WITH_WAL_INCOMING_CLI}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_CLI));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.CLI_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 4b: CLI flag does not affect WEBSOCKET_RPC
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingCli_websocket_returnsFalse()
      throws Exception {
    // Given: RunOptions {WITH_WAL, WITH_WAL_INCOMING_CLI} (no WITH_WAL_INCOMING_RPC)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_CLI));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then: returns false (CLI flag only controls CLI_RPC)
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 4c: Both RPC and CLI flags set, CLI_RPC returns true
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withBothFlags_cliRpc_returnsTrue() throws Exception {
    // Given: RunOptions {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_INCOMING_CLI}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_INCOMING_CLI));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.CLI_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 4d: CLI flag without destination returns false
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingCli_noPubNorWal_returnsFalse()
      throws Exception {
    // Given: RunOptions {WITH_WAL_INCOMING_CLI} (no WITH_WAL, no WITH_TCP_PUB)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL_INCOMING_CLI));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.CLI_RPC);

    // Then: returns false (no destination)
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 5: LOG_RPC excluded without ALL flag
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalIncomingRpc_logRpc_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC} (no WITH_WAL_ALL_INCOMING_RPC)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.LOG_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 6: LOG_RPC included with ALL flag when logs differ
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_logRpc_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = false
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    setSourceAndWalAreSameLog(dispatcher, false);

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.LOG_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 7: Circularity guard blocks LOG_RPC when same log
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_returnsFalse()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    setSourceAndWalAreSameLog(dispatcher, true);

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.LOG_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 8: Circularity guard only affects LOG_RPC
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_websocket_returnsTrue()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    setSourceAndWalAreSameLog(dispatcher, true);

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 9: TCP PUB also triggers incoming WAL
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withTcpPubOnly_withWalIncomingRpc_returnsTrue()
      throws Exception {
    // Given: runOptions = {WITH_TCP_PUB, WITH_WAL_INCOMING_RPC} (no WITH_WAL)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher, EnumSet.of(RunOptions.WITH_TCP_PUB, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(true));
  }

  // ---------------------------------------------------------------
  // Test 10: No destination means no write
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_noPubNorWal_withWalIncomingRpc_returnsFalse()
      throws Exception {
    // Given: runOptions = {WITH_WAL_INCOMING_RPC} (no WITH_WAL, no WITH_TCP_PUB)
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 11: Warning logged for circularity override
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_logsWarning()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    setSourceAndWalAreSameLog(dispatcher, true);

    // Attach a ListAppender to capture log output
    Logger logger = (Logger) LoggerFactory.getLogger(MinimalOk.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      // When
      boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.LOG_RPC);

      // Then: returns false
      assertThat(result, is(false));

      // Then: warning was logged
      boolean warningLogged =
          listAppender.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("circular"));
      assertThat("Warning should be logged for circularity override", warningLogged, is(true));
    } finally {
      logger.detachAppender(listAppender);
    }
  }

  // ---------------------------------------------------------------
  // Test 12: Replay mode always returns false
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withReplayMode_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_REPLAY, WITH_WAL, WITH_WAL_INCOMING_RPC}
    // Even though WAL writing is otherwise enabled, replay mode suppresses it.
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(RunOptions.WITH_REPLAY, RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 13: Replay mode suppresses CLI_RPC WAL writes
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withReplayMode_cliRpc_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_REPLAY, WITH_WAL, WITH_WAL_INCOMING_CLI}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(RunOptions.WITH_REPLAY, RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_CLI));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.CLI_RPC);

    // Then
    assertThat(result, is(false));
  }

  // ---------------------------------------------------------------
  // Test 14: Replay mode suppresses WebSocket WAL writes
  // ---------------------------------------------------------------

  @Test
  public void shouldWriteIncomingToWal_withReplayMode_websocket_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_REPLAY, WITH_WAL, WITH_WAL_INCOMING_RPC}
    MinimalOk dispatcher = new MinimalOk();
    setRunOptions(
        dispatcher,
        EnumSet.of(RunOptions.WITH_REPLAY, RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));

    // When
    boolean result = invokeShouldWriteIncomingToWal(dispatcher, MessageChannelType.WEBSOCKET_RPC);

    // Then
    assertThat(result, is(false));
  }
}
