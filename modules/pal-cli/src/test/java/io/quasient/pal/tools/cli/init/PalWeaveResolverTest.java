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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@code PalWeaveResolver}, which checks the local Maven repository for pal-weave
 * and fetches it from Maven Central if missing.
 *
 * <p>These tests verify local repo detection, URL construction, fetch behavior, graceful failure
 * handling, and dry-run mode.
 */
public class PalWeaveResolverTest {

  /** Temporary folder for simulating a local Maven repository. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that {@code isAvailableLocally} returns {@code true} when the pal-weave JAR exists in
   * the local Maven repository at the expected path.
   *
   * <p>Uses a temporary directory as a mock repository root with the correct directory structure
   * ({@code io/quasient/pal/pal-weave/1.0.0/pal-weave-1.0.0.jar}) pre-created.
   */
  @Test
  public void testDetectsPalWeaveInLocalRepo() throws IOException {
    // Given: pal-weave JAR exists at the expected repo path
    Path repoRoot = tempDir.getRoot().toPath();
    Path jarDir = repoRoot.resolve("io/quasient/pal/pal-weave/1.0.0");
    Files.createDirectories(jarDir);
    Files.write(jarDir.resolve("pal-weave-1.0.0.jar"), new byte[] {0x50, 0x4B});

    // When: isAvailableLocally called
    boolean available = PalWeaveResolver.isAvailableLocally("1.0.0", repoRoot);

    // Then: returns true
    assertThat(available, is(true));
  }

  /**
   * Verifies that {@code isAvailableLocally} returns {@code false} when the pal-weave JAR does not
   * exist in the local Maven repository.
   *
   * <p>Uses an empty temporary directory as the repository root.
   */
  @Test
  public void testDetectsMissingPalWeave() {
    // Given: empty repo root (no pal-weave present)
    Path repoRoot = tempDir.getRoot().toPath();

    // When: isAvailableLocally called
    boolean available = PalWeaveResolver.isAvailableLocally("1.0.0", repoRoot);

    // Then: returns false
    assertThat(available, is(false));
  }

  /**
   * Verifies that {@code getLocalRepoPath} returns the path to the default Maven local repository
   * ({@code ~/.m2/repository}), or respects {@code M2_HOME} / {@code settings.xml} overrides.
   */
  @Test
  public void testResolvesLocalRepoPath() {
    // Given: a temporary home directory with no settings.xml
    Path fakeHome = tempDir.getRoot().toPath();

    // When: resolving local repo path using the testable overload
    Path repoPath = PalWeaveResolver.resolveLocalRepoPath(fakeHome);

    // Then: returns .m2/repository under the given home
    assertThat(repoPath, is(fakeHome.resolve(".m2").resolve("repository")));
  }

  /**
   * Verifies that {@code getMavenCentralUrl} constructs the correct URL for the pal-weave JAR on
   * Maven Central given a specific PAL version.
   *
   * <p>Expected URL format: {@code
   * https://repo1.maven.org/maven2/io/quasient/pal/pal-weave/1.0.0/pal-weave-1.0.0.jar}
   */
  @Test
  public void testConstructsMavenCentralUrl() {
    // When: constructing Maven Central URL for version 1.0.0
    String url = PalWeaveResolver.getMavenCentralUrl("1.0.0");

    // Then: returns correct URL
    assertThat(
        url,
        is(
            "https://repo1.maven.org/maven2/io/quasient/pal/pal-weave/1.0.0/"
                + "pal-weave-1.0.0.jar"));
  }

  /**
   * Verifies that {@code fetchFromMavenCentral} downloads the pal-weave JAR and POM and places them
   * in the correct directory structure within the local Maven repository.
   *
   * <p>Uses a temporary directory as the repository root and a mock HTTP response returning dummy
   * JAR bytes. After the fetch, both {@code pal-weave-1.0.0.jar} and {@code pal-weave-1.0.0.pom}
   * should exist at the expected path.
   */
  @Test
  public void testFetchPlacesJarInCorrectLocation() {
    // Given: empty repo root, mock downloader that writes dummy bytes
    Path repoRoot = tempDir.getRoot().toPath();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    PalWeaveResolver.Downloader mockDownloader =
        (url, target) -> Files.write(target, new byte[] {0x50, 0x4B});
    PalWeaveResolver resolver = new PalWeaveResolver(out, false, mockDownloader);

    // When: fetching from Maven Central
    PalWeaveResolver.ResolveResult result = resolver.fetchFromMavenCentral("1.0.0", repoRoot);

    // Then: both JAR and POM placed in correct directory structure
    assertThat(result.isSuccess(), is(true));
    Path artifactDir = repoRoot.resolve("io/quasient/pal/pal-weave/1.0.0");
    assertThat(Files.exists(artifactDir.resolve("pal-weave-1.0.0.jar")), is(true));
    assertThat(Files.exists(artifactDir.resolve("pal-weave-1.0.0.pom")), is(true));
  }

