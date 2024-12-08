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

import static org.junit.Assert.assertNotNull;

import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import org.junit.Test;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
public class ConstructorMessageIT extends AbstractJsonRpcMessageIT {

  protected final String className = "net.ittera.pal.apps.rpc.Constructors";

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

    JsonRpcResponse replyMsg = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(replyMsg.getResult());

    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    assertNotNull(replyMsg.getResult().getValue().getRef());
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

    JsonRpcResponse replyMsg = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(replyMsg.getResult());

    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    assertNotNull(replyMsg.getResult().getValue().getRef());
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

    JsonRpcResponse replyMsg = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(replyMsg.getResult());

    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    assertNotNull(replyMsg.getResult().getValue().getRef());
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

    JsonRpcResponse replyMsg = sendAndReceive(request);

    // assert that the result is not null
    assertNotNull(replyMsg.getResult());

    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    assertNotNull(replyMsg.getResult().getValue().getRef());
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

    JsonRpcResponse replyMsg = sendAndReceive(request);
    // assert that the result is not null
    assertNotNull(replyMsg.getResult());

    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    Integer instanceId = replyMsg.getResult().getValue().getRef();
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
    replyMsg = sendAndReceive(request);
    logger.debug("replyMsg: {}", replyMsg);

    // assert that the result is not null
    assertNotNull(replyMsg.getResult());
    // assert that returned value's ref is not null
    assertNotNull(replyMsg.getResult().getValue());
    assertNotNull(replyMsg.getResult().getValue().getRef());
  }
}
