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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
 * Unit tests for {@link PomGenerator}, which generates new Maven {@code pom.xml} files for fresh
 * PAL projects.
 *
 * <p>PomGenerator creates the entire {@code pom.xml} from a template with variable substitution,
 * including project coordinates (GAV), the {@code pal-weave} dependency, the {@code
 * aspectj-maven-plugin} with {@code pal-weave} in {@code aspectLibraries}, the {@code aspectjrt}
 * runtime dependency, Java 17 compiler properties, and PAL/AspectJ version properties. These tests
 * validate the critical AspectJ weaving configuration that is the top onboarding pain point.
 *
 * @see PomGenerator
 */
public class PomGeneratorTest {

  /** Temporary directory for generated output. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that the generated {@code pom.xml} is well-formed XML that can be parsed by a {@code
   * DocumentBuilder} without errors.
   */
  @Test
  public void testGeneratesValidXml() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    assertNotNull(doc);
    assertNotNull(doc.getDocumentElement());
  }

  /**
   * Verifies that the generated {@code pom.xml} contains the correct {@code <groupId>}, {@code
   * <artifactId>}, and {@code <version>} elements matching the values from {@code InitConfig}.
   */
  @Test
  public void testProjectCoordinates() throws Exception {
    // Given
    InitConfig config =
        defaultConfig()
            .groupId("com.example")
            .artifactId("my-app")
            .projectVersion("1.0-SNAPSHOT")
            .build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(xpath.evaluate("/project/groupId", doc), is("com.example"));
    assertThat(xpath.evaluate("/project/artifactId", doc), is("my-app"));
    assertThat(xpath.evaluate("/project/version", doc), is("1.0-SNAPSHOT"));
  }

  /**
   * Verifies that the generated {@code pom.xml} includes the {@code pal-weave} dependency with
   * correct coordinates and version matching the configured palVersion.
   */
  @Test
  public void testPalWeaveDependencyPresent() throws Exception {
    // Given
    InitConfig config = defaultConfig().palVersion("1.0.0").build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList deps =
        (NodeList)
            xpath.evaluate(
                "/project/dependencies/dependency[artifactId='pal-weave']",
                doc,
                XPathConstants.NODESET);
    assertThat(deps.getLength(), is(1));
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='pal-weave']/groupId", doc),
        is("io.quasient.pal"));
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='pal-weave']/version", doc),
        is("1.0.0"));
  }

  /**
   * Verifies that the generated {@code pom.xml} includes the {@code aspectj-maven-plugin} with
   * correct version, complianceLevel=17, and pal-weave in aspectLibraries.
   */
  @Test
  public void testAspectjPluginConfigured() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    String pluginPath = "/project/build/plugins/plugin[artifactId='aspectj-maven-plugin']";
    NodeList plugins = (NodeList) xpath.evaluate(pluginPath, doc, XPathConstants.NODESET);
    assertThat(plugins.getLength(), is(1));
    assertThat(xpath.evaluate(pluginPath + "/configuration/complianceLevel", doc), is("17"));
    assertThat(
        xpath.evaluate(pluginPath + "/configuration/aspectLibraries/aspectLibrary/artifactId", doc),
        is("pal-weave"));
    assertThat(
        xpath.evaluate(pluginPath + "/configuration/aspectLibraries/aspectLibrary/groupId", doc),
        is("io.quasient.pal"));
  }

  /**
   * Verifies that the generated {@code pom.xml} includes Java 17 compiler properties and UTF-8
   * encoding.
   */
  @Test
  public void testJavaVersionProperties() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(xpath.evaluate("/project/properties/maven.compiler.source", doc), is("17"));
    assertThat(xpath.evaluate("/project/properties/maven.compiler.target", doc), is("17"));
    assertThat(
        xpath.evaluate("/project/properties/project.build.sourceEncoding", doc), is("UTF-8"));
  }

  /** Verifies that the generated {@code pom.xml} includes PAL and AspectJ version properties. */
  @Test
  public void testPalVersionProperties() throws Exception {
    // Given
    InitConfig config = defaultConfig().palVersion("1.0.0").aspectjVersion("1.9.24").build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    assertThat(xpath.evaluate("/project/properties/pal.version", doc), is("1.0.0"));
    assertThat(xpath.evaluate("/project/properties/aspectj.version", doc), is("1.9.24"));
  }

  /** Verifies that the generated {@code pom.xml} includes the aspectjrt runtime dependency. */
  @Test
  public void testAspectjRuntimeDependency() throws Exception {
    // Given
    InitConfig config = defaultConfig().aspectjVersion("1.9.24").build();

    // When
    new PomGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Document doc = parsePom();
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList deps =
        (NodeList)
            xpath.evaluate(
                "/project/dependencies/dependency[artifactId='aspectjrt']",
                doc,
                XPathConstants.NODESET);
    assertThat(deps.getLength(), is(1));
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='aspectjrt']/groupId", doc),
        is("org.aspectj"));
    assertThat(
        xpath.evaluate("/project/dependencies/dependency[artifactId='aspectjrt']/version", doc),
        is("1.9.24"));
  }

  /** Verifies that the generated {@code pom.xml} is written to the correct location. */
  @Test
  public void testOutputFileLocation() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();
    Path target = tempDir.getRoot().toPath();

    // When
    new PomGenerator(config).generate(target);

    // Then
    assertTrue(Files.exists(target.resolve("pom.xml")));
  }

  /** Verifies that when {@code dryRun=true}, no pom.xml is written to disk. */
  @Test
  public void testDryRunDoesNotWriteFile() throws Exception {
    // Given
    InitConfig config = defaultConfig().dryRun(true).build();
    Path target = tempDir.getRoot().toPath();

    // When
    new PomGenerator(config).generate(target);

    // Then
    assertFalse(Files.exists(target.resolve("pom.xml")));
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

  /**
   * Parses the generated pom.xml from the temp directory.
   *
   * @return the parsed XML document
   * @throws Exception if parsing fails
   */
  private Document parsePom() throws Exception {
    Path pomPath = tempDir.getRoot().toPath().resolve("pom.xml");
    assertTrue("pom.xml should exist", Files.exists(pomPath));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(Files.newInputStream(pomPath));
  }
}
