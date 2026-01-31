/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;

/**
 * Tests for {@link ExcludeFromCoverageGenerated} annotation.
 *
 * <p>Verifies that the annotation is properly configured for JaCoCo exclusion.
 */
public class ExcludeFromCoverageGeneratedTest {

  /** Verifies that the annotation has CLASS retention for JaCoCo to detect it. */
  @Test
  public void testRetentionPolicyIsClass() {
    Retention retention = ExcludeFromCoverageGenerated.class.getAnnotation(Retention.class);
    assertNotNull("Annotation must have @Retention", retention);
    assertEquals(
        "Retention must be CLASS for JaCoCo to detect it",
        RetentionPolicy.CLASS,
        retention.value());
  }

  /** Verifies that the annotation can target types, methods, and constructors. */
  @Test
  public void testTargetElementTypes() {
    Target target = ExcludeFromCoverageGenerated.class.getAnnotation(Target.class);
    assertNotNull("Annotation must have @Target", target);

    ElementType[] types = target.value();
    assertEquals("Annotation must target exactly 3 element types", 3, types.length);

    boolean hasType = false;
    boolean hasMethod = false;
    boolean hasConstructor = false;

    for (ElementType type : types) {
      if (type == ElementType.TYPE) {
        hasType = true;
      }
      if (type == ElementType.METHOD) {
        hasMethod = true;
      }
      if (type == ElementType.CONSTRUCTOR) {
        hasConstructor = true;
      }
    }

    assertTrue("Annotation must target TYPE", hasType);
    assertTrue("Annotation must target METHOD", hasMethod);
    assertTrue("Annotation must target CONSTRUCTOR", hasConstructor);
  }

  /**
   * Verifies that the annotation name contains "Generated" for JaCoCo recognition.
   *
   * <p>JaCoCo 0.8.2+ automatically excludes code annotated with annotations whose simple name
   * contains "Generated" (case-sensitive).
   */
  @Test
  public void testAnnotationNameContainsGenerated() {
    String simpleName = ExcludeFromCoverageGenerated.class.getSimpleName();
    assertTrue(
        "Annotation name must contain 'Generated' for JaCoCo recognition",
        simpleName.contains("Generated"));
  }

  /** Verifies the default value for the annotation. */
  @Test
  public void testDefaultValueIsEmptyString() throws NoSuchMethodException {
    String defaultValue =
        (String) ExcludeFromCoverageGenerated.class.getMethod("value").getDefaultValue();
    assertEquals("Default value must be empty string", "", defaultValue);
  }

  /**
   * Verifies that the annotation uses CLASS retention (not RUNTIME).
   *
   * <p>CLASS retention is correct for JaCoCo because JaCoCo works on bytecode, not runtime
   * reflection. This means the annotation won't be available via getAnnotation() at runtime, but
   * JaCoCo will still detect it during coverage analysis.
   */
  @Test
  public void testClassRetentionMeansNotAvailableAtRuntime() {
    // The annotation uses CLASS retention, which means it's in the bytecode but not
    // available via runtime reflection. This is the correct configuration for JaCoCo.
    Retention retention = ExcludeFromCoverageGenerated.class.getAnnotation(Retention.class);
    assertEquals(
        "CLASS retention means annotation is in bytecode but not available at runtime",
        RetentionPolicy.CLASS,
        retention.value());
  }
}
