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

package net.ittera.pal.rpc.basic;

import net.ittera.pal.apps.rpc.Constructors;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.rpc.AbstractPeerMessageIT;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: - varargs constructor - constructor that takes Object, passing a Constructors instance.
 * This will test ReflectionHelper, as it should invoke the more specific constructor (the one
 * taking Constructors type, not Object type) - invoke constructor using constructor-ref (requires
 * [ticket:15]) - inner constructor (commented out below), if it makes sense
 */
public class ConstructorMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.rpc.Constructors";

  /**
   * Explained here why won't pass
   * https://stackoverflow.com/questions/32301892/nosuchmethodexception-for-public-no-argument-constructor-in-inner-class
   */
  // @Test
  public void innerConstructor() throws Exception {

    String className = "net.ittera.pal.apps.rpc.Constructors$Empty";
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
    Class[] parameterTypes = {Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_packageVisibleTwoArgs_newObjectReturned() throws Exception {

    Object[] args = {"Constructing an app", 5};
    ObjectRef[] argRefs = {null, null};
    Class[] parameterTypes = {String.class, Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_publicOneArgNull_newObjectReturned() throws Exception {

    Object[] args = {null};
    ObjectRef[] argRefs = {null};
    Class[] parameterTypes = {Integer.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_privateOneArgArray_newObjectReturned() throws Exception {

    Object[] args = {new String[] {"Aa", "Bb", "Cc"}};
    ObjectRef[] argRefs = {null};
    Class[] parameterTypes = {String[].class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_protectedOneArgRef_newObjectReturned() throws Exception {

    // 1. Construct an instance calling no-args constructor
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // 2. Construct an instance calling the constructor that takes another instance as arg
    Object[] args = {null};
    ObjectRef[] argRefs = {newObjRef};
    Class[] parameterTypes = {Constructors.class};

    callConstructor(className, parameterTypes, args, argRefs);
  }

  @Test
  public void constructor_constructor3DoublesDoesNotExist_exThrown() throws Exception {

    Object[] args = {239823d, 38723d, 2323d};
    ObjectRef[] argRefs = {null, null, null};
    Class[] parameterTypes = {Double.class, Double.class, Double.class};

    callConstructor(className, parameterTypes, args, argRefs, "java.lang.NoSuchMethodException");
  }

  @Test
  public void constructor_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";

    Object[] args = {239823d, 38723d, 2323d};
    ObjectRef[] argRefs = {null, null, null};
    Class[] parameterTypes = {Double.class, Double.class, Double.class};

    callConstructor(
        nonExistingClass, parameterTypes, args, argRefs, "java.lang.ClassNotFoundException");
  }

  @Test
  public void constructor_publicOneBadArg_exThrown() throws Exception {

    Object[] args = {"not_a_number"};
    ObjectRef[] argRefs = {null};
    Class[] parameterTypes = {String.class};

    callConstructor(className, parameterTypes, args, argRefs, "java.lang.NumberFormatException");
  }
}
