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
 * Unit tests for {@link MemberCategory} and {@link RpcPolicyAction} enums.
 *
 * <p>Verifies that {@code MemberCategory.fromMessageType()} correctly maps each execution {@code
 * MessageType} to its corresponding member category, and that both enums contain exactly the
 * expected set of values.
 */
public class MemberCategoryTest {

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecConstructorToConstructor() {
    // Given: MessageType.EXEC_CONSTRUCTOR
    // When: MemberCategory.fromMessageType(EXEC_CONSTRUCTOR)
    // Then: returns MemberCategory.CONSTRUCTOR

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecInstanceMethodToMethod() {
    // Given: MessageType.EXEC_INSTANCE_METHOD
    // When: MemberCategory.fromMessageType(EXEC_INSTANCE_METHOD)
    // Then: returns MemberCategory.METHOD

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecClassMethodToStaticMethod() {
    // Given: MessageType.EXEC_CLASS_METHOD
    // When: MemberCategory.fromMessageType(EXEC_CLASS_METHOD)
    // Then: returns MemberCategory.STATIC_METHOD

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecGetFieldToFieldGet() {
    // Given: MessageType.EXEC_GET_FIELD
    // When: MemberCategory.fromMessageType(EXEC_GET_FIELD)
    // Then: returns MemberCategory.FIELD_GET

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecPutFieldToFieldSet() {
    // Given: MessageType.EXEC_PUT_FIELD
    // When: MemberCategory.fromMessageType(EXEC_PUT_FIELD)
    // Then: returns MemberCategory.FIELD_SET

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecGetStaticToFieldGet() {
    // Given: MessageType.EXEC_GET_STATIC
    // When: MemberCategory.fromMessageType(EXEC_GET_STATIC)
    // Then: returns MemberCategory.FIELD_GET

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldMapExecPutStaticToFieldSet() {
    // Given: MessageType.EXEC_PUT_STATIC
    // When: MemberCategory.fromMessageType(EXEC_PUT_STATIC)
    // Then: returns MemberCategory.FIELD_SET

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldThrowForUnsupportedMessageType() {
    // Given: MessageType.EXEC_RETURN_VALUE (a response type, not a request type)
    // When: MemberCategory.fromMessageType(EXEC_RETURN_VALUE)
    // Then: throws IllegalArgumentException

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #987")
  public void shouldHaveAllExpectedValues() {
    // Then: RpcPolicyAction has exactly ALLOW, DENY, LOG_AND_ALLOW, LOG_AND_DENY
    // Then: MemberCategory has exactly METHOD, STATIC_METHOD, CONSTRUCTOR, FIELD_GET, FIELD_SET

    // TODO(#987): Implement test logic
    fail("Not yet implemented");
  }
}
