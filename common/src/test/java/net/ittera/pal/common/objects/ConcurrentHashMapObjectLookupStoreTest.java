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

package net.ittera.pal.common.objects;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsMapWithSize.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class ConcurrentHashMapObjectLookupStoreTest {

  private Logger mockedLogger;
  private ConcurrentHashMapObjectLookupStore objectLookupStore;

  private MockedStatic<LoggerFactory> mockLoggerFactory(boolean traceEnabled) {

    // mock Logger to enable calls to logger.trace()
    mockedLogger = mock(Logger.class);
    when(mockedLogger.isTraceEnabled()).thenReturn(traceEnabled);
    MockedStatic<LoggerFactory> mockedLoggerFactory = mockStatic(LoggerFactory.class);
    mockedLoggerFactory
        .when(() -> LoggerFactory.getLogger(ConcurrentHashMapObjectLookupStore.class))
        .thenReturn(mockedLogger);
    return mockedLoggerFactory;
  }

  // <editor-fold desc="storeObject">
  @Test
  public void storeObject_nullObject_nullPointerException() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();
                try {
                  objectLookupStore.storeObject(null);
                  fail("Trying to store null should throw a NullPointerException");
                } catch (NullPointerException npe) {
                  // expected
                }

                assertThat(objectLookupStore.size(), is(0L));

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ object: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void storeObject_newObject_objectRef() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();
                ObjectRef objRef = objectLookupStore.storeObject(new ArrayList<>());
                assertNotNull(objRef);

                assertThat(objectLookupStore.size(), is(1L));

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void storeObject_sameObjectTwice_getExistingRef() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                ArrayList<Integer> listOfInts = new ArrayList<>();
                ObjectRef firstObjRef = objectLookupStore.storeObject(listOfInts);
                assertEquals(firstObjRef, objectLookupStore.storeObject(listOfInts));

                assertThat(objectLookupStore.size(), is(1L));

                if (traceEnabled) {
                  verify(mockedLogger, times(2)).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ objectRef: {}"), (Object) any());
                  verify(mockedLogger)
                      .trace(eq("out w/ (pre-existing) objectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void storeObject_differentObjsStored_sizeAsExpected() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                objectLookupStore.storeObject(new ArrayList<>());
                objectLookupStore.storeObject(34182);
                objectLookupStore.storeObject("some chars");

                assertThat(objectLookupStore.size(), is(3L));

                if (traceEnabled) {
                  verify(mockedLogger, times(3)).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger, times(3)).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void storeObject_equalButNotSameObjectStored_noException() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                objectLookupStore.storeObject(new ArrayList<>());
                objectLookupStore.storeObject(new ArrayList<>());

                assertThat(objectLookupStore.size(), is(2L));

                if (traceEnabled) {
                  verify(mockedLogger, times(2)).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger, times(2)).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="lookupObject">
  @Test
  public void lookupObject_nullObjectRefParam_nullPointerException() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                try {
                  objectLookupStore.lookupObject(null);
                  fail("Trying to look up a null objectRef should throw a NullPointerException");
                } catch (NullPointerException npe) {
                  // expected
                }

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void lookupObject_objectIsStored_object() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                ArrayList<Integer> listOfInts = new ArrayList<>();
                ObjectRef objRef = objectLookupStore.storeObject(listOfInts);

                assertThat(objectLookupStore.size(), is(1L));

                assertEquals(listOfInts, objectLookupStore.lookupObject(objRef));

                if (traceEnabled) {
                  // storeObject
                  verify(mockedLogger).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ objectRef: {}"), (Object) any());
                  // lookupObject
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ object: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void lookupObject_madeUpObjectRef_null() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                assertNull(objectLookupStore.lookupObject(ObjectRef.from("2323823")));

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ object: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="containsObjectRef">
  @Test
  public void containsObjectRef_nullObjectRef_nullPointerException() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                try {
                  objectLookupStore.containsObjectRef(null);
                  fail("Checking for a null key should throw a NullPointerException");
                } catch (NullPointerException npe) {
                  // expected
                }

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void containsObjectRef_ofStoredObject_true() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                ObjectRef objectRef = objectLookupStore.storeObject(new ArrayList<>());

                assertThat(objectLookupStore.size(), is(1L));

                assertTrue(objectLookupStore.containsObjectRef(objectRef));

                if (traceEnabled) {
                  // storeObject
                  verify(mockedLogger).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ objectRef: {}"), (Object) any());
                  // containsObjectRef
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ containsObjectRef: {}"), (Object) any());
                }
              }
            });
  }

  @Test
  public void containsObjectRef_fakeObjectRef_false() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                assertFalse(objectLookupStore.containsObjectRef(ObjectRef.from("2092373")));

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ objectRef: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ containsObjectRef: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="size">
  @Test
  public void size_noObjectsStored_sizeIsZero() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();

    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                objectLookupStore.storeObject(new ArrayList<>());
                objectLookupStore.storeObject(new HashMap<>());

                assertThat(objectLookupStore.size(), is(2L));

                if (traceEnabled) {
                  verify(mockedLogger, times(2)).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger, times(2)).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="clear">
  @Test
  public void clear_objectsStored_sizeIsZero() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                // store objects
                objectLookupStore.storeObject(new ArrayList<>());
                objectLookupStore.storeObject(new HashMap<>());

                assertThat(objectLookupStore.size(), is(2L));
                objectLookupStore.clear();
                assertThat(objectLookupStore.size(), is(0L));

                if (traceEnabled) {
                  verify(mockedLogger, times(2)).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger, times(2)).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="isEmpty">
  @Test
  public void isEmpty_noObjectsStored_true() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();

    assertThat(objectLookupStore.size(), is(0L));

    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectLookupStore = new ConcurrentHashMapObjectLookupStore();

                objectLookupStore.storeObject(new ArrayList<>());
                assertThat(objectLookupStore.size(), is(1L));

                assertFalse(objectLookupStore.isEmpty());

                if (traceEnabled) {
                  verify(mockedLogger).trace(eq("in w/ object: {}"), (Object) any());
                  verify(mockedLogger).trace(eq("out w/ objectRef: {}"), (Object) any());
                }
              }
            });
  }

  // </editor-fold>

  // <editor-fold desc="getObjects">
  @Test
  public void getObjects_noObjectsStored_objectsEmpty() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();

    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void getObjects_someObjectsStored_objects() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();

    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(1)));
  }

  // </editor-fold>

  // <editor-fold desc="removeObject">
  @Test
  public void remove_objectIsStored_objectRemoved() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();
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
  public void removeAll_someOjectsStored_allRemoved() {
    objectLookupStore = new ConcurrentHashMapObjectLookupStore();
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
