/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.binary;

import io.quasient.foobar.apps.quantized.rpc.Constructors;
import io.quasient.pal.common.objects.ObjectRef;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior */
@RunWith(Parameterized.class)
public class ConstructorMessageIT extends AbstractColferRpcMessageIT {

  protected final String className = "io.quasient.foobar.apps.quantized.rpc.Constructors";

  public ConstructorMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  // Explained here why won't pass
  // <a
  // href="https://stackoverflow.com/questions/32301892/nosuchmethodexception-for-public-no-argument-constructor-in-inner-class">...</a>
  // @Test
  public void innerConstructor() throws Exception {

    String className = "io.quasient.foobar.apps.quantized.rpc.Constructors$Empty";
    callEmptyConstructor(className);
  }

  @Test
  public void constructor_publicNoArgs_newObjectReturned() throws Exception {

    callEmptyConstructor(className);
  }

  @Test
  public void constructor_publicOneArg_newObjectReturned() throws Exception {

    Object[] args = {5};
    ObjectRef[] argRefs = {null};
    Class<?>[] parameterTypes = {Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_packageVisibleTwoArgs_newObjectReturned() throws Exception {

    Object[] args = {"Constructing an app", 5};
    ObjectRef[] argRefs = {null, null};
    Class<?>[] parameterTypes = {String.class, Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_publicOneArgNull_newObjectReturned() throws Exception {

    Object[] args = {null};
    ObjectRef[] argRefs = {null};
    Class<?>[] parameterTypes = {Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_privateOneArgArray_newObjectReturned() throws Exception {

    Object[] args = {new String[] {"Aa", "Bb", "Cc"}};
    ObjectRef[] argRefs = {null};
    Class<?>[] parameterTypes = {String[].class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_protectedOneArgRef_newObjectReturned() throws Exception {

    // 1. Construct an instance calling no-args constructor
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // 2. Construct an instance calling the constructor that takes another instance as arg
    Object[] args = {null};
    ObjectRef[] argRefs = {newObjRef};
    Class<?>[] parameterTypes = {Constructors.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_constructor3DoublesDoesNotExist_exThrown() throws Exception {

    Object[] args = {239823d, 38723d, 2323d};
    ObjectRef[] argRefs = {null, null, null};
    Class<?>[] parameterTypes = {Double.class, Double.class, Double.class};

    callConstructor(className, parameterTypes, args, argRefs, "java.lang.NoSuchMethodException");
  }

  @Test
  public void constructor_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "io.quasient.foobar.apps.IDontExist";

    Object[] args = {239823d, 38723d, 2323d};
    ObjectRef[] argRefs = {null, null, null};
    Class<?>[] parameterTypes = {Double.class, Double.class, Double.class};

    callConstructor(
        nonExistingClass, parameterTypes, args, argRefs, "java.lang.ClassNotFoundException");
  }

  @Test
  public void constructor_publicOneBadArg_exThrown() throws Exception {

    Object[] args = {"not_a_number"};
    ObjectRef[] argRefs = {null};
    Class<?>[] parameterTypes = {String.class};

    callConstructor(className, parameterTypes, args, argRefs, "java.lang.NumberFormatException");
  }
}
