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
import java.util.stream.Stream;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class ConcurrentHashMapObjectStoreTest {

  private Logger mockedLogger;
  private ConcurrentHashMapObjectStore objectStore;

  private MockedStatic<LoggerFactory> mockLoggerFactory(boolean traceEnabled) {

    // mock Logger to enable calls to logger.trace()
    mockedLogger = mock(Logger.class);
    when(mockedLogger.isTraceEnabled()).thenReturn(traceEnabled);
    MockedStatic<LoggerFactory> mockedLoggerFactory = mockStatic(LoggerFactory.class);
    mockedLoggerFactory
        .when(() -> LoggerFactory.getLogger(ConcurrentHashMapObjectStore.class))
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
                objectStore = new ConcurrentHashMapObjectStore();
                try {
                  objectStore.storeObject(null);
                  fail("Trying to store null should throw a NullPointerException");
                } catch (NullPointerException npe) {
                  // expected
                }

                assertThat(objectStore.size(), is(0L));

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
                objectStore = new ConcurrentHashMapObjectStore();
                ObjectRef objRef = objectStore.storeObject(new ArrayList<>());
                assertNotNull(objRef);

                assertThat(objectStore.size(), is(1L));

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
                objectStore = new ConcurrentHashMapObjectStore();

                ArrayList<Integer> listOfInts = new ArrayList<>();
                ObjectRef firstObjRef = objectStore.storeObject(listOfInts);
                assertEquals(firstObjRef, objectStore.storeObject(listOfInts));

                assertThat(objectStore.size(), is(1L));

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
                objectStore = new ConcurrentHashMapObjectStore();

                objectStore.storeObject(new ArrayList<>());
                objectStore.storeObject(34182);
                objectStore.storeObject("some chars");

                assertThat(objectStore.size(), is(3L));

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
                objectStore = new ConcurrentHashMapObjectStore();

                objectStore.storeObject(new ArrayList<>());
                objectStore.storeObject(new ArrayList<>());

                assertThat(objectStore.size(), is(2L));

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
                objectStore = new ConcurrentHashMapObjectStore();

                try {
                  objectStore.lookupObject(null);
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
                objectStore = new ConcurrentHashMapObjectStore();

                ArrayList<Integer> listOfInts = new ArrayList<>();
                ObjectRef objRef = objectStore.storeObject(listOfInts);

                assertThat(objectStore.size(), is(1L));

                assertEquals(listOfInts, objectStore.lookupObject(objRef));

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
                objectStore = new ConcurrentHashMapObjectStore();

                assertNull(objectStore.lookupObject(ObjectRef.from("2323823")));

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
                objectStore = new ConcurrentHashMapObjectStore();

                try {
                  objectStore.containsObjectRef(null);
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
                objectStore = new ConcurrentHashMapObjectStore();

                ObjectRef objectRef = objectStore.storeObject(new ArrayList<>());

                assertThat(objectStore.size(), is(1L));

                assertTrue(objectStore.containsObjectRef(objectRef));

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
                objectStore = new ConcurrentHashMapObjectStore();

                assertFalse(objectStore.containsObjectRef(ObjectRef.from("2092373")));

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
    objectStore = new ConcurrentHashMapObjectStore();

    assertThat(objectStore.size(), is(0L));
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectStore = new ConcurrentHashMapObjectStore();

                objectStore.storeObject(new ArrayList<>());
                objectStore.storeObject(new HashMap<>());

                assertThat(objectStore.size(), is(2L));

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
                objectStore = new ConcurrentHashMapObjectStore();

                // store objects
                objectStore.storeObject(new ArrayList<>());
                objectStore.storeObject(new HashMap<>());

                assertThat(objectStore.size(), is(2L));
                objectStore.clear();
                assertThat(objectStore.size(), is(0L));

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
    objectStore = new ConcurrentHashMapObjectStore();

    assertThat(objectStore.size(), is(0L));

    assertTrue(objectStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() {
    Stream.of(true, false)
        .forEach(
            traceEnabled -> {
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(traceEnabled)) {
                objectStore = new ConcurrentHashMapObjectStore();

                objectStore.storeObject(new ArrayList<>());
                assertThat(objectStore.size(), is(1L));

                assertFalse(objectStore.isEmpty());

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
    objectStore = new ConcurrentHashMapObjectStore();

    assertThat(objectStore.getObjects(), is(anEmptyMap()));
  }

  @Test
  public void getObjects_someObjectsStored_objects() {
    objectStore = new ConcurrentHashMapObjectStore();

    objectStore.storeObject(new ArrayList<>());
    assertThat(objectStore.getObjects(), is(aMapWithSize(1)));
  }
  // </editor-fold>
}
