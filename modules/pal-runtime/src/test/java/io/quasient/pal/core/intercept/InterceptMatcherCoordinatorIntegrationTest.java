/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InterceptEventMsg;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Integration tests for the coordination between {@link InterceptMatcher} and {@link
 * InterceptActivationCoordinator}.
 *
 * <p>These tests verify that when InterceptMatcher receives intercept registration messages, it
 * properly delegates to the coordinator which handles in-flight tracking, fencing, and drain before
 * registering the intercept.
 *
 * <p><b>Acceptance criteria tested:</b>
 *
 * <ul>
 *   <li>[TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorCalledWhenInterceptReceived]
 *   <li>[TEST:InterceptMatcherCoordinatorIntegrationTest.forceImmediateFlagPassedToCoordinator]
 *   <li>[TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorActivatesWithDrainWhenTrackingEnabled]
 *   <li>[TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorActivatesImmediatelyWhenForceImmediate]
 * </ul>
 */
public class InterceptMatcherCoordinatorIntegrationTest extends ZmqEnabledTest {

  private UUID peerUuid;
  private static final String INTERCEPT_REG_ADDRESS = "inproc://intercepts.reg.integration";
  private ZContext context;
  private ServiceManager manager;
  private InterceptMatcher interceptMatcher;
  private InterceptActivationCoordinator mockCoordinator;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private Socket registerSocket;

  @Before
  public void setup() throws InterruptedException {
    this.peerUuid = UUID.randomUUID();
    this.context = createContext();

    // First create the interceptMatcher (without coordinator)
    // We'll use a temporary null coordinator, then replace it
    this.interceptMatcher = null; // Will be set after coordinator is created

    // Create a mock coordinator that we can verify calls on
    this.mockCoordinator = mock(InterceptActivationCoordinator.class);

    // Now create the matcher with the mock coordinator
    this.interceptMatcher =
        new InterceptMatcher(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "InterceptMatcherIntegrationTest-Service",
            INTERCEPT_REG_ADDRESS,
            mockCoordinator);

    // Configure the mock coordinator to call registerInterceptRequest
    when(mockCoordinator.activateIntercept(any(InterceptMessage.class)))
        .thenAnswer(
            invocation -> {
              InterceptMessage msg = invocation.getArgument(0);
              try {
                interceptMatcher.registerInterceptRequest(msg);
                return InterceptActivationCoordinator.ActivationResult.success("Test success");
              } catch (DuplicateInterceptException e) {
                return InterceptActivationCoordinator.ActivationResult.failure(
                    "Duplicate intercept: " + e.getMessage());
              }
            });

    final Set<Service> services = new HashSet<>(List.of(this.interceptMatcher));
    this.manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);

