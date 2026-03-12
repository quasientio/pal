/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link RpcPolicyChecker}, the singleton service that extracts metadata from {@code
 * ExecMessage} instances and delegates access decisions to {@link RpcPolicy}.
 *
 * <p>Tests verify that the checker correctly maps policy evaluation results to allow/deny behavior,
 * extracts class and member names from messages, maps {@code MessageType} to {@link
 * MemberCategory}, and exempts replay injection from policy checks.
 */
public class RpcPolicyCheckerTest {

  /** Log appender for capturing log output from {@link RpcPolicyChecker}. */
  private ListAppender<ILoggingEvent> listAppender;

  /** The logback logger for {@link RpcPolicyChecker}. */
  private Logger checkerLogger;

  /** Sets up the log capture appender before each test. */
  @Before
  public void setUp() {
    checkerLogger = (Logger) LoggerFactory.getLogger(RpcPolicyChecker.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    checkerLogger.addAppender(listAppender);
  }

  /** Tears down the log capture appender after each test. */
  @After
  public void tearDown() {
    checkerLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  /**
   * Verifies that no exception is thrown when the policy evaluates the operation to {@link
   * RpcPolicyAction#ALLOW}.
   */
  @Test
  public void shouldPassWhenPolicyReturnsAllow() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  /**
   * Verifies that {@link RpcAccessDeniedException} is thrown with correct className, memberName,
   * and channel when the policy evaluates to {@link RpcPolicyAction#DENY}.
   */
  @Test
  public void shouldThrowWhenPolicyReturnsDeny() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");
    try {
      checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getClassName(), is("com.example.Foo"));
      assertThat(e.getMemberName(), is("bar"));
      assertThat(e.getChannel(), is(MessageChannelType.ZMQ_SOCKET_RPC));
    }
  }

  /**
   * Verifies that {@link RpcPolicyAction#LOG_AND_ALLOW} does not throw an exception and that an
   * info-level log message is emitted.
   */
  @Test
  public void shouldLogAndPassForLogAndAllow() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**", null, RpcPolicyAction.LOG_AND_ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);

