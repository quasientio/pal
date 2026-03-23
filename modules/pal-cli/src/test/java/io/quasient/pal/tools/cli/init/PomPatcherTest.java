/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Unit tests for {@link PomPatcher}, which modifies existing Maven {@code pom.xml} files to add PAL
 * weaving support.
 *
 * <p>PomPatcher uses the JDK's built-in {@code javax.xml.parsers.DocumentBuilder} and {@code
 * javax.xml.transform.Transformer} for XML DOM manipulation.
 *
 * @see PomPatcher
 */
public class PomPatcherTest {

  /** Temporary directory for build files. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that patching a minimal valid pom.xml adds the pal-weave dependency with correct
   * coordinates and version.
   */
  @Test
  public void testAddsPalWeaveDependency() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='pal-weave']/groupId", doc),
        is("io.quasient.pal"));
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='pal-weave']/version", doc),
        is("1.0.0"));
  }

  /**
   * Verifies that patching adds the aspectj-maven-plugin with pal-weave in aspectLibraries and
   * complianceLevel=17.
   */
  @Test
  public void testAddsAspectjPlugin() throws Exception {
    // Given
    Path pomFile = writePom(POM_WITH_BUILD_PLUGINS);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    String pluginPath = "/project/build/plugins/plugin[artifactId='aspectj-maven-plugin']";
    NodeList plugins = (NodeList) xpath.evaluate(pluginPath, doc, XPathConstants.NODESET);
    assertThat(plugins.getLength(), is(1));
    assertThat(xpath.evaluate(pluginPath + "/configuration/complianceLevel", doc), is("17"));
    assertThat(
        xpath.evaluate(pluginPath + "/configuration/aspectLibraries/aspectLibrary/artifactId", doc),
        is("pal-weave"));
  }

  /** Verifies that patching creates the dependencies section when missing and adds pal-weave. */
  @Test
  public void testCreatesDependenciesSectionIfMissing() throws Exception {
    // Given
    Path pomFile = writePom(MINIMAL_POM);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList deps =
        (NodeList)
            xpath.evaluate(
                "/project/dependencies/dependency[artifactId='pal-weave']",
                doc,
                XPathConstants.NODESET);
    assertThat(deps.getLength(), is(1));
  }

  /**
   * Verifies that patching creates the build/plugins section when missing and adds the
   * aspectj-maven-plugin.
   */
  @Test
  public void testCreatesPluginsSectionIfMissing() throws Exception {
    // Given
    Path pomFile = writePom(MINIMAL_POM);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList plugins =
        (NodeList)
            xpath.evaluate(
                "/project/build/plugins/plugin[artifactId='aspectj-maven-plugin']",
                doc,
                XPathConstants.NODESET);
    assertThat(plugins.getLength(), is(1));
  }

  /**
   * Verifies idempotency: patching twice produces no duplicate elements and the PatchResult reports
   * already-configured on second pass.
   */
  @Test
  public void testIdempotency() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    InitConfig config = defaultConfig().build();
    PomPatcher patcher = new PomPatcher();

    // When: patch twice
    patcher.patch(config, pomFile);
    String afterFirst = Files.readString(pomFile, StandardCharsets.UTF_8);
    PatchResult secondResult = patcher.patch(config, pomFile);
    String afterSecond = Files.readString(pomFile, StandardCharsets.UTF_8);

    // Then: content identical after second patch
    assertThat(afterSecond, is(afterFirst));
    assertTrue(secondResult.isAlreadyConfigured());
  }

  /** Verifies that patching creates a backup of the original pom.xml with content preserved. */
  @Test
  public void testCreatesBackup() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    String originalContent = Files.readString(pomFile, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Path backupFile = pomFile.resolveSibling("pom.xml.backup");
    assertTrue(Files.exists(backupFile));
    String backupContent = Files.readString(backupFile, StandardCharsets.UTF_8);
    assertThat(backupContent, is(originalContent));
  }

  /**
   * Verifies that patching a malformed XML file throws an IOException with a descriptive message
   * and leaves the original file untouched.
   */
  @Test
  public void testValidatesXmlBeforePatching() throws Exception {
    // Given
    String malformed = "<project><groupId>com.example</groupId>";
    Path pomFile = writePom(malformed);
    String originalContent = Files.readString(pomFile, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().build();

    // When
    try {
      new PomPatcher().patch(config, pomFile);
      fail("Expected IOException for malformed XML");
    } catch (IOException e) {
      // Then
      assertThat(e.getMessage(), containsString("not valid XML"));
    }

    // Original file unchanged
    String afterContent = Files.readString(pomFile, StandardCharsets.UTF_8);
    assertThat(afterContent, is(originalContent));
  }

  /** Verifies that the patched output is valid XML (round-trip validation). */
  @Test
  public void testValidatesXmlAfterPatching() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then: re-parse to verify round-trip validity
    Document doc = parsePom(pomFile);
    assertNotNull(doc);
    assertNotNull(doc.getDocumentElement());
  }

  /**
   * Verifies that when the pom.xml already has an aspectj-maven-plugin without pal-weave in
   * aspectLibraries, patching merges pal-weave into the existing aspectLibraries while preserving
   * other entries.
   */
  @Test
  public void testMergesIntoExistingAspectjPlugin() throws Exception {
    // Given
    Path pomFile = writePom(POM_WITH_EXISTING_ASPECTJ_PLUGIN);
    InitConfig config = defaultConfig().build();

    // When
    PatchResult result = new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList libs =
        (NodeList)
            xpath.evaluate(
                "/project/build/plugins/plugin[artifactId='aspectj-maven-plugin']"
                    + "/configuration/aspectLibraries/aspectLibrary",
                doc,
                XPathConstants.NODESET);
    // Should have 2: existing + pal-weave
    assertThat(libs.getLength(), is(2));
    assertFalse(result.getAdditions().isEmpty());
  }

  /**
   * Verifies that when the pom.xml has an aspectj-maven-plugin with a different version, the
   * PatchResult contains a warning and the existing version is not overwritten.
   */
  @Test
  public void testWarnsOnConflictingAspectjVersion() throws Exception {
    // Given
    Path pomFile = writePom(POM_WITH_CONFLICTING_PLUGIN_VERSION);
    InitConfig config = defaultConfig().build();

    // When
    PatchResult result = new PomPatcher().patch(config, pomFile);

    // Then
    assertFalse(result.getWarnings().isEmpty());
    assertTrue(
        result.getWarnings().stream().anyMatch(w -> w.contains("1.14.0") && w.contains("1.15.0")));

    // Existing version not overwritten
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(
        xpath.evaluate(
            "/project/build/plugins/plugin[artifactId='aspectj-maven-plugin']/version", doc),
        is("1.14.0"));
  }

  /** Verifies that patching adds pal.version and aspectj.version properties when missing. */
  @Test
  public void testAddsVersionProperties() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    InitConfig config = defaultConfig().palVersion("1.0.0").aspectjVersion("1.9.24").build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(xpath.evaluate("/project/properties/pal.version", doc), is("1.0.0"));
    assertThat(xpath.evaluate("/project/properties/aspectj.version", doc), is("1.9.24"));
  }

  /**
   * Verifies that patching preserves all existing content including custom dependencies, profiles,
   * and repositories.
   */
  @Test
  public void testPreservesExistingContent() throws Exception {
    // Given
    Path pomFile = writePom(POM_WITH_EXISTING_CONTENT);
    InitConfig config = defaultConfig().build();

    // When
    new PomPatcher().patch(config, pomFile);

    // Then
    Document doc = parsePom(pomFile);
    XPath xpath = XPathFactory.newInstance().newXPath();
    // Existing dependency preserved
    NodeList guavaDeps =
        (NodeList)
            xpath.evaluate(
                "/project/dependencies/dependency[artifactId='guava']",
                doc,
                XPathConstants.NODESET);
    assertThat(guavaDeps.getLength(), is(1));
    // Existing profile preserved
    NodeList profiles =
        (NodeList) xpath.evaluate("/project/profiles/profile", doc, XPathConstants.NODESET);
    assertThat(profiles.getLength(), is(1));
    // Existing repository preserved
    NodeList repos =
        (NodeList) xpath.evaluate("/project/repositories/repository", doc, XPathConstants.NODESET);
    assertThat(repos.getLength(), is(1));
  }

  /** Verifies that PatchResult accurately reports additions and skips across two scenarios. */
  @Test
  public void testPatchResultReportsActions() throws Exception {
    // Given: unpatched pom
    Path pomFile = writePom(BASIC_POM);
    InitConfig config = defaultConfig().build();
    PomPatcher patcher = new PomPatcher();

    // When: first patch
    PatchResult firstResult = patcher.patch(config, pomFile);

    // Then: additions reported
    assertFalse(firstResult.getAdditions().isEmpty());

    // When: second patch (already configured)
    PatchResult secondResult = patcher.patch(config, pomFile);

    // Then: reports already configured
    assertTrue(secondResult.isAlreadyConfigured());
  }

  /** Verifies dry-run mode: file not modified, no backup, PatchResult still reports actions. */
  @Test
  public void testDryRunDoesNotModifyFile() throws Exception {
    // Given
    Path pomFile = writePom(BASIC_POM);
    String originalContent = Files.readString(pomFile, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().dryRun(true).build();

    // When
    PatchResult result = new PomPatcher().patch(config, pomFile);

    // Then: file unchanged
    String afterContent = Files.readString(pomFile, StandardCharsets.UTF_8);
    assertThat(afterContent, is(originalContent));

    // No backup created
    assertFalse(Files.exists(pomFile.resolveSibling("pom.xml.backup")));

    // PatchResult still reports what would have been done
    assertFalse(result.getAdditions().isEmpty());

    // PatchResult reports dry-run status
    assertTrue(result.isDryRun());
  }

  /**
   * Writes a pom.xml file in the temporary directory.
   *
   * @param content the file content
   * @return the path to the created file
   * @throws Exception if the file cannot be written
   */
  private Path writePom(String content) throws Exception {
    Path pomFile = tempDir.getRoot().toPath().resolve("pom.xml");
    Files.writeString(pomFile, content, StandardCharsets.UTF_8);
    return pomFile;
  }

  /**
   * Parses a pom.xml file into a DOM document.
   *
   * @param pomFile the path to the pom.xml
   * @return the parsed document
   * @throws Exception if parsing fails
   */
  private static Document parsePom(Path pomFile) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(Files.newInputStream(pomFile));
  }

  /**
   * Creates a default config builder for tests.
   *
   * @return a builder with standard test values
   */
  private static InitConfig.Builder defaultConfig() {
    return InitConfig.builder()
        .groupId("com.example")
        .artifactId("my-app")
        .palVersion("1.0.0")
        .aspectjVersion("1.9.24")
        .buildTool(BuildTool.MAVEN);
  }

  /** A basic pom.xml with dependencies section. */
  private static final String BASIC_POM =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.13.2</version>
              </dependency>
          </dependencies>
      </project>
      """;

  /** A minimal pom.xml with no dependencies or build sections. */
  private static final String MINIMAL_POM =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
      </project>
      """;

  /** A pom.xml with build/plugins section but no aspectj plugin. */
  private static final String POM_WITH_BUILD_PLUGINS =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.13.2</version>
              </dependency>
          </dependencies>
          <build>
              <plugins>
                  <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-compiler-plugin</artifactId>
                      <version>3.11.0</version>
                  </plugin>
              </plugins>
          </build>
      </project>
      """;

  /** A pom.xml with an existing aspectj-maven-plugin but no pal-weave in aspectLibraries. */
  private static final String POM_WITH_EXISTING_ASPECTJ_PLUGIN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>io.quasient.pal</groupId>
                  <artifactId>pal-weave</artifactId>
                  <version>1.0.0</version>
              </dependency>
          </dependencies>
          <build>
              <plugins>
                  <plugin>
                      <groupId>dev.aspectj</groupId>
                      <artifactId>aspectj-maven-plugin</artifactId>
                      <version>1.15.0</version>
                      <configuration>
                          <complianceLevel>17</complianceLevel>
                          <aspectLibraries>
                              <aspectLibrary>
                                  <groupId>com.other</groupId>
                                  <artifactId>other-aspect</artifactId>
                              </aspectLibrary>
                          </aspectLibraries>
                      </configuration>
                  </plugin>
              </plugins>
          </build>
      </project>
      """;

  /** A pom.xml with aspectj-maven-plugin at a conflicting version. */
  private static final String POM_WITH_CONFLICTING_PLUGIN_VERSION =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>io.quasient.pal</groupId>
                  <artifactId>pal-weave</artifactId>
                  <version>1.0.0</version>
              </dependency>
          </dependencies>
          <build>
              <plugins>
                  <plugin>
                      <groupId>dev.aspectj</groupId>
                      <artifactId>aspectj-maven-plugin</artifactId>
                      <version>1.14.0</version>
                      <configuration>
                          <complianceLevel>17</complianceLevel>
                          <aspectLibraries>
                              <aspectLibrary>
                                  <groupId>com.other</groupId>
                                  <artifactId>other-aspect</artifactId>
                              </aspectLibrary>
                          </aspectLibraries>
                      </configuration>
                  </plugin>
              </plugins>
          </build>
      </project>
      """;

  /** A pom.xml with custom dependencies, profiles, and repositories. */
  private static final String POM_WITH_EXISTING_CONTENT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>my-app</artifactId>
          <version>1.0-SNAPSHOT</version>
          <dependencies>
              <dependency>
                  <groupId>com.google.guava</groupId>
                  <artifactId>guava</artifactId>
                  <version>33.0.0-jre</version>
              </dependency>
          </dependencies>
          <repositories>
              <repository>
                  <id>central</id>
                  <url>https://repo.maven.apache.org/maven2</url>
              </repository>
          </repositories>
          <profiles>
              <profile>
                  <id>release</id>
              </profile>
          </profiles>
      </project>
      """;
}
