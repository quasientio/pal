/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.rpc.json;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import net.ittera.pal.AbstractIntegrationTest;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.RPCType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJsonRPCMessageIT extends AbstractIntegrationTest {
  protected static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final UUID clientId = UUID.randomUUID();
  protected static MessageBuilder messageBuilder;
  protected static ThinPeer thinPeer;

  @BeforeClass
  public static void initialize() throws Exception {

    // configure wiring
    AbstractModule module =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Properties appProperties = new Properties();
            appProperties.setProperty("messages.with_src_context", Boolean.toString(false));
            Names.bindProperties(binder(), appProperties);
            bind(ObjectLookupStore.class)
                .to(ConcurrentHashMapObjectLookupStore.class)
                .asEagerSingleton();
            bind(MessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    // find a peer listening with JSON-RPC enabled
    PeerInfo jsonRpcPeer =
        findRPCPeer(RPCType.JSONRPC)
            .orElseThrow(() -> new RuntimeException("No peer found with JSON-RPC enabled"));
    thinPeer =
        new ThinPeer()
            .withUUID(clientId)
            .withDirectoryURL(getPALDirectoryURL())
            .withInitialPeer(jsonRpcPeer)
            .withOutboundRPCType(RPCType.JSONRPC)
            .init();
  }

  private JsonRpcResponse sendAndReceive(JsonRpcRequest jsonRpcRequest)
      throws ExecutionException, InterruptedException {
    logger.debug("Sending JSON-RPC request: {}", jsonRpcRequest);
    final JsonRpcResponse response;
    try {
      response = thinPeer.sendAndReceive(jsonRpcRequest, JsonRpcRequest.class).get();
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving message with uuid: {}\n{}",
          jsonRpcRequest.getId(),
          jsonRpcRequest,
          e);
      throw e;
    }
    return response;
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  /** Helper methods */
  protected String callEmptyConstructor(String className) throws Exception {
    JsonRpcRequest jsonRpc = new JsonRpcRequest();
    jsonRpc.setJsonrpc("2.0");
    jsonRpc.setId(UUID.randomUUID().toString());
    jsonRpc.setMethod("new:" + className);
    JsonRpcResponse replyMsg = sendAndReceive(jsonRpc);

    // basic assertions
    /**
     * if (expectedThrowableType != null) { assertHasThrowableOfType(replyMsg,
     * expectedThrowableType); } else { assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
     * assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className); }
     */
    return replyMsg.getResult();
  }
}
