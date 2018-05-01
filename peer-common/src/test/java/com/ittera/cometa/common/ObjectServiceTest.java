package com.ittera.cometa.common;

import com.ittera.cometa.common.lang.ObjectRef;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 * <p>
 * TODO containsValue(object)
 * TODO containsKey(objectRef)
 * TODO remove(key)
 */
public class ObjectServiceTest {

	private static ObjectService objectService;

	@BeforeClass
	public static void initService() {
		objectService = new BiMapObjectService();
	}


	@Before
	public void clearStore() {
		objectService.clear();
	}

	//<editor-fold desc="storeObject">
	@Test
	public void storeObject_nullObject_nullPointerException() throws Exception {
		try {
			objectService.storeObject(null);
			fail("Trying to store null should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void storeObject_newObject_objectRef() throws Exception {

		ObjectRef objRef = objectService.storeObject(new ArrayList());

		assertNotNull(objRef);
	}

	@Test
	public void storeObject_sameObjectTwice_getExistingRef() throws Exception {

		ArrayList<Integer> listOfInts = new ArrayList();
		ObjectRef firstObjRef = objectService.storeObject(listOfInts);
		assertEquals(firstObjRef, objectService.storeObject(listOfInts));
	}

	@Test
	public void storeObject_differentObjsStored_sizeAsExpected() throws Exception {

		objectService.storeObject(new ArrayList());
		objectService.storeObject(34182);
		objectService.storeObject("some chars");

		int objectsStored = objectService.size();
		assertEquals(3, objectsStored);
	}

	@Test
	public void storeObject_equalButNotSameObjectStored_noException() throws Exception {

		objectService.storeObject(new ArrayList());
		objectService.storeObject(new ArrayList());

		assertEquals(2, objectService.size());
	}
	//</editor-fold>

	//<editor-fold desc="lookupObject">
	@Test
	public void lookupObject_nullObjectRefParam_nullPointerException() throws Exception {
		try {
			objectService.lookupObject(null);
			fail("Trying to look up a null objectRef should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void lookupObject_objectIsStored_object() throws Exception {

		ArrayList<Integer> listOfInts = new ArrayList();
		ObjectRef objRef = objectService.storeObject(listOfInts);

		assertEquals(listOfInts, objectService.lookupObject(objRef));
	}

	@Test
	public void lookupObject_madeUpObjectRef_null() throws Exception {

		assertNull(objectService.lookupObject(ObjectRef.from("23:not_real:objectref")));
	}
	//</editor-fold>

	//<editor-fold desc="lookupObjectRef">
	@Test
	public void lookupObjectRef_objectIsStored_objectRef() throws Exception {

		ArrayList<Integer> listOfInts = new ArrayList();
		ObjectRef objRef = objectService.storeObject(listOfInts);

		assertEquals(objRef, objectService.lookupObjectRef(listOfInts));
	}

	@Test
	public void lookupObjectRef_objectIsNull_nullPointerException() throws Exception {
		try {
			objectService.lookupObjectRef(null);
			fail("Trying to look up a null object should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void lookupObjectRef_objectNotStored_null() throws Exception {

		ArrayList<Integer> listOfInts = new ArrayList();
		assertNull(objectService.lookupObjectRef(listOfInts));
	}

	//</editor-fold>

	//<editor-fold desc="size">

	@Test
	public void size_noObjectsStored_sizeIsZero() throws Exception {

		assertEquals(0, objectService.size());
	}

	@Test
	public void size_someObjectsStored_numberOfObjects() throws Exception {

		objectService.storeObject(new ArrayList());
		objectService.storeObject(new HashMap());

		assertEquals(2, objectService.size());
	}
	//</editor-fold>

	//<editor-fold desc="clear">
	@Test
	public void clear_objectsStored_sizeIsZero() throws Exception {

		//store objects
		objectService.storeObject(new ArrayList());
		objectService.storeObject(new HashMap());

		objectService.clear();
		assertEquals(0, objectService.size());
	}
	//</editor-fold>

	//<editor-fold desc="isEmpty">
	@Test
	public void isEmpty_noObjectsStored_true() throws Exception {

		assertTrue(objectService.isEmpty());
	}

	@Test
	public void isEmpty_someObjectsStored_false() throws Exception {

		objectService.storeObject(new ArrayList());
		assertFalse(objectService.isEmpty());
	}
	//</editor-fold>


	//<editor-fold desc="containsValue">

	@Test
	public void containsValue_nullObject_nullPointerException() throws Exception {
		try {
			objectService.containsValue(null);
			fail("Checking for a null value should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void containsValue_storedObject_true() throws Exception {

		ArrayList<Integer> listOfInts = new ArrayList();
		objectService.storeObject(listOfInts);

		assertTrue(objectService.containsValue(listOfInts));
	}

	//</editor-fold>

	//<editor-fold desc="containsObjectRef">

	@Test
	public void containsObjectRef_nullObjectRef_nullPointerException() throws Exception {
		try {
			objectService.containsObjectRef(null);
			fail("Checking for a null key should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void containsObjectRef_ofStoredObject_true() throws Exception {

		ObjectRef objectRef = objectService.storeObject(new ArrayList());

		assertTrue(objectService.containsObjectRef(objectRef));
	}

	@Test
	public void containsObjectRef_fakeObjectRef_false() throws Exception {

		assertFalse(objectService.containsObjectRef(ObjectRef.from("23:SomeFakeObjRef:209237")));
	}

	//</editor-fold>

	//<editor-fold desc="remove">

	@Test
	public void remove_nullObjectRef_nullPointerException() throws Exception {
		try {
			objectService.remove(null);
			fail("Removing value for a null key should throw a NullPointerException");
		} catch (NullPointerException npe) {
			//expected
		}
	}

	@Test
	public void remove_fakeObjectRef_null() throws Exception {
		assertNull(objectService.remove(ObjectRef.from("23:SomeFakeObjRef:209237")));
	}

	@Test
	public void remove_storedObjectRef_intObject() throws Exception {

		Integer anInt = 34182;
		ObjectRef objectRef = objectService.storeObject(anInt);

		assertEquals(1, objectService.size());
		assertEquals(anInt, objectService.remove(objectRef));
		assertEquals(0, objectService.size());

	}

	@Test
	public void remove_storedObjectRef_stringObject() throws Exception {

		String aString = "just a string";
		ObjectRef objectRef = objectService.storeObject(aString);

		assertEquals(aString, objectService.remove(objectRef));
		assertTrue(objectService.isEmpty());
	}

	@Test
	public void remove_alreadyRemoved_null() throws Exception {

		String aString = "just another string";
		ObjectRef objectRef = objectService.storeObject(aString);

		assertEquals(aString, objectService.remove(objectRef));
		assertNull(objectService.remove(objectRef));
		assertTrue(objectService.isEmpty());
	}


	//</editor-fold>

}

