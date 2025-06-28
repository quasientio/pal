/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.meta.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

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
}
