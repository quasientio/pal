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
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectStoreBackgroundProcessorTest {

  private final int CLEANUP_INTERVAL_SECS = 1;
  private final int STATS_INTERVAL_SECS = 1;
  private Logger mockedLogger;
  private ConcurrentHashMapObjectStore objectStore;
  private ObjectStoreStats objectStoreStats;
  private ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  private MockedStatic<LoggerFactory> mockLoggerFactory(boolean loggingEnabled) {

    // mock Logger to enable calls to logger.debug()
    mockedLogger = mock(Logger.class);
    when(mockedLogger.isDebugEnabled()).thenReturn(loggingEnabled);
    MockedStatic<LoggerFactory> mockedLoggerFactory = mockStatic(LoggerFactory.class);
    mockedLoggerFactory
        .when(() -> LoggerFactory.getLogger(ObjectStoreBackgroundProcessor.class))
        .thenReturn(mockedLogger);
    return mockedLoggerFactory;
  }

  private ObjectStoreBackgroundProcessor initMockedObjectStore(
      int cleanupIntervalSecs, int statsIntervalSecs) {
    objects =
        new ConcurrentHashMap<>(
            ConcurrentHashMapObjectStore.DEFAULT_INITIAL_CAPACITY,
            ConcurrentHashMapObjectStore.DEFAULT_LOAD_FACTOR);
    objectStore = mock(ConcurrentHashMapObjectStore.class);
    when(objectStore.getObjects()).thenReturn(objects);
    objectStoreStats = new ObjectStoreStats();
    return new ObjectStoreBackgroundProcessor(
        objectStore, objectStoreStats, cleanupIntervalSecs, statsIntervalSecs);
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
              try (MockedStatic<LoggerFactory> ignored = mockLoggerFactory(loggingEnabled)) {
                ObjectStoreBackgroundProcessor objectStoreProcessor =
                    initMockedObjectStore(CLEANUP_INTERVAL_SECS, STATS_INTERVAL_SECS);
                ObjectStoreBackgroundProcessor processorSpy = spy(objectStoreProcessor);

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

                processorSpy.start();
                // allow enough time for removeClearedEntries() to be called at least once
                try {
                  Thread.sleep(CLEANUP_INTERVAL_SECS * 2 * 1000);
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
                processorSpy.interrupt();

                // verify removeClearedEntries called
                verify(processorSpy, atLeastOnce()).removeClearedEntries();
                verify(objectStore, atLeastOnce()).getObjects();

                // verify entries cleared
                assertThat(objects.mappingCount(), is(0L));

                // verify stats
                assertThat(objectStoreStats.getTotalObjectsCleared().get(), is(2L));

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
              }
              Mockito.reset(mockedLogger);
              Mockito.reset(objectStore);
            });
  }
}
