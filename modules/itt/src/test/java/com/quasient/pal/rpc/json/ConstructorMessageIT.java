/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class ConstructorMessageIT extends AbstractJsonRpcMessageIT {

  protected final String className = "com.quasient.pal.apps.rpc.Constructors";

  public ConstructorMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void constructor_publicNoArgs_newObjectReturned() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }

  @Test
  public void constructor_publicOneArg_newObjectReturned() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "int", "value": 5}
                        ]
                      }
                    }
                    """
            .formatted(className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }

  @Test
  public void constructor_privateOneArgArray_newObjectReturned() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "String[]", "value": ["Aa", "Bb", "Cc"]}
                        ]
                      }
                    }
                    """
            .formatted(className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }

  @Test
  public void constructor_publicOneArgNull_newObjectReturned() throws Exception {
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "java.lang.Integer", "value": null}
                        ]
                      }
                    }
                    """
            .formatted(className);

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }

  @Test
  public void constructor_protectedOneArgRef_newObjectReturned() throws Exception {

    // 1. Construct an instance calling no-args constructor
    String request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(className);

    JsonRpcResponse responseMessage = sendAndReceive(request);
    // assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    Integer instanceId = responseMessage.getResult().getValue().getRef();
    assertNotNull(instanceId);

    // 2. Construct an instance calling the constructor that takes another instance as arg
    request =
            """
                     {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"ref": %d}
                        ]
                      }
                    }
                    """
            .formatted(className, instanceId);
    responseMessage = sendAndReceive(request);
    logger.debug("responseMessage: {}", responseMessage);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());
    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }
}
