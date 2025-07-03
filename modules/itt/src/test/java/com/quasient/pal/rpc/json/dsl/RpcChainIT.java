/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.json.dsl;

import static com.quasient.pal.dsl.jsonrpc.RpcChain.args;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.dsl.jsonrpc.RpcChain;
import com.quasient.pal.messages.types.RpcType;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RpcChainIT extends AbstractIntegrationTest {

  private static final String VARS_CLASS_NAME = "com.quasient.pal.apps.rpc.Variables";
  private static final String METHODS_CLASS_NAME = "com.quasient.pal.apps.rpc.Methods";

  private ThinPeer thinPeer;
  private RpcChain chain;
  DirectoryConnectionProvider directoryConnectionProvider;

  @Before
  public void setUp() throws Exception {
    thinPeer = createTestThinPeer();
    chain = new RpcChain(thinPeer);
  }

  @After
  public void tearDown() {
    thinPeer.close();
  }

  private ThinPeer createTestThinPeer() throws Exception {
    directoryConnectionProvider = new DirectoryConnectionProvider(getPalDirectoryUrl());
    PeerInfo jsonRpcPeer =
        findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider)
            .orElseThrow(() -> new RuntimeException("No peer found with JSON-RPC enabled"));
    return new ThinPeer()
        .withUuid(UUID.randomUUID())
        .withDirectoryProvider(directoryConnectionProvider)
        .withInitialPeer(jsonRpcPeer)
        .withOutboundRpcType(RpcType.JSON_RPC)
        .init();
  }

  /* Get & put static fields */
  @Test
  public void testGetStaticField() throws Exception {
    chain.getStatic(VARS_CLASS_NAME, "aClassString", "myVar").send();
    assertThat(chain.getChainResult().getValue("myVar"), is("I'm classy"));
  }

  @Test
  public void testGetStaticFieldWithPrimitive() throws Exception {
    chain.getStatic(VARS_CLASS_NAME, "aStaticInteger", "intVar").send();
    assertThat(chain.getChainResult().getValue("intVar"), is(3000));
  }

  @Test
  public void testGetStaticFieldWithBoolean() throws Exception {
    chain.getStatic(VARS_CLASS_NAME, "aPackageVisibleBool", "boolVar").send();
    assertThat(chain.getChainResult().getValue("boolVar"), is(true));
  }

  @Test
  public void testPutGetStaticField() throws Exception {
    chain
        .getStatic(VARS_CLASS_NAME, "aNullStaticStr", "nullVar")
        .putStatic(VARS_CLASS_NAME, "aNullStaticStr", "I'm not null") // set to non-null
        .getStatic(VARS_CLASS_NAME, "aNullStaticStr", "nonNullVar")
        .putStatic(VARS_CLASS_NAME, "aNullStaticStr", null) // reset to null
        .getStatic(VARS_CLASS_NAME, "aNullStaticStr", "anotherNullVar")
        .send();

    assertNull(chain.getChainResult().getValue("nullVar"));
    assertThat(chain.getChainResult().getValue("nonNullVar"), is("I'm not null"));
    assertNull(chain.getChainResult().getValue("anotherNullVar"));
  }

  /* Get & put instance fields */
  @Test
  public void testGetField() throws Exception {
    chain.create(VARS_CLASS_NAME, "vars").get("anInt", "myVar").send();

    assertThat(chain.getChainResult().getValue("myVar"), is(4));
  }

  @Test
  public void testGetFieldWithNull() throws Exception {
    chain.create(VARS_CLASS_NAME, "vars").get("myNullInt", "nullVar").send();

    assertNull(chain.getChainResult().getValue("nullVar"));
  }

  @Test
  public void testGetFieldWithBoolean() throws Exception {
    chain.create(VARS_CLASS_NAME, "vars").get("myBool", "boolVar").send();

    assertThat(chain.getChainResult().getValue("boolVar"), is(true));
  }

  @Test
  public void testPutGetField() throws Exception {
    chain
        .create(VARS_CLASS_NAME, "vars")
        .get("myNullStr", "nullVar")
        .put("myNullStr", "I'm not null") // set to non-null
        .get("myNullStr", "notNullVar")
        .put("myNullStr", null) // reset to null
        .get("myNullStr", "anotherNullVar")
        .send();

    assertNull(chain.getChainResult().getValue("nullVar"));
    assertThat(chain.getChainResult().getValue("notNullVar"), is("I'm not null"));
    assertNull(chain.getChainResult().getValue("anotherNullVar"));
  }

  /* Call static */
  @Test
  public void testCallStaticMethodReturnsInt() throws Exception {
    chain.callStatic(METHODS_CLASS_NAME, "highFive", "five").send();
    assertThat(chain.getChainResult().getValue("five"), is(5));
  }

  @Test
  public void testCallStaticMethodUnnamedVarReturnsInt() throws Exception {
    chain.callStatic(METHODS_CLASS_NAME, "highFive").send();
    assertThat(chain.getChainResult().getAllVarNames(), is(empty()));
    assertThat(chain.getChainResult().getAllValues().size(), is(1));
    assertThat(chain.getChainResult().getAllValues().get(0).get("value"), is(5));
  }

  @Test
  public void testCallStaticMethodWithArgsReturnsInt() throws Exception {
    chain.callStatic(METHODS_CLASS_NAME, "testNonVoidStatic", new Object[] {"MyNameIs"}).send();
    assertThat(chain.getChainResult().getAllVarNames(), is(empty()));
    assertThat(chain.getChainResult().getAllValues().size(), is(1));
    assertThat(chain.getChainResult().getAllValues().get(0).get("value"), is("mynameis"));
  }

  @Test
  public void testCallStaticMethodReturnsNull() throws Exception {
    chain.callStatic(METHODS_CLASS_NAME, "giveMeNull", "nullVar").send();
    assertNull(chain.getChainResult().getValue("nullVar"));
  }

  /* Call instance methods */

  @Test
  public void testCreateArrayListAddElementsAndGetSize() throws Exception {
    chain
        .create("java.util.ArrayList", "myList")
        .call("add", new Object[] {42})
        .call("add", new Object[] {100})
        .call("size", "listSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("myList"));
    assertThat(chain.getChainResult().getValue("listSize"), is(2));
  }

  @Test
  public void testCreateArrayListWithSetInConstructor() throws Exception {
    chain
        .create("java.util.HashSet", "mySet")
        .call("add", new Object[] {42})
        .call("add", new Object[] {100})
        .call("size", "setSize")
        .create("java.util.ArrayList", "myList", new Object[] {"mySet"})
        .call("size", "listSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("myList"));
    assertNotNull(chain.getChainResult().getRef("mySet"));
    assertThat(chain.getChainResult().getValue("setSize"), is(2));
    assertThat(chain.getChainResult().getValue("listSize"), is(2));
  }

  @Test
  public void testCreateArrayListPopulateAndGetSize() throws Exception {
    chain
        .create("java.util.ArrayList", "myList")
        .callStatic(
            "java.util.Collections",
            "addAll",
            "changed",
            new Object[] {"myList", new Integer[] {19, 23, 465}})
        .call("size", "listSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("myList"));
    assertThat(chain.getChainResult().getValue("changed"), is(true));
    assertThat(chain.getChainResult().getValue("listSize"), is(3));
  }

  /**
   * Test instance method calls on two instances: Create two ArrayList, add elements, and then call
   * a addAll method on one of them to add the elements of the other.
   */
  @Test
  public void testCreateInstancesAddAll() throws Exception {
    chain
        .create("java.util.ArrayList", "listOne")
        .call("add", new Object[] {11})
        .call("add", new Object[] {222})
        .create("java.util.ArrayList", "listTwo")
        .call("add", new Object[] {32})
        .call("add", new Object[] {44})
        .call("add", new Object[] {44})
        .with("listOne")
        .call("addAll", "changed", new Object[] {"listTwo"})
        .call("size", "listOneSize")
        .with("listTwo")
        .call("size", "listTwoSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("listOne"));
    assertNotNull(chain.getChainResult().getRef("listTwo"));
    assertThat(chain.getChainResult().getValue("listOneSize"), is(5));
    assertThat(chain.getChainResult().getValue("listTwoSize"), is(3));
  }

  /** Same as above but using args() instead of new Object[]. */
  @Test
  public void testCreateInstancesAddAllUsingArgs() throws Exception {
    chain
        .create("java.util.ArrayList", "listOne")
        .call("add", args(11))
        .call("add", args(222))
        .create("java.util.ArrayList", "listTwo")
        .call("add", args(32))
        .call("add", args(44))
        .call("add", args(44))
        .with("listOne")
        .call("addAll", "changed", args("listTwo"))
        .call("size", "listOneSize")
        .with("listTwo")
        .call("size", "listTwoSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("listOne"));
    assertNotNull(chain.getChainResult().getRef("listTwo"));
    assertThat(chain.getChainResult().getValue("changed"), is(true));
    assertThat(chain.getChainResult().getValue("listOneSize"), is(5));
    assertThat(chain.getChainResult().getValue("listTwoSize"), is(3));
  }

  /**
   * Tests nested calls.
   *
   * <pre>
   *  - Create a HashMap
   *  - Put an entry where the value is a newly created ArrayList filled with values
   *  - Call size on the HashMap to verify insertion
   * </pre>
   */
  @Test
  public void testNestedCalls() throws Exception {
    chain
        .create("java.util.HashMap", "map")
        .call(
            "put",
            new Object[] {
              "listKey",
              chain
                  .create("java.util.ArrayList", "intList") // create a new list inline
                  .call("add", new Object[] {10})
                  .call("add", new Object[] {20})
            })
        .call("size", "mapSize")
        .with("intList")
        .call("size", "listSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("map"));
    assertThat(chain.getChainResult().getValue("listSize"), is(2));
    assertThat(chain.getChainResult().getValue("mapSize"), is(1));
  }

  @Test
  public void testNestedCallsUsingArgs() throws Exception {
    chain
        .create("java.util.HashMap", "map")
        .call(
            "put",
            args(
                "listKey",
                chain
                    .create("java.util.ArrayList", "intList") // create a new list inline
                    .call("add", args(10))
                    .call("add", args(20))))
        .call("size", "mapSize")
        .with("intList")
        .call("size", "listSize")
        .send();

    assertNotNull(chain.getChainResult().getRef("map"));
    assertThat(chain.getChainResult().getValue("listSize"), is(2));
    assertThat(chain.getChainResult().getValue("mapSize"), is(1));
  }

  /**
   * Arrays and primitives: Create an ArrayList, add multiple primitives, and then convert it to an
   * array by calling toArray().
   */
  @Test
  public void testArrayArgumentsAndResults() throws Exception {
    chain
        .create("java.util.ArrayList", "numList")
        .call("add", new Object[] {5})
        .call("add", new Object[] {10})
        .call("toArray", "arr", new Object[] {new Integer[0]})
        .send();

    assertNotNull(chain.getChainResult().getRef("numList"));
    assertThat(chain.getChainResult().getValue("arr"), is(new Integer[] {5, 10}));
  }
}
