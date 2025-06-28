/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

public class AbstractRpcMessageIT extends AbstractIntegrationTest {

  protected static final UUID clientId = UUID.randomUUID();
  protected static MessageBuilder messageBuilder;
  protected static ThinPeer thinPeer;
  protected static DirectoryConnectionProvider directoryConnectionProvider;

  protected final TargetType targetType;

  public enum TargetType {
    PEER,
    LOG
  }

  protected AbstractRpcMessageIT(TargetType targetType) {
    this.targetType = targetType;
  }

  protected static Collection<Object[]> getSendTargetParameters() {
    return Arrays.asList(new Object[][] {{TargetType.LOG}, {TargetType.PEER}});
  }
}
