package com.ittera.cometa.concentrator;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;


/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 *
 * TODO containsValue(object)
 * TODO containsKey(objectRef)
 * TODO remove(key)
 *
 */
public class ObjectStoreTest {

  @Before public void clearStore() {
    ObjectStore.clear();
  }

  //<editor-fold desc="storeObject">
  @Test
  public void storeObject_nullObject_nullPointerException() throws Exception {
    try {
      ObjectStore.storeObject(null);
      fail("Trying to store null should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void storeObject_newObject_objectRef() throws Exception {

    String objRef = ObjectStore.storeObject(new ArrayList());

    assertNotNull(objRef);
  }

  @Test
  public void storeObject_sameObjectTwice_illegalArgumentException() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    String firstObjRef = ObjectStore.storeObject(listOfInts);
    try {
      String secondObjRef = ObjectStore.storeObject(listOfInts);
      fail(String.format("Storing same object again should throw an IllegalArgumentException. First objRef=%s Second objRef=%s",firstObjRef,secondObjRef));
    } catch (IllegalArgumentException iae) {
      //expected
    }
  }

  @Test
  public void storeObject_differentObjsStored_sizeAsExpected() throws Exception {

    ObjectStore.storeObject(new ArrayList());
    ObjectStore.storeObject(Integer.valueOf(34182));
    ObjectStore.storeObject(new String("some chars"));

    int objectsStored = ObjectStore.size();
    assertEquals(3, objectsStored);
  }

  @Test
  public void storeObject_equalButNotSameObjectStored_noException() throws Exception {

    ObjectStore.storeObject(new ArrayList());
    ObjectStore.storeObject(new ArrayList());

    assertEquals(2, ObjectStore.size());
  }
  //</editor-fold>

  //<editor-fold desc="lookupObject">
  @Test
  public void lookupObject_nullObjectRefParam_nullPointerException() throws Exception {
    try {
      ObjectStore.lookupObject(null);
      fail("Trying to look up a null objectRef should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void lookupObject_objectIsStored_object() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    String objRef = ObjectStore.storeObject(listOfInts);

    assertEquals(listOfInts, ObjectStore.lookupObject(objRef));
  }

  @Test
  public void lookupObject_madeUpObjectRef_null() throws Exception {

    assertNull(ObjectStore.lookupObject("23:not_real:objectref"));
  }
  //</editor-fold>

  //<editor-fold desc="lookupObjectRef">
  @Test
  public void lookupObjectRef_objectIsStored_objectRef() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    String objRef = ObjectStore.storeObject(listOfInts);

    assertEquals(objRef, ObjectStore.lookupObjectRef(listOfInts));
  }

  @Test
  public void lookupObjectRef_objectIsNull_nullPointerException() throws Exception {
    try {
      ObjectStore.lookupObjectRef(null);
      fail("Trying to look up a null object should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void lookupObjectRef_objectNotStored_null() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    assertNull(ObjectStore.lookupObjectRef(listOfInts));
  }

  //</editor-fold>

  //<editor-fold desc="size">

  @Test
  public void size_noObjectsStored_sizeIsZero() throws Exception {

    assertEquals(0, ObjectStore.size());
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() throws Exception {

    ObjectStore.storeObject(new ArrayList());
    ObjectStore.storeObject(new HashMap());

    assertEquals(2, ObjectStore.size());
  }
  //</editor-fold>

  //<editor-fold desc="clear">
  @Test
  public void clear_objectsStored_sizeIsZero() throws Exception {

    //store objects
    ObjectStore.storeObject(new ArrayList());
    ObjectStore.storeObject(new HashMap());

    ObjectStore.clear();
    assertEquals(0, ObjectStore.size());
  }
  //</editor-fold>

  //<editor-fold desc="isEmpty">
  @Test
  public void isEmpty_noObjectsStored_true() throws Exception {

    assertTrue(ObjectStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() throws Exception {

    ObjectStore.storeObject(new ArrayList());
    assertFalse(ObjectStore.isEmpty());
  }
  //</editor-fold>


  //<editor-fold desc="containsValue">

  @Test
  public void containsValue_nullObject_nullPointerException() throws Exception {
    try {
      ObjectStore.containsValue(null);
      fail("Checking for a null value should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void containsValue_storedObject_true() throws Exception {

    ArrayList<Integer> listOfInts = new ArrayList();
    ObjectStore.storeObject(listOfInts);

    assertTrue(ObjectStore.containsValue(listOfInts));
  }

  //</editor-fold>

   //<editor-fold desc="containsObjectRef">

  @Test
  public void containsObjectRef_nullObjectRef_nullPointerException() throws Exception {
    try {
      ObjectStore.containsObjectRef(null);
      fail("Checking for a null key should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void containsObjectRef_ofStoredObject_true() throws Exception {

    String objectRef = ObjectStore.storeObject(new ArrayList());

    assertTrue(ObjectStore.containsObjectRef(objectRef));
  }

  @Test
  public void containsObjectRef_fakeObjectRef_false() throws Exception {

    assertFalse(ObjectStore.containsObjectRef("23:SomeFakeObjRef:209237"));
  }

  //</editor-fold>

  //<editor-fold desc="remove">

  @Test
  public void remove_nullObjectRef_nullPointerException() throws Exception {
    try {
      ObjectStore.remove(null);
      fail("Removing value for a null key should throw a NullPointerException");
    } catch (NullPointerException npe) {
      //expected
    }
  }

  @Test
  public void remove_fakeObjectRef_null() throws Exception {
    assertNull(ObjectStore.remove("23:SomeFakeObjRef:209237"));
  }

  @Test
  public void remove_storedObjectRef_intObject() throws Exception {

    Integer anInt = Integer.valueOf(34182);
    String objectRef = ObjectStore.storeObject(anInt);

    assertEquals(1, ObjectStore.size());
    assertEquals(anInt, ObjectStore.remove(objectRef));
    assertEquals(0, ObjectStore.size());

  }

  @Test
  public void remove_storedObjectRef_stringObject() throws Exception {

    String aString = new String("just a string");
    String objectRef = ObjectStore.storeObject(aString);

    assertEquals(aString, ObjectStore.remove(objectRef));
    assertTrue(ObjectStore.isEmpty());
  }

  @Test
  public void remove_alreadyRemoved_null() throws Exception {

    String aString = new String("just a string");
    String objectRef = ObjectStore.storeObject(aString);

    assertEquals(aString, ObjectStore.remove(objectRef));
    assertNull(ObjectStore.remove(objectRef));
    assertTrue(ObjectStore.isEmpty());
  }


  //</editor-fold>

}

