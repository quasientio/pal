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
