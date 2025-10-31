/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.annotations;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.example.paltest.GenericMethods;
import org.junit.Test;

public class AnnotationsProcessorTest {

  private static class RecordingProcessor implements AnnotationProcessor {
    private final String name;
    private final List<String> calls;
    private final int order;
    private final boolean supportAll;

    RecordingProcessor(String name, List<String> calls, int order, boolean supportAll) {
      this.name = name;
      this.calls = calls;
      this.order = order;
      this.supportAll = supportAll;
    }

    @Override
    public boolean supports(Class<?> clazz) {
      return supportAll;
    }

    @Override
    public void process(Class<?> clazz) {
      calls.add(name + ":" + clazz.getName());
    }

    @Override
    public int order() {
      return order;
    }
  }

  // Use a non-core, non-pal class from testdata package

  @Test
  public void skipsCoreAndPalClasses_ordersProcessors() {
    List<String> calls = new ArrayList<>();
    // two processors with different order
    RecordingProcessor p2 = new RecordingProcessor("p2", calls, 2, true);
    RecordingProcessor p1 = new RecordingProcessor("p1", calls, 1, true);
    Set<AnnotationProcessor> set = new LinkedHashSet<>();
    set.add(p2);
    set.add(p1);

    AnnotationsProcessor ap = new AnnotationsProcessor(set);

    // Skip core Java
    ap.classLoaded(String.class);
    assertThat(calls.isEmpty(), is(true));

    // Skip Pal core
    ap.classLoaded(AnnotationProcessor.class);
    assertThat(calls.isEmpty(), is(true));

    // Process non-core, non-pal
    ap.classLoaded(GenericMethods.class);
    // Expect order p1 then p2 due to order value
    String n = GenericMethods.class.getName();
    assertThat(calls, contains("p1:" + n, "p2:" + n));
  }
}
