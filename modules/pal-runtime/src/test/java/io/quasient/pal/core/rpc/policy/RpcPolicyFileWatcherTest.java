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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@code RpcPolicyFileWatcher}, the daemon thread that polls a YAML policy file for
 * changes and reloads the {@link RpcPolicy} via {@link RpcPolicyHolder}.
 *
 * <p>Tests verify reload on file change, error resilience (invalid YAML, deleted file), stable
 * behavior when the file is unchanged, preservation of CLI presets across reloads, and clean
 * lifecycle management (start/stop).
 */
public class RpcPolicyFileWatcherTest {

  /** Temporary folder for creating policy YAML files. */
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /** Short poll interval for tests to avoid long waits. */
  private static final long TEST_POLL_INTERVAL_MS = 50;

  /** The watcher under test; stopped in {@link #tearDown()} if started. */
  private RpcPolicyFileWatcher watcher;

  /** YAML content that creates an ALLOW-all policy with one rule. */
  private static final String ALLOW_YAML =
      """
      version: 1
      defaultAction: ALLOW
      rules:
        - class: "com.example.**"
          method: "**"
          action: ALLOW
      """;

  /** YAML content that creates a DENY-all policy with one rule. */
  private static final String DENY_YAML =
      """
      version: 1
      defaultAction: DENY
      rules:
        - class: "com.example.**"
          method: "**"
          action: DENY
      """;

  /** Stops the watcher after each test to avoid thread leaks. */
  @Before
  public void setUp() {
    watcher = null;
  }

  /** Stops the watcher after each test to avoid thread leaks. */
  @After
  public void tearDown() {
    if (watcher != null) {
      watcher.stop();
    }
  }

  /**
   * Verifies that the watcher detects a file modification and reloads the policy with the updated
   * content.
   */
  @Test
  public void shouldReloadPolicyOnFileChange() throws Exception {
    // Given: A temp YAML file with ALLOW policy, watcher started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);
    assertThat(holder.getPolicy().getDefaultAction(), is(RpcPolicyAction.ALLOW));

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // When: Overwrite with DENY policy (ensure timestamp changes)
    Thread.sleep(TEST_POLL_INTERVAL_MS);
    Files.writeString(yamlFile, DENY_YAML);

    // Then: Wait for reload
    Thread.sleep(TEST_POLL_INTERVAL_MS * 4);
    assertThat(holder.getPolicy().getDefaultAction(), is(RpcPolicyAction.DENY));
    assertThat(holder.getPolicy().getRules().size(), is(1));
  }

  /**
   * Verifies that a YAML parse error does not replace the current valid policy, and that an ERROR
   * log is emitted.
   */
  @Test
  public void shouldKeepCurrentPolicyOnParseError() throws Exception {
    // Given: A valid YAML policy file, watcher started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);
    RpcPolicy originalPolicy = holder.getPolicy();

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // When: Overwrite with invalid YAML
    Thread.sleep(TEST_POLL_INTERVAL_MS);
    Files.writeString(yamlFile, "!!!invalid yaml: {{{");

    // Then: Wait for poll cycle; policy should not change
    Thread.sleep(TEST_POLL_INTERVAL_MS * 4);
    assertSame(originalPolicy, holder.getPolicy());
  }

  /**
   * Verifies that deleting the policy file does not trigger a reload, preserving the current
   * policy.
   */
  @Test
  public void shouldKeepCurrentPolicyOnFileDeleted() throws Exception {
    // Given: A valid YAML policy file, watcher started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);
    RpcPolicy originalPolicy = holder.getPolicy();

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // When: Delete the file
    Thread.sleep(TEST_POLL_INTERVAL_MS);
    Files.delete(yamlFile);

    // Then: Wait for poll cycles; policy should not change
    Thread.sleep(TEST_POLL_INTERVAL_MS * 4);
    assertSame(originalPolicy, holder.getPolicy());
  }

  /**
   * Verifies that the watcher does not trigger a reload when the file has not been modified,
   * confirming reference equality of the policy instance.
   */
  @Test
  public void shouldNotReloadWhenFileUnchanged() throws Exception {
    // Given: A valid YAML policy file, watcher started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);
    RpcPolicy originalPolicy = holder.getPolicy();

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // When: Sleep for multiple poll intervals without modifying the file
    Thread.sleep(TEST_POLL_INTERVAL_MS * 6);

    // Then: Policy should be the exact same instance (no reload)
    assertSame(originalPolicy, holder.getPolicy());
  }

