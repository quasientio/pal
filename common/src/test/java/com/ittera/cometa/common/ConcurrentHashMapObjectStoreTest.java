package com.ittera.cometa.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ittera.cometa.common.lang.ObjectRef;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class ConcurrentHashMapObjectStoreTest {

  private static ObjectStore objectStore;

  @BeforeClass
  public static void initService() {
    objectStore = new ConcurrentHashMapObjectStore();
  }

  @Before
  public void clearStore() {
    objectStore.clear();
  }

  // <editor-fold desc="storeObject">
  @Test
  public void storeObject_nullObject_nullPointerException() throws Exception {
    try {
      objectStore.storeObject(null);
      fail("Trying to store null should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void storeObject_newObject_objectRef() throws Exception {

    ObjectRef objRef = objectStore.storeObject(new ArrayList());

    assertNotNull(objRef);
  }

  @Test
  public void storeObject_sameObjectTwice_getExistingRef() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    ObjectRef firstObjRef = objectStore.storeObject(listOfInts);
    assertEquals(firstObjRef, objectStore.storeObject(listOfInts));
  }

  @Test
  public void storeObject_differentObjsStored_sizeAsExpected() throws Exception {

    objectStore.storeObject(new ArrayList());
    objectStore.storeObject(34182);
    objectStore.storeObject("some chars");

    long objectsStored = objectStore.size();
    assertEquals(3, objectsStored);
  }

  @Test
  public void storeObject_equalButNotSameObjectStored_noException() throws Exception {

    objectStore.storeObject(new ArrayList());
    objectStore.storeObject(new ArrayList());

    assertEquals(2, objectStore.size());
  }
  // </editor-fold>

  // <editor-fold desc="lookupObject">
  @Test
  public void lookupObject_nullObjectRefParam_nullPointerException() throws Exception {
    try {
      objectStore.lookupObject(null);
      fail("Trying to look up a null objectRef should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void lookupObject_objectIsStored_object() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    ObjectRef objRef = objectStore.storeObject(listOfInts);

    assertEquals(listOfInts, objectStore.lookupObject(objRef));
  }

  @Test
  public void lookupObject_madeUpObjectRef_null() throws Exception {

    assertNull(objectStore.lookupObject(ObjectRef.from("2323823")));
  }
  // </editor-fold>

  // <editor-fold desc="containsObjectRef">
  @Test
  public void containsObjectRef_nullObjectRef_nullPointerException() throws Exception {
    try {
      objectStore.containsObjectRef(null);
      fail("Checking for a null key should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void containsObjectRef_ofStoredObject_true() throws Exception {

    ObjectRef objectRef = objectStore.storeObject(new ArrayList());

    assertTrue(objectStore.containsObjectRef(objectRef));
  }

  @Test
  public void containsObjectRef_fakeObjectRef_false() throws Exception {

    assertFalse(objectStore.containsObjectRef(ObjectRef.from("2092373")));
  }
  // </editor-fold>

  // <editor-fold desc="size">
  @Test
  public void size_noObjectsStored_sizeIsZero() throws Exception {

    assertEquals(0, objectStore.size());
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() throws Exception {

    objectStore.storeObject(new ArrayList());
    objectStore.storeObject(new HashMap());

    assertEquals(2, objectStore.size());
  }
  // </editor-fold>

  // <editor-fold desc="clear">
  @Test
  public void clear_objectsStored_sizeIsZero() throws Exception {

    // store objects
    objectStore.storeObject(new ArrayList());
    objectStore.storeObject(new HashMap());

    objectStore.clear();
    assertEquals(0, objectStore.size());
  }
  // </editor-fold>

  // <editor-fold desc="isEmpty">
  @Test
  public void isEmpty_noObjectsStored_true() throws Exception {

    assertTrue(objectStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() throws Exception {

    objectStore.storeObject(new ArrayList());
    assertFalse(objectStore.isEmpty());
  }
  // </editor-fold>
}
