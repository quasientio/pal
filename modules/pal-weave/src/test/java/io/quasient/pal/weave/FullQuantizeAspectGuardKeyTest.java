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
package io.quasient.pal.weave;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Test;

/**
 * Specification tests for {@link FullQuantizeAspect#computeMethodGuardKey(JoinPoint)} and the
 * {@code GUARD_KEY_CACHE} / {@code INTERNED_GUARD_KEYS} it reads through.
 *
 * <p>The key produced by this method is the identity used by the exec-site advice to decide whether
 * a call-site has already dispatched the current invocation. Both the <em>format</em> of the key
 * (so call-site and exec-site agree) and the <em>reference identity</em> of the returned string (so
 * the {@code ==} fast path in {@code callSiteAlreadyDispatched} fires) matter. These tests cover
 * both.
 */
public class FullQuantizeAspectGuardKeyTest {

  /** Interface used for interface-dispatch fixtures. */
  interface Iface {

    /** Interface-declared method used to exercise interface-dispatch key scoping. */
    void iMethod();
  }

  /** Base class used as a static receiver type with an overridable method. */
  static class Base implements Iface {

    /** Concrete implementation of {@link Iface#iMethod()} used by virtual-dispatch tests. */
    @Override
    public void iMethod() {}

    /** Overridable instance method used to exercise virtual-dispatch key scoping. */
    public void overridable() {}
  }

  /** Subclass overriding {@link Base#overridable()} to exercise virtual-dispatch scoping. */
  static class Sub extends Base {

    /** Overriding implementation that lets the runtime-class branch of the key computation fire. */
    @Override
    public void overridable() {}
  }

