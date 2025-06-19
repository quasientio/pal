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

package com.quasient.pal.common.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectLookupStoreBackgroundProcessorTest {

  private static final int CLEANUP_INTERVAL_SECONDS = 1;
  private static final int STATS_INTERVAL_SECONDS = 1;
  private Logger mockedLogger;
  private ConcurrentHashMapObjectLookupStore objectLookupStore;
  private ObjectLookupStoreStats objectLookupStoreStats;
  private ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  private MockedStatic<LoggerFactory> mockLoggerFactory(boolean loggingEnabled) {

    // mock Logger to enable calls to logger.debug()
    mockedLogger = mock(Logger.class);
    when(mockedLogger.isDebugEnabled()).thenReturn(loggingEnabled);
    MockedStatic<LoggerFactory> mockedLoggerFactory = mockStatic(LoggerFactory.class);
    mockedLoggerFactory
        .when(() -> LoggerFactory.getLogger(ObjectLookupStoreBackgroundProcessor.class))
        .thenReturn(mockedLogger);
    return mockedLoggerFactory;
  }

  private ObjectLookupStoreBackgroundProcessor initMockedObjectLookupStore() {
    objects =
        new ConcurrentHashMap<>(
            ConcurrentHashMapObjectLookupStore.DEFAULT_INITIAL_CAPACITY,
            ConcurrentHashMapObjectLookupStore.DEFAULT_LOAD_FACTOR);
    objectLookupStore = mock(ConcurrentHashMapObjectLookupStore.class);
    when(objectLookupStore.getObjects()).thenReturn(objects);
    objectLookupStoreStats = new ObjectLookupStoreStats();
    return new ObjectLookupStoreBackgroundProcessor(
        objectLookupStore,
        objectLookupStoreStats,
        CLEANUP_INTERVAL_SECONDS,
        STATS_INTERVAL_SECONDS);
  }

  private ObjectRef generateObjectRef(Object object) {
    final int identHash = System.identityHashCode(object);
    return ObjectRef.from(String.valueOf(identHash));
  }

  @Test
  public void run_withClearableEntries() {
    Stream.of(true, false)
        .forEach(
            loggingEnabled -> {
              MockedStatic<LoggerFactory> mockLoggerFactory = mockLoggerFactory(loggingEnabled);
              ObjectLookupStoreBackgroundProcessor objectLookupStoreProcessor =
                  initMockedObjectLookupStore();

              Object aString = new String("just a string");
              Object aList = new ArrayList<>();
              // add some entries to objects
              objects.put(generateObjectRef(aString), new IdentifiableObject(aString));
              objects.put(generateObjectRef(aList), new IdentifiableObject(aList));
              assertThat(objects.mappingCount(), is(2L));

              // clear the references
              aString = null;
              aList = null;
              System.gc();

              ObjectLookupStoreBackgroundProcessor processorSpy = spy(objectLookupStoreProcessor);
              processorSpy.start();
              // allow enough time for removeClearedEntries() to be called at least once
              try {
                Thread.sleep(CLEANUP_INTERVAL_SECONDS * 2 * 1000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              processorSpy.stop();

              // verify removeClearedEntries called
              verify(processorSpy, atLeastOnce()).removeClearedEntries();
              verify(objectLookupStore, atLeastOnce()).getObjects();

              // verify entries cleared
              assertThat(objects.mappingCount(), is(0L));

              // verify stats
              assertThat(objectLookupStoreStats.getTotalObjectsCleared().get(), is(2L));

              // verify stats printed
              if (loggingEnabled) {
                verify(mockedLogger).debug("Starting OBJECTS stats");
                verify(mockedLogger, atLeastOnce())
                    .debug(eq("OBJECTS: max size={}"), (Object) any());
                verify(mockedLogger, atLeastOnce())
                    .debug(eq("OBJECTS: current size={}"), (Object) any());
                verify(mockedLogger, atLeastOnce())
                    .debug(eq("OBJECTS: successful lookups={}"), (Object) any());
                verify(mockedLogger, atLeastOnce())
                    .debug(eq("OBJECTS: total cleared={}"), (Object) any());
              }
              mockLoggerFactory.close();
              Mockito.reset(mockedLogger, objectLookupStore);
            });
  }
}
