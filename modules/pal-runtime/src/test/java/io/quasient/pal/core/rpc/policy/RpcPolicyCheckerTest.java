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

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link RpcPolicyChecker}, the singleton service that extracts metadata from {@code
 * ExecMessage} instances and delegates access decisions to {@link RpcPolicy}.
 *
 * <p>Tests verify that the checker correctly maps policy evaluation results to allow/deny behavior,
 * extracts class and member names from messages, maps {@code MessageType} to {@link
 * MemberCategory}, and exempts replay injection from policy checks.
 */
public class RpcPolicyCheckerTest {

  /**
   * Verifies that no exception is thrown when the policy evaluates the operation to {@link
   * RpcPolicyAction#ALLOW}.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldPassWhenPolicyReturnsAllow() {
    // Given: Policy that evaluates to ALLOW
    // When: checkAccess(msg, EXEC_INSTANCE_METHOD, ZMQ_SOCKET_RPC)
    // Then: No exception thrown

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@link RpcAccessDeniedException} is thrown with correct className, memberName,
   * and channel when the policy evaluates to {@link RpcPolicyAction#DENY}.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldThrowWhenPolicyReturnsDeny() {
    // Given: Policy that evaluates to DENY
    // When: checkAccess(msg, EXEC_INSTANCE_METHOD, ZMQ_SOCKET_RPC)
    // Then: Throws RpcAccessDeniedException with correct className, memberName, channel

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@link RpcPolicyAction#LOG_AND_ALLOW} does not throw an exception and that an
   * info-level log message is emitted.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldLogAndPassForLogAndAllow() {
    // Given: Policy that evaluates to LOG_AND_ALLOW
    // When: checkAccess(...)
    // Then: No exception; logger.info called (verify via mock/test logger)

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@link RpcPolicyAction#LOG_AND_DENY} throws {@link RpcAccessDeniedException} and
   * that a warn-level log message is emitted.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldLogAndThrowForLogAndDeny() {
    // Given: Policy that evaluates to LOG_AND_DENY
    // When: checkAccess(...)
    // Then: Throws RpcAccessDeniedException; logger.warn called

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the checker extracts the fully-qualified class name from an {@code ExecMessage}
   * containing an instance method call and passes it to the policy's {@code evaluate()} method.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldExtractClassnameFromExecMessage() {
    // Given: ExecMessage with instanceMethodCall containing class "com.example.Foo"
    // When: checkAccess is called
    // Then: Policy.evaluate() receives "com.example.Foo" as className

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the checker extracts the member name from an {@code ExecMessage} containing an
   * instance method call and passes it to the policy's {@code evaluate()} method.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldExtractMemberNameFromExecMessage() {
    // Given: ExecMessage with instanceMethodCall named "bar"
    // When: checkAccess is called
    // Then: Policy.evaluate() receives "bar" as memberName

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the checker correctly maps {@code MessageType.EXEC_CONSTRUCTOR} to {@link
   * MemberCategory#CONSTRUCTOR} when delegating to the policy.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldMapMessageTypeToMemberCategory() {
    // Given: MessageType.EXEC_CONSTRUCTOR
    // When: checkAccess with this type
    // Then: Policy.evaluate() receives MemberCategory.CONSTRUCTOR

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that operations arriving via {@link
   * io.quasient.pal.core.transport.MessageChannelType#REPLAY_INJECTION} are exempt from policy
   * checks, even when the policy default is DENY.
   */
  @Test
  @Ignore("Awaiting implementation in #997")
  public void shouldExemptReplayInjectionChannel() {
    // Given: Policy with DENY default
    // When: checkAccess with MessageChannelType.REPLAY_INJECTION
    // Then: No exception (replay is exempt from policy)

    // TODO(#997): Implement test logic
    fail("Not yet implemented");
  }
}
