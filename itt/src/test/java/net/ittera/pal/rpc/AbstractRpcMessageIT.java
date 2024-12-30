package net.ittera.pal.rpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import net.ittera.pal.AbstractIntegrationTest;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.serdes.colfer.MessageBuilder;

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