    assertThat(listAppender.list, hasSize(1));
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel(), is(Level.INFO));
    assertThat(event.getFormattedMessage().contains("com.example.Foo"), is(true));
    assertThat(event.getFormattedMessage().contains("bar"), is(true));
  }

  /**
   * Verifies that {@link RpcPolicyAction#LOG_AND_DENY} throws {@link RpcAccessDeniedException} and
   * that a warn-level log message is emitted.
   */
  @Test
  public void shouldLogAndThrowForLogAndDeny() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**", null, RpcPolicyAction.LOG_AND_DENY, null, null, null)),
            RpcPolicyAction.ALLOW);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");
    try {
      checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getClassName(), is("com.example.Foo"));
    }

    assertThat(listAppender.list, hasSize(1));
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel(), is(Level.WARN));
    assertThat(event.getFormattedMessage().contains("com.example.Foo"), is(true));
  }

  /**
   * Verifies that the checker extracts the fully-qualified class name from an {@code ExecMessage}
   * containing an instance method call and passes it to the policy's {@code evaluate()} method.
   */
  @Test
  public void shouldExtractClassnameFromExecMessage() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.Foo.**", null, RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "doSomething");

    // If the class name is correctly extracted, the rule matches and no exception is thrown.
    // If extraction fails, the default DENY action would cause an exception.
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  /**
   * Verifies that the checker extracts the member name from an {@code ExecMessage} containing an
   * instance method call and passes it to the policy's {@code evaluate()} method.
   */
  @Test
  public void shouldExtractMemberNameFromExecMessage() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.Foo", "specificMethod", RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "specificMethod");

    // Passes only if the member name "specificMethod" is correctly extracted
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);

    // A different member name should be denied
    ExecMessage wrongMsg = createInstanceMethodMessage("com.example.Foo", "otherMethod");
    try {
      checker.checkAccess(
          wrongMsg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException for non-matching member name");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getMemberName(), is("otherMethod"));
    }
  }

  /**
   * Verifies that the checker correctly maps {@code MessageType.EXEC_CONSTRUCTOR} to {@link
   * MemberCategory#CONSTRUCTOR} when delegating to the policy.
   */
  @Test
  public void shouldMapMessageTypeToMemberCategory() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.Foo.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    EnumSet.of(MemberCategory.CONSTRUCTOR),
                    null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createConstructorMessage("com.example.Foo");

    // The rule only allows CONSTRUCTOR category; passes only if MessageType is correctly mapped
    checker.checkAccess(msg, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  /**
   * Verifies that operations arriving via {@link MessageChannelType#REPLAY_INJECTION} are exempt
   * from policy checks, even when the policy default is DENY.
   */
  @Test
  public void shouldExemptReplayInjectionChannel() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");

    // Should not throw even though policy denies everything
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.REPLAY_INJECTION);
  }

  /**
   * Verifies that operations arriving via {@link MessageChannelType#CLI_RPC} are exempt from policy
   * checks, even when the policy default is DENY. CLI_RPC represents the peer invoking its own main
   * class — a local operation, not a remote call.
   */
  @Test
  public void shouldExemptCliRpcChannel() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createInstanceMethodMessage("com.example.Foo", "bar");

    // Should not throw even though policy denies everything
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.CLI_RPC);
  }

  /**
   * Verifies that {@link RpcPolicyChecker#isAccessible(String, String, MessageChannelType,
   * MemberCategory)} returns {@code true} for ALLOW and LOG_AND_ALLOW actions and {@code false} for
   * DENY and LOG_AND_DENY actions.
   */
  @Test
  public void shouldReturnCorrectAccessibilityForIsAccessible() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    assertThat(
        checker.isAccessible(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
    assertThat(
        checker.isAccessible(
            "com.other.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(false));
  }

  /**
   * Verifies that {@link RpcPolicyChecker#isAccessible(String, String, MessageChannelType,
   * MemberCategory)} returns {@code true} for {@link RpcPolicyAction#LOG_AND_ALLOW}.
   */
  @Test
  public void shouldReturnTrueForLogAndAllowInIsAccessible() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**", null, RpcPolicyAction.LOG_AND_ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    assertThat(
        checker.isAccessible(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
  }

  // ---------------------------------------------------------------------------
  // Visibility-aware policy evaluation tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that a non-public method is denied when the policy has an ALLOW rule restricted to
   * {@link MemberVisibility#PUBLIC} visibility with a default DENY action.
   */
  @Test
  public void shouldDenyNonPublicMethodWhenPolicyRequiresPublicVisibility() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    null,
                    EnumSet.of(MemberVisibility.PUBLIC))),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg =
        createInstanceMethodMessageWithModifiers("com.example.Foo", "bar", Modifier.PROTECTED);
    try {
      checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getClassName(), is("com.example.Foo"));
      assertThat(e.getMemberName(), is("bar"));
    }
  }

  /**
   * Verifies that a public method is allowed when the policy has an ALLOW rule restricted to {@link
   * MemberVisibility#PUBLIC} visibility with a default DENY action.
   */
  @Test
  public void shouldAllowPublicMethodWhenPolicyRequiresPublicVisibility() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    null,
                    EnumSet.of(MemberVisibility.PUBLIC))),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg =
        createInstanceMethodMessageWithModifiers("com.example.Foo", "bar", Modifier.PUBLIC);
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  /**
   * Verifies that visibility is not checked when no rules specify visibility restrictions, even for
   * private methods.
   */
  @Test
  public void shouldSkipVisibilityCheckWhenNoVisibilityRules() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg =
        createInstanceMethodMessageWithModifiers("com.example.Foo", "bar", Modifier.PRIVATE);
    // No exception: visibility is not checked because no rules have visibility constraints
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  /**
   * Verifies that the {@code deny-nonpublic} preset denies a package-private constructor (modifiers
   * = 0).
   */
  @Test
  public void shouldDenyPackagePrivateConstructorWhenDenyNonpublicPreset() {
    List<RpcPolicyRule> presetRules = RpcPolicyPresets.getDenyNonpublicRules();
    RpcPolicy policy = new RpcPolicy(presetRules, RpcPolicyAction.ALLOW);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg = createConstructorMessageWithModifiers("com.example.Foo", 0);
    try {
      checker.checkAccess(msg, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getClassName(), is("com.example.Foo"));
    }
  }

  /**
   * Verifies that exempt channels (e.g. {@link MessageChannelType#REPLAY_INJECTION}) still bypass
   * all policy checks, even when visibility rules are active.
   */
  @Test
  public void shouldStillExemptReplayInjectionChannel() {
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    null,
                    EnumSet.of(MemberVisibility.PUBLIC))),
            RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(policy);

    ExecMessage msg =
        createInstanceMethodMessageWithModifiers("com.example.Foo", "bar", Modifier.PRIVATE);
    // No exception: REPLAY_INJECTION channel is exempt from all policy checks
    checker.checkAccess(msg, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.REPLAY_INJECTION);
  }

  /**
   * Verifies that {@link RpcPolicyChecker} picks up an updated policy after the underlying {@link
   * RpcPolicyHolder} is swapped from deny-all to allow-all.
   */
  @Test
  @Ignore("Awaiting implementation in #1133")
  public void shouldPickUpUpdatedPolicyAfterHolderSwap() {
    // Given: An RpcPolicyHolder initialized with a deny-all policy, and an RpcPolicyChecker
    //        constructed with that holder
    // When: The holder's policy is swapped to an allow-all policy via updatePolicy()
    // Then: checkAccess() with the same message that was previously denied now passes
    //       without throwing RpcAccessDeniedException

    // TODO(#1133): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Creates an {@link ExecMessage} containing an {@link InstanceMethodCall} with the given class
   * name and method name.
   *
   * @param className the fully-qualified class name
   * @param methodName the method name
   * @return the constructed message
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createInstanceMethodMessage(String className, String methodName) {
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.name = className;

    InstanceMethodCall call = new InstanceMethodCall();
    call.clazz = clazz;
    call.name = methodName;

    ExecMessage msg = new ExecMessage();
    msg.instanceMethodCall = call;
    return msg;
  }

  /**
   * Creates an {@link ExecMessage} containing a {@link ConstructorCall} with the given class name.
   *
   * @param className the fully-qualified class name
   * @return the constructed message
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createConstructorMessage(String className) {
    return createConstructorMessageWithModifiers(className, 0);
  }

  /**
   * Creates an {@link ExecMessage} containing an {@link InstanceMethodCall} with the given class
   * name, method name, and Java modifiers.
   *
   * @param className the fully-qualified class name
   * @param methodName the method name
   * @param modifiers the Java modifier bitmask (from {@link Modifier})
   * @return the constructed message
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createInstanceMethodMessageWithModifiers(
      String className, String methodName, int modifiers) {
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.name = className;

    InstanceMethodCall call = new InstanceMethodCall();
    call.clazz = clazz;
    call.name = methodName;
    call.modifiers = modifiers;

    ExecMessage msg = new ExecMessage();
    msg.instanceMethodCall = call;
    return msg;
  }

  /**
   * Creates an {@link ExecMessage} containing a {@link ConstructorCall} with the given class name
   * and Java modifiers.
   *
   * @param className the fully-qualified class name
   * @param modifiers the Java modifier bitmask (from {@link Modifier})
   * @return the constructed message
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createConstructorMessageWithModifiers(
      String className, int modifiers) {
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.name = className;

    ConstructorCall call = new ConstructorCall();
    call.clazz = clazz;
    call.modifiers = modifiers;

    ExecMessage msg = new ExecMessage();
    msg.constructorCall = call;
    return msg;
  }
}
