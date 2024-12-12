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

package net.ittera.pal.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import net.ittera.pal.apps.intercept.InterceptableApp;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.Unwrapper;
import org.junit.Test;

public class MethodInterceptIT extends AbstractInterceptIT {

  @Test
  public void testBeforeInstanceMethod() throws Exception {

    final InterceptType interceptType = InterceptType.BEFORE;

    // args to call
    final int N = 2;
    final int factor = 5;

    final String callbackClass = "net.ittera.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    // register intercept
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            interceptType,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyBy", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);

    // give it some time so listening peer gets intercept request
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // create app instance
    ObjectRef interceptableAppInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // receive callbacks and verify class/object state in separate thread
    List<Message> callbacks = new ArrayList<>();
    Future<?> future =
        executor.submit(
            () -> {
              Message callback =
                  receiveCallbackVerifyAndReply(
                      () -> {
                        ReturnValue retValue =
                            invoke(
                                    messageBuilder.buildGetObject(
                                        myPeerUuid,
                                        InterceptableApp.class.getName(),
                                        "counter",
                                        interceptableAppInstance),
                                    verifierThinPeer)
                                .getReturnValue();

                        try {
                          assertValueIsObjectOfType(retValue, "java.lang.Integer");
                        } catch (ClassNotFoundException e) {
                          logger.error("Error asserting object type", e);
                          return new AssertionError("Error asserting object type");
                        }
                        Object unwrappedObj;
                        try {
                          unwrappedObj = Unwrapper.unwrapObject(retValue.getObject());
                        } catch (ClassNotFoundException e) {
                          logger.error("Error unwrapping object", e);
                          return new AssertionError("Error unwrapping object");
                        }
                        if (!(unwrappedObj instanceof Integer) || ((Integer) unwrappedObj) != 1) {
                          return new AssertionError("Expected Integer value = 1");
                        }
                        return null;
                      });
              callbacks.add(callback);
              logger.debug("first callback received");

              callback =
                  receiveCallbackVerifyAndReply(
                      () -> {
                        ReturnValue retValue =
                            invoke(
                                    messageBuilder.buildGetObject(
                                        myPeerUuid,
                                        InterceptableApp.class.getName(),
                                        "counter",
                                        interceptableAppInstance),
                                    verifierThinPeer)
                                .getReturnValue();
                        try {
                          assertValueIsObjectOfType(retValue, "java.lang.Integer");
                        } catch (ClassNotFoundException e) {
                          logger.error("Error asserting object type", e);
                          return new AssertionError("Error asserting object type");
                        }
                        Object unwrappedObj;
                        try {
                          unwrappedObj = Unwrapper.unwrapObject(retValue.getObject());
                        } catch (ClassNotFoundException e) {
                          logger.error("Error unwrapping object", e);
                          return new AssertionError("Error unwrapping object");
                        }
                        if (!(unwrappedObj instanceof Integer) || ((Integer) unwrappedObj) != 5) {
                          return new AssertionError("Expected Integer value = 5");
                        }
                        return null;
                      });
              callbacks.add(callback);
              logger.debug("second callback received");
            });

    // call a method that triggers N calls to the method we intercept, so we expect N callbacks
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            interceptableAppInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {N, factor}));

    future.get();

    // verify callback contents
    callbacks.forEach(
        callback -> {
          assertThat(callback, is(not(nullValue())));
          assertThat(callback.getMessageType(), is(MessageType.EXEC_MESSAGE.toByte()));
          assertThat(
              callback.getExecMessage().getExecMessageType(),
              is(ExecMessageType.CLASS_METHOD.toByte()));
          assertThat(
              callback.getExecMessage().getClassMethodCall().getClazz().getName(),
              is(callbackClass));
          assertThat(callback.getExecMessage().getClassMethodCall().getName(), is(callbackMethod));
          assertThat(callback.getExecMessage().getClassMethodCall().getParameters().length, is(1));
        });

    // throw AssertionError saved from class/object state verification
    if (assertionError != null) {
      throw assertionError;
    }
  }
}
