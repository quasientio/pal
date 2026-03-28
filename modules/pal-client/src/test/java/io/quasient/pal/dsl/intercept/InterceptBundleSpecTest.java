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
package io.quasient.pal.dsl.intercept;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.common.lang.intercept.InterceptType;
import java.time.Duration;
import org.junit.Test;

/**
 * Unit tests for the {@code InterceptBundleSpec} value class.
 *
 * <p>These tests define the contract for {@code InterceptBundleSpec}. Each test documents expected
 * behavior via Given/When/Then comments.
 */
public class InterceptBundleSpecTest {

  @Test
  public void builder_setsAllFields() {
    // Given: An InterceptBundleSpec builder with name, defaults, and 2 intercept specs
    InterceptBundleDefaults defaults =
        new InterceptBundleDefaults("peer-1", 5, Duration.ofMinutes(1), true, null, null, null);

    InterceptSpec spec1 =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    InterceptSpec spec2 =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("baz")
            .type(InterceptType.AFTER)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBaz")
            .build();

    // When: build() is called
    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder("test-bundle")
            .defaults(defaults)
            .addIntercept(spec1)
            .addIntercept(spec2)
            .build();

    // Then: getName() returns the bundle name, getDefaults() returns the configured defaults,
    //       getIntercepts() returns a list of 2 InterceptSpecs
    assertThat(bundle.getBundleName(), is("test-bundle"));
    assertThat(bundle.getDefaults(), is(defaults));
    assertThat(bundle.getIntercepts().size(), is(2));
  }

  @Test(expected = NullPointerException.class)
  public void builder_requiresBundleName() {
    // Given: An InterceptBundleSpec builder with intercepts set but bundle name omitted
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    // When: build() is called
    // Then: NullPointerException is thrown
    InterceptBundleSpec.builder(null).addIntercept(spec).build();
  }

  @Test(expected = IllegalStateException.class)
  public void builder_requiresAtLeastOneIntercept() {
    // Given: An InterceptBundleSpec builder with bundle name set but empty intercept list
    // When: build() is called
    // Then: IllegalStateException is thrown
    InterceptBundleSpec.builder("empty-bundle").build();
  }

  @Test
  public void builder_defaultsAreOptional() {
    // Given: An InterceptBundleSpec builder with name and intercepts but no defaults
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    // When: build() is called
    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder("my-bundle").addIntercept(spec).build();

    // Then: getDefaults() returns a no-op defaults object (all fields null)
    assertNotNull(bundle.getDefaults());
    assertThat(bundle.getDefaults(), is(InterceptBundleDefaults.EMPTY));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void interceptsListIsImmutable() {
    // Given: A built InterceptBundleSpec with intercepts
    InterceptSpec spec =
        InterceptSpec.builder()
            .targetClass("com.acme.Foo")
            .targetName("bar")
            .type(InterceptType.BEFORE)
            .callbackClass("com.acme.Cb")
            .callbackMethod("onBar")
            .build();

    InterceptBundleSpec bundle =
        InterceptBundleSpec.builder("my-bundle").addIntercept(spec).build();

    // When: getIntercepts() is called and an attempt is made to add to the returned list
    // Then: UnsupportedOperationException is thrown
    bundle.getIntercepts().add(spec);
  }
}