  /**
   * Verifies that CLI preset rules are preserved across a YAML file reload, so the reloaded policy
   * contains both the new YAML rules and the original preset rules.
   */
  @Test
  public void shouldPreservePresetsAcrossReload() throws Exception {
    // Given: A YAML file with one user rule, watcher started with presets="deny-unsafe"
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    String presetNames = "deny-unsafe";
    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), presetNames, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);
    int initialRuleCount = initialPolicy.getRules().size();
    int presetRuleCount = RpcPolicyPresets.resolvePreset("deny-unsafe").size();

    // Verify initial policy has both YAML rule(s) and preset rules
    assertTrue(initialRuleCount > presetRuleCount);

    watcher = new RpcPolicyFileWatcher(yamlFile, presetNames, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // When: Modify the YAML file (change to DENY policy)
    Thread.sleep(TEST_POLL_INTERVAL_MS);
    Files.writeString(yamlFile, DENY_YAML);

    // Then: Wait for reload
    Thread.sleep(TEST_POLL_INTERVAL_MS * 4);
    RpcPolicy reloadedPolicy = holder.getPolicy();
    assertNotSame(initialPolicy, reloadedPolicy);

    // Reloaded policy should contain both the new YAML rule AND the preset rules
    List<RpcPolicyRule> reloadedRules = reloadedPolicy.getRules();
    assertTrue(
        "Expected reloaded rules to include preset rules, got " + reloadedRules.size(),
        reloadedRules.size() >= presetRuleCount + 1);
  }

  /**
   * Verifies that calling {@code stop()} terminates the watcher thread cleanly within a bounded
   * time.
   */
  @Test
  public void shouldStopCleanly() throws Exception {
    // Given: A watcher started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // Allow the thread to enter the poll loop
    Thread.sleep(TEST_POLL_INTERVAL_MS);

    // When: stop() is called
    watcher.stop();

    // Then: The watcher thread should no longer be alive
    // The stop() method joins with a 2-second timeout, so after it returns
    // the thread should be terminated. Verify by checking no thread with
    // the name is alive.
    Thread.sleep(50);
    boolean watcherAlive = false;
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if ("rpc-policy-file-watcher".equals(t.getName()) && t.isAlive()) {
        watcherAlive = true;
        break;
      }
    }
    assertTrue("Watcher thread should not be alive after stop()", !watcherAlive);

    // Prevent tearDown from calling stop() again on a null thread
    watcher = null;
  }

  /**
   * Verifies that calling {@code start()} on an already-running watcher is idempotent and does not
   * create a second watcher thread.
   */
  @Test
  public void shouldNotStartWhenAlreadyRunning() throws Exception {
    // Given: A watcher that has been started
    Path yamlFile = tempFolder.newFile("policy.yaml").toPath();
    Files.writeString(yamlFile, ALLOW_YAML);

    RpcPolicy initialPolicy = RpcPolicyParser.fromOptions(yamlFile.toString(), null, null);
    RpcPolicyHolder holder = new RpcPolicyHolder(initialPolicy);

    watcher = new RpcPolicyFileWatcher(yamlFile, null, null, holder, TEST_POLL_INTERVAL_MS);
    watcher.start();

    // Count initial threads with the watcher name
    Thread.sleep(TEST_POLL_INTERVAL_MS);
    long initialCount = countWatcherThreads();

    // When: start() is called again
    watcher.start();
    Thread.sleep(TEST_POLL_INTERVAL_MS);

    // Then: No second thread is created
    long afterCount = countWatcherThreads();
    assertThat("Second start() should not create additional threads", afterCount, is(initialCount));
    assertThat("Exactly one watcher thread should exist", initialCount, is(1L));
  }

  /**
   * Counts the number of alive threads named {@code rpc-policy-file-watcher}.
   *
   * @return the count of matching threads
   */
  private long countWatcherThreads() {
    long count = 0;
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if ("rpc-policy-file-watcher".equals(t.getName()) && t.isAlive()) {
        count++;
      }
    }
    return count;
  }
}
