/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.rpc;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.serdes.colfer.MessageBuilder;
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