  /**
   * Builds a mock join point that returns the given method-signature components. This mirrors the
   * shape AspectJ exposes to advice at both call and execution sites, so the unit under test can be
   * exercised without running the AspectJ weaver.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static JoinPoint mockMethodJoinPoint(
      Class<?> declaringType, String methodName, Class<?>[] params, int modifiers, Object target) {
    MethodSignature ms = mock(MethodSignature.class);
    when(ms.getDeclaringType()).thenReturn((Class) declaringType);
    when(ms.getName()).thenReturn(methodName);
    when(ms.getParameterTypes()).thenReturn(params);
    when(ms.getModifiers()).thenReturn(modifiers);

    JoinPoint.StaticPart sp = mock(JoinPoint.StaticPart.class);
    when(sp.getSignature()).thenReturn(ms);

    JoinPoint jp = mock(JoinPoint.class);
    when(jp.getSignature()).thenReturn(ms);
    when(jp.getStaticPart()).thenReturn(sp);
    when(jp.getTarget()).thenReturn(target);
    return jp;
  }

  /**
   * Verifies the key format is {@code <class>#<method>(<params>)} for a no-arg instance method with
   * a runtime receiver of the declaring class.
   */
  @Test
  public void shouldFormatKeyAsClassHashMethodParenParams() {
    JoinPoint jp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, new Base());

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertThat(key, startsWith(Base.class.getName() + "#overridable("));
    assertThat(key, endsWith(")"));
    assertEquals(Base.class.getName() + "#overridable()", key);
  }

  /**
   * Verifies the parameter list is rendered as comma-separated {@link Class#getName()} values,
   * including primitives, and that no trailing comma is emitted.
   */
  @Test
  public void shouldIncludeParameterTypesCommaSeparated() {
    JoinPoint jp =
        mockMethodJoinPoint(
            Base.class,
            "foo",
            new Class<?>[] {String.class, int.class, Object.class},
            0,
            new Base());

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Base.class.getName() + "#foo(java.lang.String,int,java.lang.Object)", key);
  }

  /**
   * Verifies the cache returns the same {@link String} reference on a repeat invocation with the
   * same join point — the hot-path allocation-free property.
   */
  @Test
  public void shouldReturnSameReferenceOnRepeatedCallsWithSameJoinPoint() {
    JoinPoint jp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, new Base());

    String first = FullQuantizeAspect.computeMethodGuardKey(jp);
    String second = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertSame("Cache must return the same reference for the same join point", first, second);
  }

  /**
   * Verifies the intern map canonicalises across distinct join points: two join points that differ
   * only in their {@link JoinPoint.StaticPart} but resolve to the same (class, method-descriptor)
   * pair must return the <em>same</em> {@link String} instance. This is what makes the {@code ==}
   * fast path in {@code callSiteAlreadyDispatched} fire for the woven-to-woven direct-call case,
   * where the call-site and exec-site naturally carry different StaticParts.
   */
  @Test
  public void shouldReturnSameReferenceAcrossDifferentStaticPartsWithSameDescriptor() {
    Base b = new Base();
    JoinPoint callSite = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, b);
    JoinPoint execSite = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, b);

    String callKey = FullQuantizeAspect.computeMethodGuardKey(callSite);
    String execKey = FullQuantizeAspect.computeMethodGuardKey(execSite);

    assertSame(
        "Interning must make call-site and exec-site keys reference-equal", callKey, execKey);
  }

  /**
   * Verifies that for an instance method the key includes the <em>runtime</em> receiver class, not
   * the signature's declaring type. This is the virtual-dispatch scoping that lets the call-site
   * (which sees the static type of the receiver) and the exec-site (which sees the overriding
   * class) agree.
   */
  @Test
  public void shouldKeyInstanceMethodByRuntimeTargetClass() {
    Sub s = new Sub();
    JoinPoint jp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, s);

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Sub.class.getName() + "#overridable()", key);
  }

  /**
   * Verifies that for an interface-declared method called on a concrete implementor, the key names
   * the <em>implementor</em>, not the interface. This mirrors interface-dispatch weaving.
   */
  @Test
  public void shouldKeyInterfaceMethodByConcreteImplementor() {
    Base b = new Base();
    JoinPoint jp = mockMethodJoinPoint(Iface.class, "iMethod", new Class<?>[0], 0, b);

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Base.class.getName() + "#iMethod()", key);
  }

  /**
   * Verifies that static methods — whose target is {@code null} — key by the signature's declaring
   * type, which is stable across call and execution.
   */
  @Test
  public void shouldKeyStaticMethodByDeclaringType() {
    JoinPoint jp = mockMethodJoinPoint(Base.class, "sm", new Class<?>[0], Modifier.STATIC, null);

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Base.class.getName() + "#sm()", key);
  }

  /**
   * Verifies the declaring-type fallback also applies when an instance-method join point exposes a
   * {@code null} target (defensive — AspectJ normally provides a non-null target for instance
   * calls, but execution-site advice on constructor-style joinpoints or advice woven on the aspect
   * itself can present a null).
   */
  @Test
  public void shouldFallBackToDeclaringTypeWhenTargetIsNull() {
    JoinPoint jp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, null);

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Base.class.getName() + "#overridable()", key);
  }

  /**
   * Verifies the cache is correctly partitioned by runtime class: the same method name with the
   * same parameter list, invoked on different runtime receiver classes, produces distinct keys.
   */
  @Test
  public void shouldDifferentiateKeysByRuntimeReceiverClass() {
    Base b = new Base();
    Sub s = new Sub();

    JoinPoint baseJp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, b);
    JoinPoint subJp = mockMethodJoinPoint(Base.class, "overridable", new Class<?>[0], 0, s);

    String baseKey = FullQuantizeAspect.computeMethodGuardKey(baseJp);
    String subKey = FullQuantizeAspect.computeMethodGuardKey(subJp);

    assertNotSame(
        "Different runtime receiver classes must yield different canonical keys", baseKey, subKey);
    assertThat(baseKey, startsWith(Base.class.getName() + "#"));
    assertThat(subKey, startsWith(Sub.class.getName() + "#"));
  }

  /**
   * Verifies that different method names produce different keys (sanity-check that the cache does
   * not over-collapse by class alone).
   */
  @Test
  public void shouldDifferentiateKeysByMethodName() {
    Base b = new Base();
    JoinPoint a = mockMethodJoinPoint(Base.class, "alpha", new Class<?>[0], 0, b);
    JoinPoint z = mockMethodJoinPoint(Base.class, "zulu", new Class<?>[0], 0, b);

    String keyA = FullQuantizeAspect.computeMethodGuardKey(a);
    String keyZ = FullQuantizeAspect.computeMethodGuardKey(z);

    assertNotSame(keyA, keyZ);
    assertEquals(Base.class.getName() + "#alpha()", keyA);
    assertEquals(Base.class.getName() + "#zulu()", keyZ);
  }

  /**
   * Verifies that overloaded methods with different parameter lists produce different keys, so the
   * cache can distinguish between {@code foo(int)} and {@code foo(int, int)}.
   */
  @Test
  public void shouldDifferentiateKeysByParameterList() {
    Base b = new Base();
    JoinPoint one = mockMethodJoinPoint(Base.class, "foo", new Class<?>[] {int.class}, 0, b);
    JoinPoint two =
        mockMethodJoinPoint(Base.class, "foo", new Class<?>[] {int.class, int.class}, 0, b);

    String keyOne = FullQuantizeAspect.computeMethodGuardKey(one);
    String keyTwo = FullQuantizeAspect.computeMethodGuardKey(two);

    assertNotSame(keyOne, keyTwo);
    assertEquals(Base.class.getName() + "#foo(int)", keyOne);
    assertEquals(Base.class.getName() + "#foo(int,int)", keyTwo);
  }

  /**
   * Verifies that no-arg method keys render as {@code ...#m()} — empty parentheses with no comma.
   */
  @Test
  public void shouldRenderNoArgMethodWithEmptyParameterList() {
    JoinPoint jp = mockMethodJoinPoint(Base.class, "noargs", new Class<?>[0], 0, new Base());

    String key = FullQuantizeAspect.computeMethodGuardKey(jp);

    assertEquals(Base.class.getName() + "#noargs()", key);
  }

  /**
   * Stresses the cache and intern map under concurrent access: many threads computing the key for
   * join points that resolve to the same (class, method-descriptor) pair must all observe the
   * <em>same</em> canonical {@link String} reference, and none may throw.
   */
  @Test
  public void shouldBeThreadSafeUnderConcurrentAccess() throws Exception {
    final int threadCount = 16;
    final int iterations = 500;
    final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    try {
      final CountDownLatch start = new CountDownLatch(1);
      final List<Future<List<String>>> futures = new ArrayList<>(threadCount);

      for (int i = 0; i < threadCount; i++) {
        futures.add(
            pool.submit(
                () -> {
                  List<String> localKeys = new ArrayList<>(iterations);
                  start.await();
                  for (int j = 0; j < iterations; j++) {
                    JoinPoint jp =
                        mockMethodJoinPoint(
                            Base.class, "overridable", new Class<?>[0], 0, new Base());
                    localKeys.add(FullQuantizeAspect.computeMethodGuardKey(jp));
                  }
                  return localKeys;
                }));
      }

      start.countDown();

      String canonical = null;
      for (Future<List<String>> f : futures) {
        List<String> perThread = f.get(30, TimeUnit.SECONDS);
        for (String k : perThread) {
          if (canonical == null) {
            canonical = k;
          }
          assertSame(
              "Every thread must see the same canonical String for Base#overridable()",
              canonical,
              k);
        }
      }
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
