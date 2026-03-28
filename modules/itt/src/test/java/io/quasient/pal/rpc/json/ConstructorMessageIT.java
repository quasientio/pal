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
package io.quasient.pal.rpc.json;

import static org.junit.Assert.assertNotNull;

import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class ConstructorMessageIT extends AbstractJsonRpcMessageIT {

  protected final String className = "io.quasient.foobar.apps.quantized.rpc.Constructors";

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(generateId(), className);

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "int", "value": 5}
                        ]
                      }
                    }
                    """
            .formatted(generateId(), className);

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "String[]", "value": ["Aa", "Bb", "Cc"]}
                        ]
                      }
                    }
                    """
            .formatted(generateId(), className);

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"type": "java.lang.Integer", "value": null}
                        ]
                      }
                    }
                    """
            .formatted(generateId(), className);

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s"
                      }
                    }
                    """
            .formatted(generateId(), className);

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
                      "id": %s,
                      "method": "new",
                      "params": {
                        "type": "%s",
                        "args": [
                          {"ref": %d}
                        ]
                      }
                    }
                    """
            .formatted(generateId(), className, instanceId);
    responseMessage = sendAndReceive(request);
    logger.debug("responseMessage: {}", responseMessage);

    // assert that the result is not null
    assertNotNull(responseMessage.getResult());
    // assert that returned value's ref is not null
    assertNotNull(responseMessage.getResult().getValue());
    assertNotNull(responseMessage.getResult().getValue().getRef());
  }
}
