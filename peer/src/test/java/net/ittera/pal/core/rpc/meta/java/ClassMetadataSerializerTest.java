package net.ittera.pal.core.rpc.meta.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    String classesMetadata =
        classMetadataSerializer.scannedClasspathToJson(false, null, null, false);
    String searchString = "className";
    // expect 10000 classes at least
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, classesMetadata) > minExpectedClassCount);

    // expect at least 300 java.util classes
    String javaUtilClassNameEntry = "\"className\":\"java.util.";
    assertTrue(findOccurrences(javaUtilClassNameEntry, classesMetadata) > 300);
  }

  @Test
  public void testScanWithMergedAncestry() throws Exception {
    String classesMetadata =
        classMetadataSerializer.scannedClasspathToJson(false, null, null, true);
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

    String scannedClasses =
        classMetadataSerializer.scannedClasspathToJson(
            false, null, additionalExcludePrefixes, false);

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

    String scannedClasses =
        classMetadataSerializer.scannedClasspathToJson(false, includeClasses, null, false);

    String searchString = "className";
    assertEquals(2, findOccurrences(searchString, scannedClasses));
  }

  @Test
  public void testScanWithIncludeClassesAndMergedAncestry() throws Exception {
    Set<String> includeClasses = new HashSet<>();
    includeClasses.add("java.util.ArrayList");
    String scannedClasses =
        classMetadataSerializer.scannedClasspathToJson(false, includeClasses, null, true);

    String searchString = "className";
    assertEquals(1, findOccurrences(searchString, scannedClasses));

    // verify that some inherited methods are included

    // inherited from Object
    assertEquals(3, findOccurrences("\"wait\"", scannedClasses));
    // inherited from AbstractList
    assertEquals(1, findOccurrences("\"set\"", scannedClasses));
    // inherited from List
    assertTrue(findOccurrences("\"of\"", scannedClasses) > 10);
  }
}