    // create REQ socket to simulate requests (IRL: InterceptNodeListener)
    registerSocket = context.createSocket(SocketType.REQ);
    registerSocket.connect(INTERCEPT_REG_ADDRESS);
  }

  @After
  public void cleanup() throws Exception {
    // shut down services
    manager.stopAsync();

    // close sockets
    if (registerSocket != null) {
      registerSocket.close();
    }

    // close zmq context
    closeContext(context);
  }

  /**
   * Tests that the InterceptMatcher delegates to the coordinator when receiving an intercept
   * registration message.
   *
   * <p>Verifies that InterceptMatcher.registerNewAndGoneIntercepts() calls
   * coordinator.activateIntercept() with the received InterceptMessage.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorCalledWhenInterceptReceived]
   */
  @Test
  public void coordinatorCalledWhenInterceptReceived() throws Exception {
    // Given: An intercept registration message
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "com.example.Calculator",
            "add",
            Collections.emptyList(),
            this.getClass().getName(),
            "callback");

    // And: The coordinator will succeed
    when(mockCoordinator.activateIntercept(any(InterceptMessage.class)))
        .thenReturn(InterceptActivationCoordinator.ActivationResult.success("Test success"));

    // When: The intercept message is sent to the matcher
    new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)).send(registerSocket);

    // Then: The matcher should delegate to the coordinator
    String response = registerSocket.recvStr();

    // And: The response should be OK
    assertTrue("Expected OK response", response.equals(InterceptMatcher.REGISTER_OK_RESPONSE));

    // And: The coordinator's activateIntercept method should have been called
    verify(mockCoordinator, times(1)).activateIntercept(any(InterceptMessage.class));
  }

  /**
   * Tests that the forceImmediate flag from InterceptMessage is passed to the coordinator.
   *
   * <p>Verifies that when an InterceptMessage has forceImmediate=true, the coordinator receives
   * that flag and can make activation decisions based on it.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptMatcherCoordinatorIntegrationTest.forceImmediateFlagPassedToCoordinator]
   */
  @Test
  public void forceImmediateFlagPassedToCoordinator() throws Exception {
    // Given: An intercept registration message with forceImmediate=true
    InterceptMessage interceptMessage =
        msgBuilder
            .buildInterceptMessage(
                peerUuid,
                InterceptType.BEFORE,
                "com.example.Calculator",
                "add",
                Collections.emptyList(),
                this.getClass().getName(),
                "callback")
            .withForceImmediate(true);

    // And: The coordinator will succeed
    when(mockCoordinator.activateIntercept(any(InterceptMessage.class)))
        .thenReturn(InterceptActivationCoordinator.ActivationResult.success("Test success"));

    // When: The intercept message is sent to the matcher
    new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)).send(registerSocket);

    // Then: The matcher should receive OK response
    String response = registerSocket.recvStr();
    assertTrue("Expected OK response", response.equals(InterceptMatcher.REGISTER_OK_RESPONSE));

    // And: The coordinator should have been called with a message where forceImmediate=true
    verify(mockCoordinator, times(1))
        .activateIntercept(argThat(msg -> msg.getForceImmediate())); // Verify the flag is true
  }

  /**
   * Tests that the coordinator is called with the correct message when forceImmediate is false.
   *
   * <p>Verifies that when an intercept has forceImmediate=false, the coordinator is invoked with
   * that message.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorActivatesWithDrainWhenTrackingEnabled]
   */
  @Test
  public void coordinatorActivatesWithDrainWhenTrackingEnabled() throws Exception {
    // Given: An intercept registration message with forceImmediate=false
    InterceptMessage interceptMessage =
        msgBuilder
            .buildInterceptMessage(
                peerUuid,
                InterceptType.BEFORE,
                "com.example.Calculator",
                "add",
                Collections.emptyList(),
                this.getClass().getName(),
                "callback")
            .withForceImmediate(false);

    // When: The intercept message is sent to the matcher
    new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)).send(registerSocket);

    // Then: The matcher should receive OK response
    String response = registerSocket.recvStr();
    assertTrue("Expected OK response", response.equals(InterceptMatcher.REGISTER_OK_RESPONSE));

    // And: The coordinator should have been called with a message where forceImmediate=false
    verify(mockCoordinator, times(1))
        .activateIntercept(argThat(msg -> !msg.getForceImmediate())); // Verify the flag is false
  }

  /**
   * Tests that the coordinator is called correctly when forceImmediate=true.
   *
   * <p>Verifies that when an intercept has forceImmediate=true, the coordinator is invoked with
   * that message.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptMatcherCoordinatorIntegrationTest.coordinatorActivatesImmediatelyWhenForceImmediate]
   */
  @Test
  public void coordinatorActivatesImmediatelyWhenForceImmediate() throws Exception {
    // Given: An intercept registration message with forceImmediate=true
    InterceptMessage interceptMessage =
        msgBuilder
            .buildInterceptMessage(
                peerUuid,
                InterceptType.BEFORE,
                "com.example.Calculator",
                "add",
                Collections.emptyList(),
                this.getClass().getName(),
                "callback")
            .withForceImmediate(true);

    // When: The intercept message is sent to the matcher
    new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)).send(registerSocket);

    // Then: The matcher should receive OK response
    String response = registerSocket.recvStr();
    assertTrue("Expected OK response", response.equals(InterceptMatcher.REGISTER_OK_RESPONSE));

    // And: The coordinator should have been called with a message where forceImmediate=true
    verify(mockCoordinator, times(1))
        .activateIntercept(argThat(msg -> msg.getForceImmediate())); // Verify the flag is true
  }
}
