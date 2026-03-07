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
import static org.junit.Assert.fail;

import io.quasient.pal.messages.types.MessageType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
  public void shouldMapExecConstructorToConstructor() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_CONSTRUCTOR),
        is(MemberCategory.CONSTRUCTOR));
  }

  @Test
  public void shouldMapExecInstanceMethodToMethod() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_INSTANCE_METHOD),
        is(MemberCategory.METHOD));
  }

  @Test
  public void shouldMapExecClassMethodToStaticMethod() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_CLASS_METHOD),
        is(MemberCategory.STATIC_METHOD));
  }

  @Test
  public void shouldMapExecGetFieldToFieldGet() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_GET_FIELD), is(MemberCategory.FIELD_GET));
  }

  @Test
  public void shouldMapExecPutFieldToFieldSet() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_PUT_FIELD), is(MemberCategory.FIELD_SET));
  }

  @Test
  public void shouldMapExecGetStaticToFieldGet() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_GET_STATIC), is(MemberCategory.FIELD_GET));
  }

  @Test
  public void shouldMapExecPutStaticToFieldSet() {
    assertThat(
        MemberCategory.fromMessageType(MessageType.EXEC_PUT_STATIC), is(MemberCategory.FIELD_SET));
  }

  @Test
  public void shouldThrowForUnsupportedMessageType() {
    try {
      MemberCategory.fromMessageType(MessageType.EXEC_RETURN_VALUE);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage().contains("EXEC_RETURN_VALUE"), is(true));
    }
  }

  @Test
  public void shouldHaveAllExpectedValues() {
    Set<String> policyActions =
        Arrays.stream(RpcPolicyAction.values()).map(Enum::name).collect(Collectors.toSet());
    assertThat(policyActions, is(Set.of("ALLOW", "DENY", "LOG_AND_ALLOW", "LOG_AND_DENY")));

    Set<String> memberCategories =
        Arrays.stream(MemberCategory.values()).map(Enum::name).collect(Collectors.toSet());
    assertThat(
        memberCategories,
        is(Set.of("METHOD", "STATIC_METHOD", "CONSTRUCTOR", "FIELD_GET", "FIELD_SET")));
  }
}
