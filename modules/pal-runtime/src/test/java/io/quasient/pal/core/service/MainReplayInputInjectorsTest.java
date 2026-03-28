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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Tests for the replay input injector startup sequencing in {@link Main}.
 *
 * <p>Verifies that {@code startReplayInputInjectors} creates the correct number of threads, names
 * them to match WAL thread names, and that {@code joinReplayInjectorThreads} correctly joins
 * threads.
 */
public class MainReplayInputInjectorsTest {

  /**
   * Tests that {@code startReplayInputInjectors} returns an empty list when the WAL has no input
   * threads (no entry-point operations).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void startReplayInputInjectors_noInputThreads_returnsEmptyList() throws Exception {
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0, "self-caller", 1), makeCompletion(1, "self-caller", 2));

    WalIndex index = WalIndex.build(entries);
    ReplayContext replayContext = createReplayContext(index, true);

    MockDispatcher mockDispatcher = new MockDispatcher();
    Injector injector = createInjector(mockDispatcher);
    CustomClassloader cl = createTestClassloader();

    @SuppressWarnings("unchecked")
    List<Thread> threads =
        (List<Thread>) invokeStartReplayInputInjectors(new Main(), replayContext, injector, cl);

    assertThat(threads, is(empty()));
  }

  /**
   * Tests that {@code startReplayInputInjectors} creates one thread per input thread in the WAL.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void startReplayInputInjectors_withInputThreads_createsThreadsPerInputThread()
      throws Exception {
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeEntryPointOperation(2, "rpc-worker-1", 3),
            makeEntryPointCompletion(3, "rpc-worker-1", 4),
            makeEntryPointOperation(4, "rpc-worker-2", 5),
            makeEntryPointCompletion(5, "rpc-worker-2", 6));

    WalIndex index = WalIndex.build(entries);
    // Use unordered so injectors don't gate on WAL offsets
    ReplayContext replayContext = createReplayContext(index, false);

    MockDispatcher mockDispatcher = new MockDispatcher();
    Injector injector = createInjector(mockDispatcher);
    CustomClassloader cl = createTestClassloader();

    @SuppressWarnings("unchecked")
    List<Thread> threads =
        (List<Thread>) invokeStartReplayInputInjectors(new Main(), replayContext, injector, cl);

    assertThat(threads, hasSize(2));

    // In the real flow, dispatchIncoming() counts down the injector ready latch after loading
    // the target class. Simulate this by counting it down here so injectors can proceed.
    replayContext.countDownInjectorLatch();

    // Wait for threads to finish
    for (Thread t : threads) {
      t.join(5000);
    }

    // Both threads should have been named to match WAL thread names
    List<String> threadNames = new ArrayList<>();
    for (Thread t : threads) {
      threadNames.add(t.getName());
    }
    Collections.sort(threadNames);
    assertThat(threadNames.get(0), is("rpc-worker-1"));
    assertThat(threadNames.get(1), is("rpc-worker-2"));
  }

  /**
   * Tests that injector threads are daemon threads.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void startReplayInputInjectors_threadsAreDaemons() throws Exception {
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeEntryPointOperation(2, "rpc-worker-1", 3),
            makeEntryPointCompletion(3, "rpc-worker-1", 4));

    WalIndex index = WalIndex.build(entries);
    ReplayContext replayContext = createReplayContext(index, false);

    MockDispatcher mockDispatcher = new MockDispatcher();
    Injector injector = createInjector(mockDispatcher);
    CustomClassloader cl = createTestClassloader();

    @SuppressWarnings("unchecked")
    List<Thread> threads =
        (List<Thread>) invokeStartReplayInputInjectors(new Main(), replayContext, injector, cl);

    assertThat(threads, hasSize(1));
    assertThat(threads.get(0).isDaemon(), is(true));

    // Count down the injector ready latch so the thread can proceed and finish.
    replayContext.countDownInjectorLatch();

    for (Thread t : threads) {
      t.join(5000);
    }
  }

  /**
   * Tests that {@code joinReplayInjectorThreads} completes successfully when threads are already
   * done.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void joinReplayInjectorThreads_completedThreads_joinsSuccessfully() throws Exception {
    Thread t = new Thread(() -> {});
    t.start();
    t.join();

    invokeJoinReplayInjectorThreads(new Main(), List.of(t));
    // No exception means success
  }

  /**
   * Tests that {@code joinReplayInjectorThreads} handles an empty list gracefully.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void joinReplayInjectorThreads_emptyList_noOp() throws Exception {
    invokeJoinReplayInjectorThreads(new Main(), new ArrayList<>());
    // No exception means success
  }

  /**
   * Tests that injector threads dispatch entry points to IncomingMessageDispatcher.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void startReplayInputInjectors_dispatchesEntryPointsToDispatcher() throws Exception {
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeEntryPointOperation(2, "rpc-worker-1", 3),
            makeEntryPointCompletion(3, "rpc-worker-1", 4),
            makeEntryPointOperation(4, "rpc-worker-1", 5),
            makeEntryPointCompletion(5, "rpc-worker-1", 6));

    WalIndex index = WalIndex.build(entries);
    ReplayContext replayContext = createReplayContext(index, false);

    MockDispatcher mockDispatcher = new MockDispatcher();
    Injector injector = createInjector(mockDispatcher);
    CustomClassloader cl = createTestClassloader();

    @SuppressWarnings("unchecked")
    List<Thread> threads =
        (List<Thread>) invokeStartReplayInputInjectors(new Main(), replayContext, injector, cl);

    assertThat(threads, hasSize(1));

    // In the real flow, dispatchIncoming() counts down the injector ready latch after loading
    // the target class. Simulate this by counting it down here so injectors can proceed.
    replayContext.countDownInjectorLatch();

    for (Thread t : threads) {
      t.join(5000);
    }

    // 2 entry points dispatched
    assertThat(mockDispatcher.callCount.get(), is(2));
  }

  /**
   * Tests that injector threads have the custom classloader set as their context classloader.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void startReplayInputInjectors_setsContextClassloader() throws Exception {
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeEntryPointOperation(2, "rpc-worker-1", 3),
            makeEntryPointCompletion(3, "rpc-worker-1", 4));

    WalIndex index = WalIndex.build(entries);
    ReplayContext replayContext = createReplayContext(index, false);

    MockDispatcher mockDispatcher = new MockDispatcher();
    Injector injector = createInjector(mockDispatcher);
    CustomClassloader cl = createTestClassloader();

    @SuppressWarnings("unchecked")
    List<Thread> threads =
        (List<Thread>) invokeStartReplayInputInjectors(new Main(), replayContext, injector, cl);

    assertThat(threads, hasSize(1));
    assertThat(threads.get(0).getContextClassLoader(), is(cl));

    // Count down the injector ready latch so the thread can proceed and finish.
    replayContext.countDownInjectorLatch();

    for (Thread t : threads) {
      t.join(5000);
    }
  }

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /** Creates a synthetic OPERATION WalEntry (not an entry point). */
  private static WalEntry makeOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("op" + offset);
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a synthetic COMPLETION WalEntry. */
  private static WalEntry makeCompletion(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a synthetic entry-point OPERATION WalEntry. */
  private static WalEntry makeEntryPointOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("entryOp" + offset);
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a synthetic entry-point COMPLETION WalEntry. */
  private static WalEntry makeEntryPointCompletion(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);
    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a ReplayContext from a WalIndex. */
  private static ReplayContext createReplayContext(WalIndex index, boolean ordered) {
    return new ReplayContext(
        index,
        new ReplayPolicy(),
        new ReplayObjectStore(),
        new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
        new ReplayGate(ordered));
  }

  /** Creates a CustomClassloader for testing. */
  private static CustomClassloader createTestClassloader() {
    return new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
  }

  /** Creates a Guice injector providing the mock dispatcher via provider to skip injection. */
  private static Injector createInjector(MockDispatcher mockDispatcher) {
    return Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(IncomingMessageDispatcher.class).toProvider(() -> mockDispatcher);
          }
        });
  }

  /** Invokes the private startReplayInputInjectors method via reflection. */
  private static Object invokeStartReplayInputInjectors(
      Main main, ReplayContext replayContext, Injector injector, CustomClassloader classloader)
      throws Exception {
    Method method =
        Main.class.getDeclaredMethod(
            "startReplayInputInjectors",
            ReplayContext.class,
            Injector.class,
            CustomClassloader.class);
    method.setAccessible(true);
    return method.invoke(main, replayContext, injector, classloader);
  }

  /** Invokes the private joinReplayInjectorThreads method via reflection. */
  private static void invokeJoinReplayInjectorThreads(Main main, List<Thread> threads)
      throws Exception {
    Method method = Main.class.getDeclaredMethod("joinReplayInjectorThreads", List.class);
    method.setAccessible(true);
    method.invoke(main, threads);
  }

  /**
   * Mock implementation of IncomingMessageDispatcher for counting calls.
   *
   * <p>Extends IncomingMessageDispatcher and overrides {@code incomingCall} to count invocations.
   * Since IncomingMessageDispatcher uses field injection, we can construct the mock with default
   * constructor and override the method.
   */
  static class MockDispatcher extends IncomingMessageDispatcher {

    /** Number of times incomingCall was invoked. */
    final AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public ExecMessage incomingCall(
        ExecMessage execMessage, MessageType messageType, MessageChannelType messageChannelType) {
      callCount.incrementAndGet();
      return new ExecMessage();
    }
  }
}
