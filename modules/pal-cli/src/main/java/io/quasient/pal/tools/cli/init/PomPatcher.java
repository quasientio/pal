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
package io.quasient.pal.tools.cli.init;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Patches an existing Maven {@code pom.xml} to add PAL AspectJ weaving configuration.
 *
 * <p>Uses the JDK's built-in {@code javax.xml.parsers.DocumentBuilder} and {@code
 * javax.xml.transform.Transformer} for XML DOM manipulation. Key behaviors:
 *
 * <ul>
 *   <li>Validates XML before patching (parse test); rejects malformed input with descriptive error
 *   <li>Creates {@code pom.xml.backup} before any modification (skipped in dry-run mode)
 *   <li>Adds {@code pal-weave} dependency if not present (checks groupId+artifactId)
 *   <li>Adds {@code aspectj-maven-plugin} if not present; if present without {@code pal-weave},
 *       merges {@code pal-weave} into existing {@code aspectLibraries}
 *   <li>Adds {@code pal.version} and {@code aspectj.version} properties if missing
 *   <li>Creates {@code <dependencies>}, {@code <build>}, {@code <plugins>} sections if missing
 *   <li>Validates XML after patching (re-parse output before writing)
 *   <li>Returns {@link PatchResult} listing what was added/skipped/warned
 *   <li>Uses {@code OutputKeys.INDENT="no"} to minimize formatting changes
 *   <li>In dry-run mode: performs all analysis and returns PatchResult but does NOT write files
 * </ul>
 *
 * @since 1.0.0
 */
public final class PomPatcher {

  /** PAL weave groupId. */
  private static final String PAL_GROUP_ID = "io.quasient.pal";

  /** PAL weave artifactId. */
  private static final String PAL_WEAVE_ARTIFACT = "pal-weave";

  /** AspectJ Maven plugin groupId. */
  private static final String ASPECTJ_PLUGIN_GROUP_ID = "org.codehaus.mojo";

  /** AspectJ Maven plugin artifactId. */
  private static final String ASPECTJ_PLUGIN_ARTIFACT = "aspectj-maven-plugin";

  /** Expected AspectJ Maven plugin version from PAL's build. */
  private static final String EXPECTED_PLUGIN_VERSION = "1.15.0";

