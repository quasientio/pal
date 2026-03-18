/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.util.EnumSet;
import org.junit.Test;

/**
 * Unit tests for {@code RecordingScopeRule}, the atomic pattern-matching unit of the recording
 * scope system. Each rule combines an Ant-style class pattern, a member pattern, a {@code
 * RecordingScopeAction} (RECORD or SKIP), and an optional {@code MemberCategory} filter set.
 *
 * <p>Pattern matching uses {@code AntPathMatcherArrays} with dot-separated paths and case
 * insensitivity, following the same conventions as {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicyRule}.
 *
 * @see io.quasient.pal.core.rpc.policy.RpcPolicyRule
 */
public class RecordingScopeRuleTest {

  /**
   * Verifies that a rule with exact class and member patterns matches the correct operation and
   * rejects a different member on the same class.
   */
  @Test
  public void exactClassAndMemberMatch() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.Foo", "bar", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.Foo", "baz", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that the double-star wildcard member pattern ({@code **}) matches all member names on
   * the specified class, including constructors represented as {@code new}.
   */
  @Test
  public void wildcardMemberMatchesAllMembers() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.Foo", "**", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.Foo", "baz", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.Foo", "new", MemberCategory.CONSTRUCTOR), is(true));
  }

  /**
   * Verifies that the multi-segment class wildcard ({@code com.example.**}) matches classes in
   * nested sub-packages but does not match classes in unrelated packages.
   */
  @Test
  public void multiSegmentWildcardClass() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.sub.pkg.Bar", "baz", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.other.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that the single-segment class wildcard ({@code com.example.*}) matches classes
   * directly under the package but does not match classes in nested sub-packages.
   */
  @Test
  public void singleSegmentWildcardClass() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.*", "**", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.sub.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a prefix wildcard on the member pattern (e.g., {@code get*}) matches members
   * starting with that prefix but rejects members with a different prefix.
   */
  @Test
  public void wildcardMemberPrefix() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.Foo", "get*", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "getName", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.Foo", "setName", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a rule with a specific {@code MemberCategory} filter set matches only operations
   * of that category and rejects operations of a different category on the same path.
   */
  @Test
  public void categoryFilterMatchesCorrectCategory() {
    RecordingScopeRule rule =
        new RecordingScopeRule(
            "com.example.**",
            "**",
            RecordingScopeAction.SKIP,
            EnumSet.of(MemberCategory.FIELD_GET));

    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.FIELD_GET), is(true));
    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a rule with a null category filter set matches all {@code MemberCategory} values,
   * including METHOD, FIELD_GET, FIELD_SET, CONSTRUCTOR, and STATIC_METHOD.
   */
  @Test
  public void nullCategoryFilterMatchesAllCategories() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.FIELD_GET), is(true));
    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.FIELD_SET), is(true));
    assertThat(rule.matches("com.example.Foo", "new", MemberCategory.CONSTRUCTOR), is(true));
    assertThat(rule.matches("com.example.Foo", "bar", MemberCategory.STATIC_METHOD), is(true));
  }

  /**
   * Verifies that pattern matching is case insensitive: a rule defined with mixed-case class and
   * member patterns matches paths in different casing.
   */
  @Test
  public void caseInsensitiveMatching() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.Example.Foo", "Bar", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.foo", "bar", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies that constructors are matched using the member name {@code new}. When combined with a
   * {@code CONSTRUCTOR} category filter, only constructor operations match; the same path with a
   * non-constructor category is rejected.
   */
  @Test
  public void constructorNameIsNew() {
    RecordingScopeRule rule =
        new RecordingScopeRule(
            "com.example.Foo",
            "new",
            RecordingScopeAction.RECORD,
            EnumSet.of(MemberCategory.CONSTRUCTOR));

    assertThat(rule.matches("com.example.Foo", "new", MemberCategory.CONSTRUCTOR), is(true));
    assertThat(rule.matches("com.example.Foo", "new", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that field name patterns work with both {@code FIELD_GET} and {@code FIELD_SET}
   * categories. A wildcard member pattern like {@code total*} should match field names starting
   * with "total" regardless of the field operation direction.
   */
  @Test
  public void fieldNamePatternMatching() {
    RecordingScopeRule rule =
        new RecordingScopeRule("com.example.Order", "total*", RecordingScopeAction.RECORD, null);

    assertThat(rule.matches("com.example.Order", "totalPrice", MemberCategory.FIELD_GET), is(true));
    assertThat(rule.matches("com.example.Order", "totalPrice", MemberCategory.FIELD_SET), is(true));
  }

  /**
   * Verifies that the {@code getAction()} accessor returns the correct {@code RecordingScopeAction}
   * for both RECORD and SKIP rules.
   */
  @Test
  public void actionIsPreserved() {
    RecordingScopeRule recordRule =
        new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null);
    RecordingScopeRule skipRule =
        new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.SKIP, null);

    assertThat(recordRule.getAction(), is(RecordingScopeAction.RECORD));
    assertThat(skipRule.getAction(), is(RecordingScopeAction.SKIP));
  }

  /**
   * Verifies that a rule with multiple categories in the filter set (e.g., {@code [FIELD_GET,
   * FIELD_SET]}) matches operations of any included category and rejects operations of excluded
   * categories.
   */
  @Test
  public void multipleCategoriesInSet() {
    RecordingScopeRule rule =
        new RecordingScopeRule(
            "com.example.**",
            "**",
            RecordingScopeAction.SKIP,
            EnumSet.of(MemberCategory.FIELD_GET, MemberCategory.FIELD_SET));

    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.FIELD_GET), is(true));
    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.FIELD_SET), is(true));
    assertThat(rule.matches("com.example.Foo", "value", MemberCategory.METHOD), is(false));
  }
}
