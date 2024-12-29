package net.ittera.pal.core.rpc.meta.java;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClassMetadataSerializerTest {

  private ClassMetadataSerializer classMetadataSerializer;
  private Path outputFile;

  @Before
  public void setUp() throws Exception {
    outputFile = Paths.get("classes.json");
    boolean scanNonPublic = false;
    classMetadataSerializer = new ClassMetadataSerializer(scanNonPublic);
  }

  @After
  public void tearDown() throws IOException {
    if (Files.exists(outputFile)) {
      Files.delete(outputFile);
    }
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

  private int findOccurrences(String searchString) throws IOException {
    String fileContent = Files.readString(outputFile);
    return findOccurrences(searchString, fileContent);
  }

  @Test
  public void testScanAndWriteToFile() throws IOException {
    String scannedText = classMetadataSerializer.scannedClasspathToJson(false, null);
    BufferedWriter writer = Files.newBufferedWriter(outputFile, UTF_8);
    writer.write(scannedText);
    writer.close();

    String searchString = "className";
    // expect 10000 classes at least
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString) > minExpectedClassCount);

    // expect at least 300 java.util classes
    String javaUtilClassNameEntry = "\"className\" : \"java.util.";
    assertTrue(findOccurrences(javaUtilClassNameEntry) > 300);
  }

  @Test
  public void testScanWithPrefixExcludes() throws IOException {
    Set<String> additionalExcludePrefixes = new HashSet<>();
    additionalExcludePrefixes.add("java.util");

    String scannedClasses =
        classMetadataSerializer.scannedClasspathToJson(false, additionalExcludePrefixes);

    String searchString = "className";
    // expect 10000 classes at least
    int minExpectedClassCount = 10000;
    assertTrue(findOccurrences(searchString, scannedClasses) > minExpectedClassCount);

    // expect no java.util class
    String javaUtilClassNameEntry = "\"className\" : \"java.util.";
    assertEquals(0, findOccurrences(javaUtilClassNameEntry, scannedClasses));
  }
}