  /**
   * Patches an existing {@code pom.xml} to add PAL weaving configuration.
   *
   * @param config the init configuration
   * @param pomPath the path to the existing pom.xml
   * @return a result describing what was added, skipped, or warned about
   * @throws IOException if an I/O error occurs or the XML is malformed
   */
  public PatchResult patch(InitConfig config, Path pomPath) throws IOException {
    String originalContent = Files.readString(pomPath, StandardCharsets.UTF_8);
    PatchResult.Builder result = PatchResult.builder().dryRun(config.isDryRun());

    Document doc = parseXml(originalContent, "Input pom.xml is not valid XML");

    Element project = doc.getDocumentElement();

    patchProperties(doc, project, config, result);
    patchDependencies(doc, project, config, result);
    patchPlugins(doc, project, result);

    String patchedContent = serializeXml(doc);

    parseXml(patchedContent, "Patched pom.xml produced invalid XML");

    if (!config.isDryRun()) {
      Path backupPath = pomPath.resolveSibling("pom.xml.backup");
      Files.copy(pomPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(pomPath, patchedContent, StandardCharsets.UTF_8);
    }

    return result.build();
  }

  /**
   * Adds {@code pal.version} and {@code aspectj.version} properties if they are not already
   * present.
   *
   * @param doc the XML document
   * @param project the project root element
   * @param config the init configuration
   * @param result the result builder
   */
  private void patchProperties(
      Document doc, Element project, InitConfig config, PatchResult.Builder result) {
    Element properties = getOrCreateChild(doc, project, "properties");

    if (getChildByTagName(properties, "pal.version") == null) {
      appendChildElement(doc, properties, "pal.version", config.getPalVersion());
      result.addition("Added pal.version property");
    } else {
      result.skip("pal.version property already present");
    }

    if (getChildByTagName(properties, "aspectj.version") == null) {
      appendChildElement(doc, properties, "aspectj.version", config.getAspectjVersion());
      result.addition("Added aspectj.version property");
    } else {
      result.skip("aspectj.version property already present");
    }
  }

  /**
   * Adds the {@code pal-weave} dependency if not already present.
   *
   * @param doc the XML document
   * @param project the project root element
   * @param config the init configuration
   * @param result the result builder
   */
  private void patchDependencies(
      Document doc, Element project, InitConfig config, PatchResult.Builder result) {
    Element dependencies = getOrCreateChild(doc, project, "dependencies");

    if (findDependency(dependencies, PAL_GROUP_ID, PAL_WEAVE_ARTIFACT) != null) {
      result.skip("pal-weave dependency already present");
      return;
    }

    Element dep = doc.createElement("dependency");
    appendChildElement(doc, dep, "groupId", PAL_GROUP_ID);
    appendChildElement(doc, dep, "artifactId", PAL_WEAVE_ARTIFACT);
    appendChildElement(doc, dep, "version", config.getPalVersion());
    dependencies.appendChild(dep);
    result.addition("Added pal-weave dependency");
  }

  /**
   * Adds the {@code aspectj-maven-plugin} if not present, or merges {@code pal-weave} into an
   * existing plugin's aspect libraries.
   *
   * @param doc the XML document
   * @param project the project root element
   * @param result the result builder
   */
  private void patchPlugins(Document doc, Element project, PatchResult.Builder result) {
    Element build = getOrCreateChild(doc, project, "build");
    Element plugins = getOrCreateChild(doc, build, "plugins");

    Element existingPlugin = findPlugin(plugins, ASPECTJ_PLUGIN_GROUP_ID, ASPECTJ_PLUGIN_ARTIFACT);

    if (existingPlugin == null) {
      Element plugin = doc.createElement("plugin");
      appendChildElement(doc, plugin, "groupId", ASPECTJ_PLUGIN_GROUP_ID);
      appendChildElement(doc, plugin, "artifactId", ASPECTJ_PLUGIN_ARTIFACT);
      appendChildElement(doc, plugin, "version", EXPECTED_PLUGIN_VERSION);

      Element configuration = doc.createElement("configuration");
      appendChildElement(doc, configuration, "complianceLevel", "17");

      Element aspectLibraries = doc.createElement("aspectLibraries");
      Element aspectLibrary = doc.createElement("aspectLibrary");
      appendChildElement(doc, aspectLibrary, "groupId", PAL_GROUP_ID);
      appendChildElement(doc, aspectLibrary, "artifactId", PAL_WEAVE_ARTIFACT);
      aspectLibraries.appendChild(aspectLibrary);
      configuration.appendChild(aspectLibraries);

      plugin.appendChild(configuration);

      Element executions = doc.createElement("executions");
      Element execution = doc.createElement("execution");
      Element goals = doc.createElement("goals");
      appendChildElement(doc, goals, "goal", "compile");
      execution.appendChild(goals);
      executions.appendChild(execution);
      plugin.appendChild(executions);

      plugins.appendChild(plugin);
      result.addition("Added aspectj-maven-plugin with pal-weave aspect library");
      return;
    }

    String existingVersion = getChildTextContent(existingPlugin, "version");
    if (existingVersion != null && !existingVersion.equals(EXPECTED_PLUGIN_VERSION)) {
      result.warning(
          "aspectj-maven-plugin version "
              + existingVersion
              + " differs from expected "
              + EXPECTED_PLUGIN_VERSION);
    }

    Element configuration = getChildByTagName(existingPlugin, "configuration");
    if (configuration == null) {
      configuration = doc.createElement("configuration");
      existingPlugin.appendChild(configuration);
    }

    Element aspectLibraries = getChildByTagName(configuration, "aspectLibraries");
    if (aspectLibraries == null) {
      aspectLibraries = doc.createElement("aspectLibraries");
      configuration.appendChild(aspectLibraries);
    }

    if (findAspectLibrary(aspectLibraries, PAL_GROUP_ID, PAL_WEAVE_ARTIFACT)) {
      result.skip("pal-weave already in aspectj-maven-plugin aspectLibraries");
      return;
    }

    Element aspectLibrary = doc.createElement("aspectLibrary");
    appendChildElement(doc, aspectLibrary, "groupId", PAL_GROUP_ID);
    appendChildElement(doc, aspectLibrary, "artifactId", PAL_WEAVE_ARTIFACT);
    aspectLibraries.appendChild(aspectLibrary);
    result.addition("Added pal-weave to existing aspectj-maven-plugin aspectLibraries");
  }

  /**
   * Parses an XML string into a DOM document.
   *
   * @param xml the XML string
   * @param errorMessage the error message to use if parsing fails
   * @return the parsed document
   * @throws IOException if the XML is malformed
   */
  private static Document parseXml(String xml, String errorMessage) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (SAXException e) {
      throw new IOException(errorMessage + ": " + e.getMessage(), e);
    } catch (ParserConfigurationException e) {
      throw new IOException("XML parser configuration error", e);
    }
  }

