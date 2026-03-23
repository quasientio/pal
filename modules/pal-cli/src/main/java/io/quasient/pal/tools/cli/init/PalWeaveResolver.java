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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the {@code pal-weave} artifact in the local Maven repository, fetching it from Maven
 * Central if missing.
 *
 * <p>This resolver is invoked by the {@code pal init} command after build file generation to ensure
 * that the critical {@code pal-weave} dependency is available before the user runs their first
 * build. It works for both Maven and Gradle projects since both can consume artifacts from the
 * local Maven repository.
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>Checks {@code ~/.m2/repository} (or custom location from {@code settings.xml}) for the
 *       artifact
 *   <li>Downloads both JAR and POM from Maven Central when not locally available
 *   <li>Fails gracefully with a descriptive message on network or HTTP errors
 *   <li>Supports dry-run mode (reports actions without performing downloads)
 *   <li>Is idempotent — skips fetch when the artifact is already present
 * </ul>
 *
 * @since 1.0.0
 */
public final class PalWeaveResolver {

  /** Maven group path for pal-weave on the filesystem. */
  private static final String GROUP_PATH = "io/quasient/pal";

  /** Maven artifact ID for pal-weave. */
  private static final String ARTIFACT_ID = "pal-weave";

  /** Base URL for Maven Central repository. */
  private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

  /** Connection timeout in milliseconds for HTTP downloads. */
  private static final int CONNECT_TIMEOUT_MS = 30_000;

  /** Read timeout in milliseconds for HTTP downloads. */
  private static final int READ_TIMEOUT_MS = 30_000;

  /**
   * Functional interface for downloading a URL to a local file. Package-private to allow test
   * injection of mock downloaders.
   */
  @FunctionalInterface
  interface Downloader {

    /**
     * Downloads the content at the given URL to the target path.
     *
     * @param url the URL to download from
     * @param target the local file path to write to
     * @throws IOException if the download fails
     */
    void download(String url, Path target) throws IOException;
  }

  /** Output stream for progress messages. */
  private final PrintStream out;

  /** Whether this resolver operates in dry-run mode (no actual downloads). */
  private final boolean dryRun;

  /** The download strategy, defaulting to HTTP. */
  private final Downloader downloader;

  /**
   * Creates a resolver with real HTTP downloading.
   *
   * @param out the output stream for progress messages
   * @param dryRun whether to operate in dry-run mode
   */
  public PalWeaveResolver(PrintStream out, boolean dryRun) {
    this(out, dryRun, PalWeaveResolver::httpDownload);
  }

  /**
   * Creates a resolver with a custom download strategy. Package-private for testing.
   *
   * @param out the output stream for progress messages
   * @param dryRun whether to operate in dry-run mode
   * @param downloader the download strategy to use
   */
  PalWeaveResolver(PrintStream out, boolean dryRun, Downloader downloader) {
    this.out = out;
    this.dryRun = dryRun;
    this.downloader = downloader;
  }

  /**
   * Returns the path to the local Maven repository. Checks {@code ~/.m2/settings.xml} for a custom
   * {@code <localRepository>} element, falling back to {@code ~/.m2/repository}.
   *
   * @return the local Maven repository path
   */
  public static Path getLocalRepoPath() {
    return resolveLocalRepoPath(Path.of(System.getProperty("user.home")));
  }

  /**
   * Resolves the local Maven repository path relative to the given user home directory. Checks for
   * a custom {@code <localRepository>} element in {@code .m2/settings.xml}.
   *
   * @param userHome the user home directory
   * @return the local Maven repository path
   */
  static Path resolveLocalRepoPath(Path userHome) {
    Path settingsPath = userHome.resolve(".m2").resolve("settings.xml");
    if (Files.exists(settingsPath)) {
      String customRepo = parseLocalRepoFromSettings(settingsPath);
      if (customRepo != null) {
        return Path.of(customRepo);
      }
    }
    return userHome.resolve(".m2").resolve("repository");
  }

  /**
   * Checks whether the {@code pal-weave} JAR exists in the local Maven repository at the expected
   * path for the given version.
   *
   * @param version the PAL version to check (e.g., {@code "1.0.0"})
   * @param repoRoot the root of the local Maven repository
   * @return {@code true} if the JAR file exists at the expected location
   */
  public static boolean isAvailableLocally(String version, Path repoRoot) {
    Path jarPath = repoRoot.resolve(artifactRelativePath(version, ".jar"));
    return Files.exists(jarPath);
  }

  /**
   * Constructs the Maven Central URL for the {@code pal-weave} JAR at the given version.
   *
   * @param version the PAL version (e.g., {@code "1.0.0"})
   * @return the full URL to the JAR on Maven Central
   */
  public static String getMavenCentralUrl(String version) {
    return MAVEN_CENTRAL_BASE
        + "/"
        + GROUP_PATH
        + "/"
        + ARTIFACT_ID
        + "/"
        + version
        + "/"
        + ARTIFACT_ID
        + "-"
        + version
        + ".jar";
  }

  /**
   * Constructs the Maven Central URL for the {@code pal-weave} POM at the given version.
   *
   * @param version the PAL version (e.g., {@code "1.0.0"})
   * @return the full URL to the POM on Maven Central
   */
  static String getMavenCentralPomUrl(String version) {
    return MAVEN_CENTRAL_BASE
        + "/"
        + GROUP_PATH
        + "/"
        + ARTIFACT_ID
        + "/"
        + version
        + "/"
        + ARTIFACT_ID
        + "-"
        + version
        + ".pom";
  }

