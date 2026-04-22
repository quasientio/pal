/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.runtime.ThreadAffinity;
import io.quasient.pal.core.execution.InvocationExecutor;
import io.quasient.pal.core.execution.ThreadAffinityDispatcher;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the private {@code resolveManagedTarget} helper in {@link
 * BaseExecMessageDispatcher}.
 *
 * <p>The helper is the key fall-back path introduced for replay support of lazy-bean frameworks
 * (Quarkus Arc, Spring {@code @Lazy}, Guice lazy providers): when {@code ReplayObjectStore} cannot
 * supply the WAL-recorded target during a replay-injected call, the dispatcher consults the
 * affinity-bound {@link InvocationExecutor#resolveTarget} hook to ask the managed-bean container
 * for a live instance.
 *
 * <p>These tests exercise every branch of the helper by reflecting on the private method and
 * driving it directly. End-to-end coverage of the dispatch wiring is provided by the {@code
 * quarkus-petclinic} example replay.
 */
public class BaseExecMessageDispatcherResolveManagedTargetTest {

  /** Sample non-null AccessibleObject used to satisfy the early null check in the helper. */
  private Method sampleMethod;

  /** Reflective handle to the private helper under test. */
  private Method resolveManagedTargetMethod;

  /** ThreadAffinityDispatcher injected into the dispatcher; tests register executors on it. */
  private ThreadAffinityDispatcher threadAffinityDispatcher;

  /** Test dispatcher instance used to invoke the helper. */
  private TestableDispatcher dispatcher;

  /** Sets up reflective handles, a fresh affinity dispatcher, and the test dispatcher. */
  @Before
  public void setUp() throws Exception {
    sampleMethod = String.class.getMethod("toString");
    resolveManagedTargetMethod =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "resolveManagedTarget", ExecMessage.class, String.class, AccessibleObject.class);
    resolveManagedTargetMethod.setAccessible(true);

    threadAffinityDispatcher = new ThreadAffinityDispatcher();
    dispatcher = new TestableDispatcher();
    setField(dispatcher, "threadAffinityDispatcher", threadAffinityDispatcher);
  }

  /**
   * Sets a field declared on {@link AbstractDispatcher} via reflection.
   *
   * @param target the dispatcher instance
   * @param fieldName the field name
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = AbstractDispatcher.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Builds a minimal {@link ExecMessage} carrying an {@link InstanceMethodCall} with the given
   * thread affinity. The dispatcher reads the affinity to look up a registered executor.
   *
   * @param affinity the affinity key, or {@code null} to leave the field at its default
   * @return the configured message
   */
  private static ExecMessage messageWithAffinity(String affinity) {
    ExecMessage msg = new ExecMessage();
    InstanceMethodCall call = new InstanceMethodCall();
    call.name = "toString";
    msg.instanceMethodCall = call;
    if (affinity != null) {
      msg.setThreadAffinity(affinity);
    }
    return msg;
  }

  /** Invokes the private helper, unwrapping any reflective exception. */
  private Object invokeHelper(ExecMessage msg, String declaringClassName, AccessibleObject ao)
      throws Throwable {
    try {
      return resolveManagedTargetMethod.invoke(dispatcher, msg, declaringClassName, ao);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /**
   * When {@code accessibleObject} is null the helper short-circuits without consulting the affinity
   * dispatcher. The early return prevents a managed-bean lookup from succeeding for an operation we
   * cannot ultimately invoke (the dispatcher would still be unable to call the method).
   */
  @Test
  public void nullAccessibleObjectReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);
    threadAffinityDispatcher.register(ThreadAffinity.SERVICE_REQUEST, alwaysResolve("ignored"));

    Object resolved = invokeHelper(msg, String.class.getName(), null);

    assertThat(resolved, is(nullValue()));
  }

  /**
   * Without a thread affinity on the message the helper has no executor to consult and must return
   * {@code null} so the dispatcher falls through to the existing phantom-skip path.
   */
  @Test
  public void nullAffinityReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(null);

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /** An explicitly empty affinity is treated identically to a null affinity. */
  @Test
  public void emptyAffinityReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity("");

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /**
   * If the affinity is set but no executor has been registered for it, the helper returns null —
   * the dispatcher cannot synthesize a target without a managed-bean container behind the affinity.
   */
  @Test
  public void noExecutorRegisteredReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /**
   * The default {@link InvocationExecutor#resolveTarget} returns {@code null}, so an executor that
   * has not opted into target resolution leaves the helper returning null and the dispatcher falls
   * through to phantom-skip.
   */
  @Test
  public void executorWithDefaultResolveTargetReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);
    threadAffinityDispatcher.register(
        ThreadAffinity.SERVICE_REQUEST, invocation -> invocation.call());

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /**
   * If the executor's resolver throws, the helper swallows the exception and returns null so the
   * dispatcher can still fall through to phantom-skip rather than aborting the entry-point
   * dispatch.
   */
  @Test
  public void executorResolverThrowsReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);
    threadAffinityDispatcher.register(
        ThreadAffinity.SERVICE_REQUEST,
        new InvocationExecutor() {
          @Override
          public Object execute(Callable<Object> invocation) throws Exception {
            return invocation.call();
          }

          @Override
          public Object resolveTarget(Class<?> declaringType) {
            throw new RuntimeException("simulated lookup failure");
          }
        });

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /**
   * Happy path: when the affinity executor resolves a managed bean for the declaring class, the
   * helper hands the live instance back to the dispatcher. Also verifies that the resolver is
   * called with the loaded {@link Class} (not just the class name string).
   */
  @Test
  public void executorResolverSuppliesTarget() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);
    String managedBean = "managed-bean-instance";
    AtomicReference<Class<?>> capturedType = new AtomicReference<>();
    threadAffinityDispatcher.register(
        ThreadAffinity.SERVICE_REQUEST,
        new InvocationExecutor() {
          @Override
          public Object execute(Callable<Object> invocation) throws Exception {
            return invocation.call();
          }

          @Override
          public Object resolveTarget(Class<?> declaringType) {
            capturedType.set(declaringType);
            return managedBean;
          }
        });

    Object resolved = invokeHelper(msg, String.class.getName(), sampleMethod);

    assertThat(resolved, sameInstance(managedBean));
    assertThat(capturedType.get(), sameInstance(String.class));
  }

  /**
   * If the declaring class is not loadable from the context classloader, the helper returns null —
   * there is no live target the executor could resolve against.
   */
  @Test
  public void unknownDeclaringClassReturnsNull() throws Throwable {
    ExecMessage msg = messageWithAffinity(ThreadAffinity.SERVICE_REQUEST);
    threadAffinityDispatcher.register(ThreadAffinity.SERVICE_REQUEST, alwaysResolve("ignored"));

    Object resolved = invokeHelper(msg, "io.quasient.pal.does.not.Exist", sampleMethod);

    assertThat(resolved, is(nullValue()));
  }

  /** Builds an executor whose {@code resolveTarget} always returns the supplied instance. */
  private static InvocationExecutor alwaysResolve(Object instance) {
    return new InvocationExecutor() {
      @Override
      public Object execute(Callable<Object> invocation) throws Exception {
        return invocation.call();
      }

      @Override
      public Object resolveTarget(Class<?> declaringType) {
        return instance;
      }
    };
  }

  /**
   * Minimal concrete dispatcher used to exercise the private helper. Mirrors the {@code MinimalOk}
   * pattern in {@link BaseExecMessageDispatcherDispatchTest}.
   */
  static class TestableDispatcher extends BaseExecMessageDispatcher {

    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt,
        Object sender,
        Object target,
        Object[] args,
        boolean includeDeclaredExceptions) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      return new ExecMessage();
    }

    @Override
    protected Object invokeIncoming(
        AccessibleObject accessibleObject,
        Object target,
        List<MessageArgument> args,
        Object value) {
      return null;
    }

    @Override
    protected boolean returnsVoid(AccessibleObject accessibleObject) {
      return false;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return false;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Obj> getArgsList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }
}
