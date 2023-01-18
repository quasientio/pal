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

package net.ittera.pal.core;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.ittera.pal.common.objects.ObjectRef;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class SessionStoreTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private SessionStore sessionStore;

  @Before
  public void setup() {
    sessionStore = new SessionStore();
  }

  @Test
  public void getObjectCountInSession_emptyStore_noSuchSessionException() {
    UUID peerUuid = UUID.randomUUID();
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
    try {
      sessionStore.getObjectCountInSession(peerUuid);
      fail("Should have thrown NoSuchSessionException");
    } catch (NoSuchSessionException e) {
      // ok
    }
  }

  @Test
  public void getObjectsInSession_emptyStore_noSuchSessionException() {
    UUID peerUuid = UUID.randomUUID();
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
    try {
      sessionStore.getObjectsInSession(peerUuid);
      fail("Should have thrown NoSuchSessionException");
    } catch (NoSuchSessionException e) {
      // ok
    }
  }

  @Test
  public void getObjectsInSession_someObjectsStored_objectsReturned()
      throws NoSuchSessionException {
    final UUID peerUuid = UUID.randomUUID();
    final Random random = new Random();
    List<Object> objects =
        Arrays.asList(
            new Object[] {new ArrayList<String>(), new HashMap<>(), 9082939487L, "A fourth value"});
    for (Object object : objects) {
      sessionStore.storeInSession(
          peerUuid, ObjectRef.from(String.valueOf(random.nextInt())), object);
    }
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is((long) objects.size()));
    List<Object> objectsStored = sessionStore.getObjectsInSession(peerUuid);
    assertThat(objectsStored, containsInAnyOrder(objects.toArray(new Object[0])));
  }

  @Test
  public void getEntriesInSession_someObjectsStored_objectsReturned()
      throws NoSuchSessionException {
    final UUID peerUuid = UUID.randomUUID();
    final Random random = new Random();
    List<Object> objects =
        Arrays.asList(
            new Object[] {new ArrayList<String>(), new HashMap<>(), 9082939487L, "A fourth value"});
    List<ObjectRef> objectRefs =
        Arrays.stream(new String[] {"2398723", "6636351", "83495", "3494857"})
            .map(ObjectRef::from)
            .collect(Collectors.toList());
    for (int i = 0; i < objects.size(); i++) {
      sessionStore.storeInSession(peerUuid, objectRefs.get(i), objects.get(i));
    }
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is((long) objects.size()));
    Set<Entry<ObjectRef, Object>> objectsStored = sessionStore.getEntriesInSession(peerUuid);
    assertThat(
        objectsStored.stream().map(Entry::getKey).collect(Collectors.toList()),
        containsInAnyOrder(objectRefs.toArray(new Object[0])));
    assertThat(
        objectsStored.stream().map(Entry::getValue).collect(Collectors.toList()),
        containsInAnyOrder(objects.toArray(new Object[0])));
  }

  @Test
  public void deleteSession_emptyStore_noSuchSessionException() {
    UUID peerUuid = UUID.randomUUID();
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
    try {
      sessionStore.deleteSession(peerUuid);
      fail("Should have thrown NoSuchSessionException");
    } catch (NoSuchSessionException e) {
      // ok
    }
  }

  @Test
  public void deleteObject_emptyStore_noSuchSessionException() {
    UUID peerUuid = UUID.randomUUID();
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
    try {
      ObjectRef objRef = ObjectRef.from("23489234");
      sessionStore.deleteObject(peerUuid, objRef);
      fail("Should have thrown NoSuchSessionException");
    } catch (NoSuchSessionException e) {
      // ok
    }
  }

  @Test
  public void storeInSession_emptyStore_sessionExistsAndObjectStored()
      throws NoSuchSessionException {
    UUID peerUuid = UUID.randomUUID();
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));

    ObjectRef objRef = ObjectRef.from("23489234");
    Object object = new ArrayList<String>();
    sessionStore.storeInSession(peerUuid, objRef, object);
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(true));
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is(1L));
  }

  @Test
  public void deleteObject_storedObject_objectDeleted() throws NoSuchSessionException {
    UUID peerUuid = UUID.randomUUID();
    ObjectRef objRef = ObjectRef.from("23489234");
    Object object = new ArrayList<String>();
    sessionStore.storeInSession(peerUuid, objRef, object);
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is(1L));
    final boolean wasDeleted = sessionStore.deleteObject(peerUuid, objRef);
    assertThat(wasDeleted, is(true));
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is(0L));
  }

  @Test
  public void deleteSession_storedObjects_sessionDeleted() throws NoSuchSessionException {
    UUID peerUuid = UUID.randomUUID();
    Object object1 = new ArrayList<String>();
    ObjectRef objRef1 = ObjectRef.from("23489234");
    Object object2 = new HashMap<>();
    ObjectRef objRef2 = ObjectRef.from("7348962");
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
    sessionStore.storeInSession(peerUuid, objRef1, object1);
    sessionStore.storeInSession(peerUuid, objRef2, object2);
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(true));
    assertThat(sessionStore.getObjectCountInSession(peerUuid), is(2L));
    sessionStore.deleteSession(peerUuid);
    assertThat(sessionStore.sessionExistsFor(peerUuid), is(false));
  }
}
