/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link ClassMetadataSerializer}.
 *
 * <p>Tests verify classpath scanning, JSON serialization, compression, ancestry merging, generic
 * signature generation, custom classloader support, and synthetic method filtering.
 */
public class ClassMetadataSerializerTest {

  private ClassMetadataSerializer classMetadataSerializer;

  @Before
  public void setUp() throws Exception {
    boolean scanNonPublic = false;
    classMetadataSerializer = new ClassMetadataSerializer(scanNonPublic);
  }

  private int findOccurrences(String searchString, String content) {
    // Count occurrences of the searchString
    int count = 0;
    int index = content.indexOf(searchString);
    while (index != -1) {
      count++;
      index = content.indexOf(searchString, index + searchString.length());
    }
    return count;
  }

  @Test
  public void testScan() throws Exception {
    Path classesMetadataPath =
        classMetadataSerializer.scannedClasspathToJson(false, null, null, false);
    String classesMetadata = Files.readString(classesMetadataPath);
    Files.delete(classesMetadataPath);

    String searchString = "className";
    // expect 10000 classes at least
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, classesMetadata) > minExpectedClassCount);

    // expect at least 300 java.util classes
    String javaUtilClassNameEntry = "\"className\":\"java.util.";
    assertTrue(findOccurrences(javaUtilClassNameEntry, classesMetadata) > 300);
  }

  @Test
  public void testScanWithPrefixExcludes() throws Exception {
    Set<String> additionalExcludePrefixes = new HashSet<>();
    additionalExcludePrefixes.add("java.util");

    Path scannedClassesPath =
        classMetadataSerializer.scannedClasspathToJson(
            false, null, additionalExcludePrefixes, false);
    String scannedClasses = Files.readString(scannedClassesPath);
    Files.delete(scannedClassesPath);

    String searchString = "className";
    // expect 10000 classes at least
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, scannedClasses) > minExpectedClassCount);

    // expect no java.util class
    String javaUtilClassNameEntry = "\"className\" : \"java.util.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, scannedClasses));
  }

  @Test
  public void testScanWithIncludeClasses() throws Exception {
    Set<String> includeClasses = new HashSet<>();
    includeClasses.add("java.util.List");
    includeClasses.add("java.util.Set");
    includeClasses.add("java.util.ArrayList");

    Path scannedClassesPath =
        classMetadataSerializer.scannedClasspathToJson(false, includeClasses, null, false);
    String scannedClasses = Files.readString(scannedClassesPath);
    Files.delete(scannedClassesPath);

    String searchString = "className";
    assertEquals(3, findOccurrences(searchString, scannedClasses));

    // verify that some inherited methods are included

    // inherited from Object
    assertEquals(3 * 3, findOccurrences("\"wait\"", scannedClasses));
    // inherited from List and Set
    assertTrue(findOccurrences("\"of\"", scannedClasses) > 30);
  }

  // ============================================================================
  // Test specifications for issue #460 - awaiting implementation in #461
  // ============================================================================

  /**
   * Tests that compression and Base64 encoding produces valid GZIP+Base64 output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withCompression_returnsCompressedBase64]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void scannedClasspathToJson_withCompression_returnsCompressedBase64() {
    // Given: ClassMetadataSerializer instance
    // When: scannedClasspathToJson called with compressAndEncode=true
    // Then: Result file contains GZIP compressed and Base64 encoded data that can be decoded
    //       back to valid JSON

    // TODO(#461): Implement test logic
    // 1. Call scannedClasspathToJson(true, Set.of("java.lang.String"), null, false)
    // 2. Read the output file as bytes
    // 3. Base64 decode the content
    // 4. GZIP decompress the decoded bytes
    // 5. Verify the decompressed content is valid JSON containing "className":"java.lang.String"
    fail("Not yet implemented");
  }

  /**
   * Tests that mergeAncestry=true includes inherited methods and fields from parent classes.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withMergeAncestry_includesInheritedMembers]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void scannedClasspathToJson_withMergeAncestry_includesInheritedMembers() {
    // Given: Class that extends parent with methods/fields (e.g., org.example.paltest.SubClass)
    // When: scannedClasspathToJson called with mergeAncestry=true
    // Then: JSON includes parent methods and fields with "inheritedFrom" marker

    // TODO(#461): Implement test logic
    // 1. Scan org.example.paltest.SubClass with mergeAncestry=true
    // 2. Parse the JSON output
    // 3. Verify methods array contains entries with "inheritedFrom":"org.example.paltest.BaseClass"
    // 4. Verify fields array contains entries with "inheritedFrom" marker for inherited fields
    // 5. Verify java.lang.Object methods (like toString, hashCode) are included
    fail("Not yet implemented");
  }

  /**
   * Tests that overridden methods are marked with the "overridden" flag set to true.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.mergeMethods_overriddenMethod_markedAsOverride]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void mergeMethods_overriddenMethod_markedAsOverride() {
    // Given: Child class overriding parent method (SubClass.getA() overrides BaseClass.getA())
    // When: Ancestry merged via scannedClasspathToJson with mergeAncestry=true
    // Then: The overridden method has "overridden":true in the JSON output

    // TODO(#461): Implement test logic
    // 1. Scan org.example.paltest.SubClass with mergeAncestry=true
    // 2. Parse JSON and find the method entry for "getA"
    // 3. Assert that the method has "overridden":true
    // 4. Assert that the method has "inheritedFrom":"org.example.paltest.BaseClass"
    fail("Not yet implemented");
  }

  /**
   * Tests that shadowed fields are marked correctly with the override flag.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.mergeFields_shadowedField_markedCorrectly]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void mergeFields_shadowedField_markedCorrectly() {
    // Given: Child class with field shadowing parent field (SubClass.a shadows BaseClass.a)
    // When: Ancestry merged via scannedClasspathToJson with mergeAncestry=true
    // Then: The shadowing field has "overridden":true in JSON output

    // TODO(#461): Implement test logic
    // 1. Scan org.example.paltest.SubClass with mergeAncestry=true
    // 2. Parse JSON and find the field entry for "a"
    // 3. Assert that the shadowing field has "overridden":true
    // 4. Assert that "inheritedFrom" points to the original class
    fail("Not yet implemented");
  }

  /**
   * Tests that generic parameter types are included in method signatures.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.methodSignature_genericParameters_includesTypeInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void methodSignature_genericParameters_includesTypeInfo() {
    // Given: Method with generic parameter (List<String>) in org.example.paltest.GenericMethods
    // When: Signature generated via scannedClasspathToJson
    // Then: Signature includes generic type information like "java.util.List<java.lang.String>"

    // TODO(#461): Implement test logic
    // 1. Scan org.example.paltest.GenericMethods
    // 2. Parse JSON and find the method "echo"
    // 3. Verify parameters array contains type "java.util.List<java.lang.String>"
    // 4. Verify return type includes generic bounds information
    fail("Not yet implemented");
  }

  /**
   * Tests that methods with bounded type parameters include bounds in signatures.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.methodSignature_typeBounds_handledCorrectly]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void methodSignature_typeBounds_handledCorrectly() {
    // Given: Method with bounded type parameter (<T extends Comparable<T>>)
    //        in org.example.paltest.TypeBoundsClass
    // When: Signature generated via scannedClasspathToJson
    // Then: Bounds are included in the signature (e.g., "T extends Comparable")

    // TODO(#461): Implement test logic
    // 1. Scan org.example.paltest.TypeBoundsClass
    // 2. Parse JSON and find the method with bounded type parameter
    // 3. Verify the method signature contains the type bound information
    // 4. Verify both upper bounds (extends) are represented
    fail("Not yet implemented");
  }

  /**
   * Tests that a custom ClassLoader is used when provided.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withCustomClassloader_usesProvidedLoader]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void scannedClasspathToJson_withCustomClassloader_usesProvidedLoader() {
    // Given: Custom ClassLoader provided to ClassMetadataSerializer constructor
    // When: scannedClasspathToJson called
    // Then: Custom classloader is used for scanning (verified by scanning classes only
    //       visible to that classloader)

    // TODO(#461): Implement test logic
    // 1. Create a ClassMetadataSerializer with a custom classloader (or mock CustomClassloader)
    // 2. Call scannedClasspathToJson
    // 3. Verify the scan uses the provided classloader by checking that classes
    //    from the custom classloader's classpath are included
    // 4. Alternative: Verify via classloader interaction if CustomClassloader can be mocked
    fail("Not yet implemented");
  }

  /**
   * Tests that AspectJ-generated synthetic methods are filtered from output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_syntheticMethods_filtered]
   */
  @Test
  @Ignore("Awaiting implementation in #461")
  public void scannedClasspathToJson_syntheticMethods_filtered() {
    // Given: Class with AspectJ-generated synthetic methods (methods containing "_aroundBody"
    //        or "$" in their names, or marked as synthetic)
    // When: scannedClasspathToJson called
    // Then: Synthetic methods are not included in the output JSON

    // TODO(#461): Implement test logic
    // 1. Scan a class known to have AspectJ weaving applied (or any class with synthetic methods)
    // 2. Parse the JSON output
    // 3. Verify no method names contain "_aroundBody"
    // 4. Verify no method names contain "$"
    // 5. Verify the output doesn't include entries for synthetic methods
    fail("Not yet implemented");
  }
}
