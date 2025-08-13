/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.common.objects.ObjectRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior. */
public class ConcurrentHashMapObjectLookupStoreTest {

  private ConcurrentHashMapObjectLookupStore objectLookupStore;

  // <editor-fold desc="storeObject">
  @Test
  public void storeObject_nullObject_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.storeObject(null);
      fail("Trying to store null should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void storeObject_newObject_objectRef() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ObjectRef objRef = objectLookupStore.storeObject(new ArrayList<>());
    assertNotNull(objRef);
    assertThat(objectLookupStore.size(), is(1L));
  }

  @Test
  public void storeObject_sameObjectTwice_getExistingRef() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ArrayList<Integer> listOfInts = new ArrayList<>();
    ObjectRef firstObjRef = objectLookupStore.storeObject(listOfInts);
    assertEquals(firstObjRef, objectLookupStore.storeObject(listOfInts));
    assertThat(objectLookupStore.size(), is(1L));
  }

  @Test
  public void storeObject_differentObjsStored_sizeAsExpected() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(34182);
    objectLookupStore.storeObject("some chars");
    assertThat(objectLookupStore.size(), is(3L));
  }

  @Test
  public void storeObject_equalButNotSameObjectStored_noException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(2L));
  }

  // </editor-fold>

  // <editor-fold desc="lookupObject">
  @Test
  public void lookupObject_nullObjectRefParam_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.lookupObject(null);
      fail("Trying to look up a null objectRef should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void lookupObject_objectIsStored_object() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ArrayList<Integer> listOfInts = new ArrayList<>();
    ObjectRef objRef = objectLookupStore.storeObject(listOfInts);
    assertThat(objectLookupStore.size(), is(1L));
    assertEquals(listOfInts, objectLookupStore.lookupObject(objRef));
  }

  @Test
  public void lookupObject_madeUpObjectRef_null() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertNull(objectLookupStore.lookupObject(ObjectRef.from("2323823")));
  }

  // </editor-fold>

  // <editor-fold desc="containsObjectRef">
  @Test
  public void containsObjectRef_nullObjectRef_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.containsObjectRef(null);
      fail("Checking for a null key should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void containsObjectRef_ofStoredObject_true() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ObjectRef objectRef = objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(objectLookupStore.containsObjectRef(objectRef));
  }

  @Test
  public void containsObjectRef_fakeObjectRef_false() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertFalse(objectLookupStore.containsObjectRef(ObjectRef.from("2092373")));
  }

  // </editor-fold>

  // <editor-fold desc="size">
  @Test
  public void size_noObjectsStored_sizeIsZero() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new HashMap<>());
    assertThat(objectLookupStore.size(), is(2L));
  }

  // </editor-fold>

  // <editor-fold desc="clear">
  @Test
  public void clear_objectsStored_sizeIsZero() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    // store objects
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new HashMap<>());
    assertThat(objectLookupStore.size(), is(2L));
    objectLookupStore.clear();
    assertThat(objectLookupStore.size(), is(0L));
  }

  // </editor-fold>

  // <editor-fold desc="isEmpty">
  @Test
  public void isEmpty_noObjectsStored_true() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(objectLookupStore.isEmpty());
  }

  // </editor-fold>

  // <editor-fold desc="getObjects">
  @Test
  public void getObjects_noObjectsStored_objectsEmpty() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void getObjects_someObjectsStored_objects() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(1)));
  }

  // </editor-fold>

  // <editor-fold desc="removeObject">
  @Test
  public void remove_objectIsStored_objectRemoved() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    final ObjectRef objRef = objectLookupStore.storeObject(new ArrayList<>());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertFalse(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(1)));

    // remove and check
    objectLookupStore.remove(objRef);
    assertFalse(objectLookupStore.containsObjectRef(objRef));
    assertTrue(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
  }

  @Test
  public void removeAll_someObjectsStored_allRemoved() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    List<ObjectRef> objectRefList = new ArrayList<>();
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.forEach(objRef -> assertTrue(objectLookupStore.containsObjectRef(objRef)));
    assertFalse(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(objectRefList.size())));

    // remove and check
    objectLookupStore.removeAll(objectRefList);
    objectRefList.forEach(objRef -> assertFalse(objectLookupStore.containsObjectRef(objRef)));
    assertTrue(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
  }
  // </editor-fold>
}