  /**
   * Ensures that {@code pal-weave} is available in the local Maven repository, fetching it from
   * Maven Central if necessary.
   *
   * <p>When the artifact is already present, returns immediately. In dry-run mode, reports what
   * would be done without performing any downloads.
   *
   * @param version the PAL version to ensure (e.g., {@code "1.0.0"})
   * @param repoRoot the root of the local Maven repository
   * @return a {@link ResolveResult} indicating success or failure
   */
  public ResolveResult ensureAvailable(String version, Path repoRoot) {
    out.println("Checking for pal-weave " + version + "...");

    if (isAvailableLocally(version, repoRoot)) {
      out.println("\u2713 pal-weave " + version + " available");
      return ResolveResult.success("pal-weave " + version + " already available locally");
    }

    if (dryRun) {
      out.println("Would fetch pal-weave " + version + " from Maven Central");
      return ResolveResult.success("Dry run: would fetch pal-weave " + version);
    }

    return fetchFromMavenCentral(version, repoRoot);
  }

  /**
   * Fetches the {@code pal-weave} JAR and POM from Maven Central and places them in the correct
   * directory structure within the local Maven repository.
   *
   * <p>Creates parent directories as needed. On failure, returns a descriptive failure result
   * without throwing an unchecked exception.
   *
   * @param version the PAL version to fetch (e.g., {@code "1.0.0"})
   * @param repoRoot the root of the local Maven repository
   * @return a {@link ResolveResult} indicating success or failure
   */
  public ResolveResult fetchFromMavenCentral(String version, Path repoRoot) {
    out.println("Fetching pal-weave " + version + " from Maven Central...");

    Path artifactDir = repoRoot.resolve(GROUP_PATH).resolve(ARTIFACT_ID).resolve(version);

    try {
      Files.createDirectories(artifactDir);

      String jarUrl = getMavenCentralUrl(version);
      Path jarPath = artifactDir.resolve(ARTIFACT_ID + "-" + version + ".jar");
      downloader.download(jarUrl, jarPath);

      String pomUrl = getMavenCentralPomUrl(version);
      Path pomPath = artifactDir.resolve(ARTIFACT_ID + "-" + version + ".pom");
      downloader.download(pomUrl, pomPath);

      out.println("\u2713 pal-weave " + version + " available");
      return ResolveResult.success("Successfully fetched pal-weave " + version);
    } catch (IOException e) {
      String msg =
          "Failed to fetch pal-weave "
              + version
              + ": "
              + e.getMessage()
              + "\nYou can resolve this manually by running: mvn dependency:resolve"
              + "\nOr check your network connection.";
      out.println("\u26a0 " + msg);
      return ResolveResult.failure(msg);
    }
  }

  /**
   * Returns the relative path within a Maven repository for a pal-weave artifact.
   *
   * @param version the artifact version
   * @param extension the file extension (e.g., {@code ".jar"} or {@code ".pom"})
   * @return the relative path string
   */
  private static String artifactRelativePath(String version, String extension) {
    return GROUP_PATH
        + "/"
        + ARTIFACT_ID
        + "/"
        + version
        + "/"
        + ARTIFACT_ID
        + "-"
        + version
        + extension;
  }

  /**
   * Downloads a URL to a local file using {@link HttpURLConnection}.
   *
   * @param url the URL to download
   * @param target the local file to write to
   * @throws IOException if the download fails or the server returns a non-200 status
   */
  private static void httpDownload(String url, Path target) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    try {
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);

      int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + responseCode + " for " + url);
      }

      try (InputStream in = connection.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Parses a Maven {@code settings.xml} file for a custom {@code <localRepository>} element.
   *
   * @param settingsPath the path to the settings.xml file
   * @return the custom repository path, or {@code null} if not found or on parse error
   */
  private static String parseLocalRepoFromSettings(Path settingsPath) {
    try {
      for (String line : Files.readAllLines(settingsPath, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (trimmed.startsWith("<localRepository>") && trimmed.endsWith("</localRepository>")) {
          String value =
              trimmed
                  .substring(
                      "<localRepository>".length(),
                      trimmed.length() - "</localRepository>".length())
                  .trim();
          if (!value.isEmpty()) {
            return value;
          }
        }
      }
    } catch (IOException e) {
      // Fall through to default
    }
    return null;
  }

  /**
   * Result of a resolve or fetch operation. Contains a success/failure flag and a descriptive
   * message.
   *
   * @since 1.0.0
   */
  public static final class ResolveResult {

    /** Whether the operation succeeded. */
    private final boolean success;

    /** A human-readable description of the outcome. */
    private final String message;

    /**
     * Constructs a resolve result.
     *
     * @param success whether the operation succeeded
     * @param message a descriptive message
     */
    private ResolveResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    /**
     * Creates a successful result.
     *
     * @param message a descriptive message
     * @return a successful {@code ResolveResult}
     */
    static ResolveResult success(String message) {
      return new ResolveResult(true, message);
    }

    /**
     * Creates a failure result.
     *
     * @param message a descriptive message explaining the failure
     * @return a failed {@code ResolveResult}
     */
    static ResolveResult failure(String message) {
      return new ResolveResult(false, message);
    }

    /**
     * Returns whether the operation succeeded.
     *
     * @return {@code true} if the operation was successful
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Returns a human-readable description of the outcome.
     *
     * @return the result message
     */
    public String getMessage() {
      return message;
    }
  }
}
