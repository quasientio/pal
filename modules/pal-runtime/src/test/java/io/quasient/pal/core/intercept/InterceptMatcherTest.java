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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InterceptEventMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
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

/** This class tests both intercept registration and intercept matching. */
public class InterceptMatcherTest extends ZmqEnabledTest {

  private UUID peerUuid;
  private static final String INTERCEPT_REG_ADDRESS = "inproc://intercepts.reg";
  private ZContext context;
  private ServiceManager manager;
  private InterceptMatcher interceptMatcher;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private Socket registerSocket;

  @Before
  public void setup() throws InterruptedException {
    this.peerUuid = UUID.randomUUID();
    this.context = createContext();

    // Create the InterceptMatcher first so we can reference it in the mock
    // We'll set the coordinator after creating the matcher
    this.interceptMatcher =
        new InterceptMatcher(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "InterceptMatcherTest-Service",
            INTERCEPT_REG_ADDRESS,
            createTestCoordinator());

    final Set<Service> services = new HashSet<>(List.of(this.interceptMatcher));
    this.manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);

    // create REQ socket to simulate requests (IRL: InterceptNodeListener)
    registerSocket = context.createSocket(SocketType.REQ);
    registerSocket.connect(INTERCEPT_REG_ADDRESS);
  }

  /**
   * Creates a test coordinator that registers intercepts immediately without drain.
   *
   * <p>The mock coordinator is configured to call the matcher's registerInterceptRequest method
   * directly when activateIntercept is called, simulating immediate activation without drain.
   */
  private InterceptActivationCoordinator createTestCoordinator() throws InterruptedException {
    InterceptActivationCoordinator mockCoordinator = mock(InterceptActivationCoordinator.class);
    when(mockCoordinator.activateIntercept(any()))
        .thenAnswer(
            invocation -> {
              // Get the InterceptMessage argument
              InterceptMessage msg = invocation.getArgument(0);
              try {
                // Call registerInterceptRequest directly on the matcher
                interceptMatcher.registerInterceptRequest(msg);
                return InterceptActivationCoordinator.ActivationResult.success(
                    "Intercept activated in test");
              } catch (DuplicateInterceptException e) {
                return InterceptActivationCoordinator.ActivationResult.failure(
                    "Duplicate intercept: " + e.getMessage());
              } catch (Exception e) {
                return InterceptActivationCoordinator.ActivationResult.failure(
                    "Error: " + e.getMessage());
              }
            });
    return mockCoordinator;
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

  @Test
  public void registerNewIntercept() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    byte[] buf = new byte[interceptMessage.marshalFit()];
    interceptMessage.marshal(buf, 0);
    new InterceptEventMsg(buf).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));
  }

  @Test
  public void registerDuplicateIntercept() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));

    // now send again
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_DUP_RESPONSE));
  }

  @Test
  public void registerNewInterceptThenNonMatchingExecMessage() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));

    // create a non-matching ExecMessage
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.HashMap");

    // Extract matching info from ExecMessage
    String className = ExecMessageUtils.getClassname(execMessage);
    String executableName = ExecMessageUtils.getExecutableName(execMessage);
    String[] parameterTypes =
        ExecMessageUtils.getParameterTypes(execMessage).toArray(new String[0]);

    // verify it doesn't get intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(
            className,
            executableName,
            parameterTypes,
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE);
    assertThat(matchingIntercepts, is(empty()));
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageWithWrongPhase() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));

    // create a matching ExecMessage with non-matching phase (ExecPhase = AFTER)
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");

    // Extract matching info from ExecMessage
    String className = ExecMessageUtils.getClassname(execMessage);
    String executableName = ExecMessageUtils.getExecutableName(execMessage);
    String[] parameterTypes =
        ExecMessageUtils.getParameterTypes(execMessage).toArray(new String[0]);

    // verify it doesn't get intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(
            className,
            executableName,
            parameterTypes,
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.AFTER);
    assertThat(matchingIntercepts, is(empty()));
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageAndPhase() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));

    // now send a matching ExecMessage
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");

    // Extract matching info from ExecMessage
    String className = ExecMessageUtils.getClassname(execMessage);
    String executableName = ExecMessageUtils.getExecutableName(execMessage);
    String[] parameterTypes =
        ExecMessageUtils.getParameterTypes(execMessage).toArray(new String[0]);

    // verify that it gets intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(
            className,
            executableName,
            parameterTypes,
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE);
    assertThat(matchingIntercepts, is(not(empty())));
    assertThat(matchingIntercepts.size(), is(1));
    assertThat(matchingIntercepts.get(0), is(interceptMessage));
  }

  @Test
  public void registerNewInterceptThenUnregister() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    final String interceptId = interceptMessage.getMessageId();
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify response
    String response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.REGISTER_OK_RESPONSE));

    // now unregister
    new InterceptEventMsg(interceptId).send(registerSocket);

    // verify response
    response = registerSocket.recvStr();
    assertThat(response, is(InterceptMatcher.UNREGISTER_OK_RESPONSE));
  }
}
