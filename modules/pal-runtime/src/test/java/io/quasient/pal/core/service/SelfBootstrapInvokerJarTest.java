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
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Unit tests for {@link SelfBootstrapInvoker#callJar(String, java.util.List)} method.
 *
 * <p>Tests verify JAR file handling including missing files, missing manifests, and missing
 * Main-Class attributes.
 */
public class SelfBootstrapInvokerJarTest {

  private ZContext ctx;
  private UUID peerId;
  private IncomingMessageDispatcher dispatcher;
  private MessageBuilder messageBuilder;
  private CustomClassloader classloader;
  private SelfBootstrapInvoker invoker;
  private Path tempDir;

  @Before
  public void setup() throws IOException {
    ctx = new ZContext(1);
    peerId = UUID.randomUUID();
    dispatcher = mock(IncomingMessageDispatcher.class);
    messageBuilder = new MessageBuilder(peerId);
    classloader = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());

    invoker =
        new SelfBootstrapInvoker(
            peerId,
            dispatcher,
            messageBuilder,
            classloader,
            ctx,
            "inproc://offs",
            Collections.emptySet());

    tempDir = Files.createTempDirectory("selfbootstrap-test");
  }

  @After
  public void cleanup() throws IOException {
    ctx.close();
    // Clean up temp directory
    if (tempDir != null) {
      try (var walk = Files.walk(tempDir)) {
        walk.sorted((a, b) -> b.compareTo(a)) // reverse order to delete files before dirs
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    // ignore
                  }
                });
      }
    }
  }

  @Test
  public void callJar_nonExistentFile_throwsPeerException() {
    String nonExistentJar = "/path/to/nonexistent/file.jar";

    try {
      invoker.callJar(nonExistentJar, Collections.emptyList());
      fail("Expected PeerException for non-existent JAR");
    } catch (PeerException e) {
      assertThat(
          e.getFatalCode(), is(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST));
    }
  }

  @Test
  public void callJar_invalidJarFile_throwsPeerException() throws IOException {
    // Create a file that isn't a valid JAR
    Path invalidJar = tempDir.resolve("invalid.jar");
    Files.writeString(invalidJar, "this is not a jar file");

    try {
      invoker.callJar(invalidJar.toString(), Collections.emptyList());
      fail("Expected PeerException for invalid JAR");
    } catch (PeerException e) {
      assertThat(
          e.getFatalCode(), is(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST));
    }
  }

  @Test
  public void callJar_jarWithoutMainClass_throwsPeerException() throws IOException {
    // Create a valid JAR without Main-Class attribute
    Path jarWithoutMain = tempDir.resolve("no-main.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    // No Main-Class attribute

    try (FileOutputStream fos = new FileOutputStream(jarWithoutMain.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // Empty JAR with just manifest
    }

    try {
      invoker.callJar(jarWithoutMain.toString(), Collections.emptyList());
      fail("Expected PeerException for JAR without Main-Class");
    } catch (PeerException e) {
      assertThat(e.getFatalCode(), is(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST));
    }
  }

  @Test
  public void callJar_directoryInsteadOfFile_throwsPeerException() {
    // Try to open a directory as a JAR
    try {
      invoker.callJar(tempDir.toString(), Collections.emptyList());
      fail("Expected PeerException for directory");
    } catch (PeerException e) {
      assertThat(
          e.getFatalCode(), is(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST));
    }
  }

  @Test
  public void callJar_emptyFile_throwsPeerException() throws IOException {
    // Create an empty file
    Path emptyJar = tempDir.resolve("empty.jar");
    Files.createFile(emptyJar);

    try {
      invoker.callJar(emptyJar.toString(), Collections.emptyList());
      fail("Expected PeerException for empty file");
    } catch (PeerException e) {
      assertThat(
          e.getFatalCode(), is(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST));
    }
  }

  @Test
  public void callJar_withNullArgList_handlesGracefully() throws IOException {
    // Create a valid JAR without Main-Class to test null argList handling
    // (it will fail at Main-Class check, but null argList should be handled before that)
    Path jarWithoutMain = tempDir.resolve("no-main-null-args.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (FileOutputStream fos = new FileOutputStream(jarWithoutMain.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // Empty JAR with just manifest
    }

    try {
      invoker.callJar(jarWithoutMain.toString(), null);
      fail("Expected PeerException for JAR without Main-Class");
    } catch (PeerException e) {
      // Should reach the Main-Class check, meaning null argList was handled
      assertThat(e.getFatalCode(), is(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST));
    }
  }
}