  /**
   * Serializes a DOM document to an XML string with minimal formatting changes.
   *
   * @param doc the document to serialize
   * @return the XML string
   * @throws IOException if serialization fails
   */
  private static String serializeXml(Document doc) throws IOException {
    try {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(writer));
      return writer.toString();
    } catch (TransformerException e) {
      throw new IOException("Failed to serialize XML: " + e.getMessage(), e);
    }
  }

  /**
   * Gets or creates a direct child element with the given tag name.
   *
   * @param doc the document
   * @param parent the parent element
   * @param tagName the child element tag name
   * @return the existing or newly created child element
   */
  private static Element getOrCreateChild(Document doc, Element parent, String tagName) {
    Element child = getChildByTagName(parent, tagName);
    if (child == null) {
      child = doc.createElement(tagName);
      parent.appendChild(child);
    }
    return child;
  }

  /**
   * Gets the first direct child element with the given tag name.
   *
   * @param parent the parent element
   * @param tagName the tag name to find
   * @return the child element, or null if not found
   */
  private static Element getChildByTagName(Element parent, String tagName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element element && child.getNodeName().equals(tagName)) {
        return element;
      }
    }
    return null;
  }

  /**
   * Gets the text content of a direct child element.
   *
   * @param parent the parent element
   * @param tagName the child tag name
   * @return the text content, or null if the child is not found
   */
  private static String getChildTextContent(Element parent, String tagName) {
    Element child = getChildByTagName(parent, tagName);
    return child != null ? child.getTextContent() : null;
  }

  /**
   * Appends a child element with the given tag name and text content.
   *
   * @param doc the document
   * @param parent the parent element
   * @param tagName the child element tag name
   * @param textContent the text content
   */
  private static void appendChildElement(
      Document doc, Element parent, String tagName, String textContent) {
    Element child = doc.createElement(tagName);
    child.setTextContent(textContent);
    parent.appendChild(child);
  }

  /**
   * Finds a dependency element by groupId and artifactId.
   *
   * @param dependencies the dependencies element
   * @param groupId the groupId to match
   * @param artifactId the artifactId to match
   * @return the matching dependency element, or null if not found
   */
  private static Element findDependency(Element dependencies, String groupId, String artifactId) {
    NodeList deps = dependencies.getElementsByTagName("dependency");
    for (int i = 0; i < deps.getLength(); i++) {
      Element dep = (Element) deps.item(i);
      String gId = getChildTextContent(dep, "groupId");
      String aId = getChildTextContent(dep, "artifactId");
      if (groupId.equals(gId) && artifactId.equals(aId)) {
        return dep;
      }
    }
    return null;
  }

  /**
   * Finds a plugin element by groupId and artifactId.
   *
   * @param plugins the plugins element
   * @param groupId the groupId to match
   * @param artifactId the artifactId to match
   * @return the matching plugin element, or null if not found
   */
  private static Element findPlugin(Element plugins, String groupId, String artifactId) {
    NodeList pluginNodes = plugins.getElementsByTagName("plugin");
    for (int i = 0; i < pluginNodes.getLength(); i++) {
      Element plugin = (Element) pluginNodes.item(i);
      String gId = getChildTextContent(plugin, "groupId");
      String aId = getChildTextContent(plugin, "artifactId");
      if (groupId.equals(gId) && artifactId.equals(aId)) {
        return plugin;
      }
    }
    return null;
  }

  /**
   * Checks whether an aspectLibraries element contains a matching aspect library.
   *
   * @param aspectLibraries the aspectLibraries element
   * @param groupId the groupId to match
   * @param artifactId the artifactId to match
   * @return true if a matching aspect library is found
   */
  private static boolean findAspectLibrary(
      Element aspectLibraries, String groupId, String artifactId) {
    NodeList libs = aspectLibraries.getElementsByTagName("aspectLibrary");
    for (int i = 0; i < libs.getLength(); i++) {
      Element lib = (Element) libs.item(i);
      String gId = getChildTextContent(lib, "groupId");
      String aId = getChildTextContent(lib, "artifactId");
      if (groupId.equals(gId) && artifactId.equals(aId)) {
        return true;
      }
    }
    return false;
  }
}