  /**
   * Verifies that {@code fetchFromMavenCentral} handles network failures or HTTP 404 responses
   * gracefully by returning a failure result with a descriptive message instead of throwing an
   * unchecked exception.
   */
  @Test
  public void testFetchFailsGracefully() {
    // Given: a downloader that simulates network failure
    Path repoRoot = tempDir.getRoot().toPath();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    PalWeaveResolver.Downloader failingDownloader =
        (url, target) -> {
          throw new IOException("Connection refused");
        };
    PalWeaveResolver resolver = new PalWeaveResolver(out, false, failingDownloader);

    // When: fetching from Maven Central
    PalWeaveResolver.ResolveResult result = resolver.fetchFromMavenCentral("1.0.0", repoRoot);

    // Then: returns a failure result with descriptive message
    assertThat(result.isSuccess(), is(false));
    assertThat(result.getMessage(), containsString("Failed to fetch pal-weave"));
    assertThat(result.getMessage(), containsString("Connection refused"));
    assertThat(result.getMessage(), containsString("mvn dependency:resolve"));
  }

  /**
   * Verifies that {@code ensureAvailable} returns immediately without attempting a download when
   * pal-weave is already present in the local Maven repository.
   */
  @Test
  public void testSkipsFetchWhenAlreadyAvailable() throws IOException {
    // Given: pal-weave JAR already exists in local repo
    Path repoRoot = tempDir.getRoot().toPath();
    Path jarDir = repoRoot.resolve("io/quasient/pal/pal-weave/1.0.0");
    Files.createDirectories(jarDir);
    Files.write(jarDir.resolve("pal-weave-1.0.0.jar"), new byte[] {0x50, 0x4B});

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    AtomicInteger downloadCount = new AtomicInteger(0);
    PalWeaveResolver.Downloader countingDownloader =
        (url, target) -> downloadCount.incrementAndGet();
    PalWeaveResolver resolver = new PalWeaveResolver(out, false, countingDownloader);

    // When: ensureAvailable called
    PalWeaveResolver.ResolveResult result = resolver.ensureAvailable("1.0.0", repoRoot);

    // Then: returns success without attempting any downloads
    assertThat(result.isSuccess(), is(true));
    assertThat(result.getMessage(), containsString("already available"));
    assertThat(downloadCount.get(), is(0));
  }

  /**
   * Verifies that {@code ensureAvailable} in dry-run mode reports that a fetch would be performed
   * but does not actually download any artifacts.
   */
  @Test
  public void testDryRunSkipsFetch() {
    // Given: pal-weave not available, dryRun=true
    Path repoRoot = tempDir.getRoot().toPath();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    AtomicInteger downloadCount = new AtomicInteger(0);
    PalWeaveResolver.Downloader countingDownloader =
        (url, target) -> downloadCount.incrementAndGet();
    PalWeaveResolver resolver = new PalWeaveResolver(out, true, countingDownloader);

    // When: ensureAvailable called
    PalWeaveResolver.ResolveResult result = resolver.ensureAvailable("1.0.0", repoRoot);

    // Then: reports dry-run without performing any downloads
    assertThat(result.isSuccess(), is(true));
    assertThat(result.getMessage(), containsString("Dry run"));
    assertThat(downloadCount.get(), is(0));
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("Would fetch"));
  }

  /**
   * Verifies that the resolver expects both the JAR and POM artifacts when checking for or fetching
   * pal-weave for a given version.
   */
  @Test
  public void testResolvesPomAndJar() {
    // Given: empty repo root, mock downloader that writes dummy bytes
    Path repoRoot = tempDir.getRoot().toPath();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    PalWeaveResolver.Downloader mockDownloader =
        (url, target) -> Files.write(target, new byte[] {0x50, 0x4B});
    PalWeaveResolver resolver = new PalWeaveResolver(out, false, mockDownloader);

    // When: fetching version 1.0.0
    PalWeaveResolver.ResolveResult result = resolver.fetchFromMavenCentral("1.0.0", repoRoot);

    // Then: both JAR and POM exist in local repo
    assertThat(result.isSuccess(), is(true));
    Path versionDir = repoRoot.resolve("io/quasient/pal/pal-weave/1.0.0");
    assertThat(Files.exists(versionDir.resolve("pal-weave-1.0.0.jar")), is(true));
    assertThat(Files.exists(versionDir.resolve("pal-weave-1.0.0.pom")), is(true));
  }
}
