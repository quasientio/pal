/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link DuplicatePeerNameException}.
 *
 * <p>Peer name resolution via {@link PalDirectory#getPeerByName} now uses the by-name reverse index
 * in etcd and is exercised by the integration tests in {@code PalDirectoryIT}. These unit tests
 * cover the exception class that guards uniqueness at creation time.
 */
public class PeerNameResolutionTest {

  /** Verifies that the exception carries the supplied message. */
  @Test
  public void duplicatePeerNameException_carriesMessage() {
    String msg = "Peer name \"foo\" is already registered by peer abc-123";
    DuplicatePeerNameException ex = new DuplicatePeerNameException(msg);
    assertThat(ex.getMessage(), is(msg));
  }

  /** Verifies that DuplicatePeerNameException is a checked exception (extends Exception). */
  @Test
  public void duplicatePeerNameException_isCheckedException() {
    assertThat(
        "Should extend Exception",
        Exception.class.isAssignableFrom(DuplicatePeerNameException.class),
        is(true));
    assertThat(
        "Should not extend RuntimeException",
        RuntimeException.class.isAssignableFrom(DuplicatePeerNameException.class),
        is(false));
  }

  /** Verifies that the exception cause is null by default. */
  @Test
  public void duplicatePeerNameException_causeIsNull() {
    DuplicatePeerNameException ex = new DuplicatePeerNameException("test");
    assertThat(ex.getCause(), is(nullValue()));
  }
}
