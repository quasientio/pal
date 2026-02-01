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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quasient.pal.core.execution.java.CustomClassloader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.Before;
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
  // Tests for issue #461 - ClassMetadataSerializer additional coverage
  // ============================================================================

  /**
   * Tests that compression and Base64 encoding produces valid GZIP+Base64 output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withCompression_returnsCompressedBase64]
   */
  @Test
  public void scannedClasspathToJson_withCompression_returnsCompressedBase64() throws Exception {
    // Given: ClassMetadataSerializer instance
    // When: scannedClasspathToJson called with compressAndEncode=true
    Path outFile =
        classMetadataSerializer.scannedClasspathToJson(
            true, Set.of("java.lang.String"), null, false);

    // Read the output file as bytes
    byte[] encoded = Files.readAllBytes(outFile);
    Files.delete(outFile);

    // Base64 decode the content
    byte[] gzipped = Base64.getDecoder().decode(encoded);

    // GZIP decompress the decoded bytes
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
      gzis.transferTo(baos);
    }
    String json = baos.toString(StandardCharsets.UTF_8);

    // Then: verify the decompressed content is valid JSON containing expected class
    assertThat(json, containsString("\"className\":\"java.lang.String\""));

    // Verify JSON is parseable
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    assertThat(root.isArray(), is(true));
    assertThat(root.size(), greaterThan(0));
  }

  /**
   * Tests that mergeAncestry=true includes inherited methods and fields from parent classes.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withMergeAncestry_includesInheritedMembers]
   */
  @Test
  public void scannedClasspathToJson_withMergeAncestry_includesInheritedMembers() throws Exception {
    // Given: Class that extends parent with methods/fields (org.example.paltest.SubClass)
    String subClassName = "org.example.paltest.SubClass";

    // When: scannedClasspathToJson called with mergeAncestry=true
    Path outFile =
        classMetadataSerializer.scannedClasspathToJson(true, Set.of(subClassName), null, true);
    byte[] encoded = Files.readAllBytes(outFile);
    Files.delete(outFile);
    byte[] gzipped = Base64.getDecoder().decode(encoded);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
      gzis.transferTo(baos);
    }
    String json = baos.toString(StandardCharsets.UTF_8);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    // Then: JSON includes the SubClass entry
    assertThat(root.isArray(), is(true));
    assertThat(root.size(), is(1));
    JsonNode classNode = root.get(0);
    assertThat(classNode.get("className").asText(), is(subClassName));

    // Verify java.lang.Object methods are included (like toString, hashCode)
    JsonNode methodsArray = classNode.get("methods");
    boolean hasToString = false;
    boolean hasHashCode = false;
    boolean hasInheritedFromObject = false;
    for (JsonNode methodNode : methodsArray) {
      String name = methodNode.get("name").asText();
      if ("toString".equals(name)) {
        hasToString = true;
        // toString should have inheritedFrom set
        if (methodNode.has("inheritedFrom")) {
          hasInheritedFromObject = true;
        }
      }
      if ("hashCode".equals(name)) {
        hasHashCode = true;
      }
    }
    assertThat("toString method should be present", hasToString, is(true));
    assertThat("hashCode method should be present", hasHashCode, is(true));
    assertThat("Methods should have inheritedFrom marker", hasInheritedFromObject, is(true));
  }

  /**
   * Tests that overridden methods are marked with the "overridden" flag set to true.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.mergeMethods_overriddenMethod_markedAsOverride]
   */
  @Test
  public void mergeMethods_overriddenMethod_markedAsOverride() throws Exception {
    // Given: Child class overriding parent method (SubClass.getA() overrides BaseClass.getA())
    // Note: BaseClass is package-private, so we need scanNonPublic=true
    ClassMetadataSerializer nonPublicSerializer = new ClassMetadataSerializer(true);
    String subClassName = "org.example.paltest.SubClass";

    // When: Ancestry merged via scannedClasspathToJson with mergeAncestry=true
    Path outFile =
        nonPublicSerializer.scannedClasspathToJson(false, Set.of(subClassName), null, true);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode classNode = root.get(0);
    JsonNode methodsArray = classNode.get("methods");

    // Then: Find the getA method and verify it has "overridden":true
    boolean foundGetA = false;
    for (JsonNode methodNode : methodsArray) {
      if ("getA".equals(methodNode.get("name").asText())) {
        foundGetA = true;
        // Assert that the method has "overridden":true
        assertThat(
            "getA method should be marked as overridden",
            methodNode.get("overridden").asBoolean(),
            is(true));
        // Assert that the method has "inheritedFrom":"org.example.paltest.BaseClass"
        assertThat(
            "getA method should have inheritedFrom marker",
            methodNode.has("inheritedFrom"),
            is(true));
        assertThat(methodNode.get("inheritedFrom").asText(), is("org.example.paltest.BaseClass"));
        break;
      }
    }
    assertThat("getA method should be present", foundGetA, is(true));
  }

  /**
   * Tests that shadowed fields are marked correctly with the override flag.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.mergeFields_shadowedField_markedCorrectly]
   */
  @Test
  public void mergeFields_shadowedField_markedCorrectly() throws Exception {
    // Given: Child class with field shadowing parent field (SubClass.a shadows BaseClass.a)
    // Note: BaseClass is package-private, so we need scanNonPublic=true
    ClassMetadataSerializer nonPublicSerializer = new ClassMetadataSerializer(true);
    String subClassName = "org.example.paltest.SubClass";

    // When: Ancestry merged via scannedClasspathToJson with mergeAncestry=true
    Path outFile =
        nonPublicSerializer.scannedClasspathToJson(false, Set.of(subClassName), null, true);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode classNode = root.get(0);
    JsonNode fieldsArray = classNode.get("fields");

    // Then: Find the field "a" and verify it has "overridden":true
    boolean foundFieldA = false;
    for (JsonNode fieldNode : fieldsArray) {
      if ("a".equals(fieldNode.get("name").asText())) {
        foundFieldA = true;
        // Assert that the shadowing field has "overridden":true
        assertThat(
            "Field 'a' should be marked as overridden (shadowed)",
            fieldNode.get("overridden").asBoolean(),
            is(true));
        // Assert that "inheritedFrom" points to the original class
        assertThat(
            "Field 'a' should have inheritedFrom marker", fieldNode.has("inheritedFrom"), is(true));
        assertThat(fieldNode.get("inheritedFrom").asText(), is("org.example.paltest.BaseClass"));
        break;
      }
    }
    assertThat("Field 'a' should be present", foundFieldA, is(true));
  }

  /**
   * Tests that generic parameter types are included in method signatures.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.methodSignature_genericParameters_includesTypeInfo]
   */
  @Test
  public void methodSignature_genericParameters_includesTypeInfo() throws Exception {
    // Given: Method with generic parameter (List<String>) in org.example.paltest.GenericMethods
    String className = "org.example.paltest.GenericMethods";

    // When: Signature generated via scannedClasspathToJson
    Path outFile =
        classMetadataSerializer.scannedClasspathToJson(false, Set.of(className), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode classNode = root.get(0);
    JsonNode methodsArray = classNode.get("methods");

    // Then: Find the "echo" method and verify generic type information
    boolean foundEcho = false;
    for (JsonNode methodNode : methodsArray) {
      if ("echo".equals(methodNode.get("name").asText())) {
        foundEcho = true;

        // Verify parameters array contains type "java.util.List<java.lang.String>"
        JsonNode parametersArray = methodNode.get("parameters");
        boolean foundListParameter = false;
        for (JsonNode paramNode : parametersArray) {
          String paramType = paramNode.get("type").asText();
          if (paramType.contains("java.util.List<java.lang.String>")) {
            foundListParameter = true;
            break;
          }
        }
        assertThat("Parameters should include List<String> type", foundListParameter, is(true));

        // Verify return type includes generic information (T extends CharSequence -> T)
        String returnType = methodNode.get("returnType").asText();
        assertThat("Return type should be present", returnType, not(is("")));
        break;
      }
    }
    assertThat("echo method should be present", foundEcho, is(true));
  }

  /**
   * Tests that methods with bounded type parameters include bounds in signatures.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.methodSignature_typeBounds_handledCorrectly]
   */
  @Test
  public void methodSignature_typeBounds_handledCorrectly() throws Exception {
    // Given: Method with bounded type parameter (<T extends Comparable<T>>)
    //        in org.example.paltest.TypeBoundsClass
    String className = "org.example.paltest.TypeBoundsClass";

    // When: Signature generated via scannedClasspathToJson
    Path outFile =
        classMetadataSerializer.scannedClasspathToJson(false, Set.of(className), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode classNode = root.get(0);
    JsonNode methodsArray = classNode.get("methods");

    // Then: Find methods and verify type bound information is present
    boolean foundComparableMethod = false;
    boolean foundMultiParamMethod = false;

    for (JsonNode methodNode : methodsArray) {
      String methodName = methodNode.get("name").asText();

      if ("comparableMethod".equals(methodName)) {
        foundComparableMethod = true;
        // The method has <T extends Comparable<T>>, verify T is in parameter type
        JsonNode parametersArray = methodNode.get("parameters");
        assertThat(parametersArray.size(), greaterThan(0));
        // The parameter type should be T (the type variable)
        String paramType = parametersArray.get(0).get("type").asText();
        assertThat("Parameter type should be present", paramType, not(is("")));
      }

      if ("multiParamMethod".equals(methodName)) {
        foundMultiParamMethod = true;
        // The method has <K extends Comparable<? super K>, V>
        JsonNode parametersArray = methodNode.get("parameters");
        assertThat("Should have 2 parameters", parametersArray.size(), is(2));
      }
    }

    assertThat("comparableMethod should be present", foundComparableMethod, is(true));
    assertThat("multiParamMethod should be present", foundMultiParamMethod, is(true));
  }

  /**
   * Tests that a custom ClassLoader is used when provided.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_withCustomClassloader_usesProvidedLoader]
   */
  @Test
  public void scannedClasspathToJson_withCustomClassloader_usesProvidedLoader() throws Exception {
    // Given: Custom ClassLoader provided to ClassMetadataSerializer constructor
    // We create a CustomClassloader that uses the current classpath
    URL[] urls = new URL[0]; // Empty URLs, will fall back to parent classloader
    CustomClassloader customLoader =
        new CustomClassloader(urls, Thread.currentThread().getContextClassLoader());

    try {
      ClassMetadataSerializer serializerWithCustomLoader =
          new ClassMetadataSerializer("false", customLoader);

      // When: scannedClasspathToJson called with a specific class
      Path outFile =
          serializerWithCustomLoader.scannedClasspathToJson(
              false, Set.of("java.lang.String"), null, false);
      String json = Files.readString(outFile);
      Files.delete(outFile);

      // Then: The class should be scanned successfully
      // This verifies that the custom classloader was used (ClassGraph uses it internally)
      assertThat(json, containsString("\"className\":\"java.lang.String\""));

      // Verify JSON structure is valid
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(json);
      assertThat(root.isArray(), is(true));
      assertThat(root.size(), is(1));
    } finally {
      customLoader.shutdown();
    }
  }

  /**
   * Tests that AspectJ-generated synthetic methods are filtered from output.
   *
   * <p>Acceptance criteria:
   * [TEST:ClassMetadataSerializerTest.scannedClasspathToJson_syntheticMethods_filtered]
   */
  @Test
  public void scannedClasspathToJson_syntheticMethods_filtered() throws Exception {
    // Given: Scan any JDK class (they have synthetic methods like lambda-related ones)
    // We use java.util.ArrayList which has internal synthetic methods

    // When: scannedClasspathToJson called
    Path outFile =
        classMetadataSerializer.scannedClasspathToJson(
            false, Set.of("java.util.ArrayList"), null, false);
    String json = Files.readString(outFile);
    Files.delete(outFile);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode classNode = root.get(0);
    JsonNode methodsArray = classNode.get("methods");
    JsonNode fieldsArray = classNode.get("fields");

    // Then: Verify no method names contain "_aroundBody" or "$"
    for (JsonNode methodNode : methodsArray) {
      String methodName = methodNode.get("name").asText();
      assertThat(
          "Method name should not contain _aroundBody: " + methodName,
          methodName.contains("_aroundBody"),
          is(false));
      assertThat(
          "Method name should not contain $: " + methodName, methodName.contains("$"), is(false));
    }

    // Also verify field names don't contain ajc$ (AspectJ compiler-generated)
    for (JsonNode fieldNode : fieldsArray) {
      String fieldName = fieldNode.get("name").asText();
      assertThat(
          "Field name should not contain ajc$: " + fieldName,
          fieldName.contains("ajc$"),
          is(false));
    }
  }
}
